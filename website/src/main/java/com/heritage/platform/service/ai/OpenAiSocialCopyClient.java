package com.heritage.platform.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiSocialCopyClient implements SocialCopyStructuredJsonClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiSocialCopyClient.class);

    private final OpenAiStructuredResponseClient client;

    @Value("${openai.enabled:true}")
    private boolean enabled = true;

    @Value("${openai.api-key:}")
    private String apiKey = "";

    @Value("${ai.social-copy.max-retries:1}")
    private int maxRetries = 1;

    @Value("${ai.social-copy.initial-backoff-seconds:5}")
    private long initialBackoffSeconds = 5;

    @Value("${ai.social-copy.max-backoff-seconds:30}")
    private long maxBackoffSeconds = 30;

    public OpenAiSocialCopyClient(OpenAiStructuredResponseClient client) {
        this.client = client;
    }

    @Override
    public String providerName() {
        return "OPENAI";
    }

    @Override
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String requestJson(
            String systemPrompt,
            String userPrompt,
            String schemaName,
            Map<String, Object> schema
    ) {
        logger.info(
                "OpenAI social-copy client configuration: enabled={}, keyConfigured={}, keyLength={}",
                enabled, apiKey != null && !apiKey.isBlank(), apiKey == null ? 0 : apiKey.length()
        );
        if (!isEnabled()) {
            throw new SocialCopyProviderException(
                    providerName(), SocialCopyFailureReason.DISABLED, null, null,
                    "OpenAI social-copy provider is disabled or not configured."
            );
        }
        int attempts = Math.max(0, maxRetries) + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                String prompt = "SYSTEM INSTRUCTIONS:\n" + systemPrompt + "\n\nUSER TASK:\n" + userPrompt;
                return client.requestJson(prompt, schemaName, schema, List.of());
            } catch (SocialCopyProviderException ex) {
                if (!ex.retryable() || attempt + 1 >= attempts) {
                    throw ex;
                }
                awaitBackoff(attempt, ex.retryAfterSeconds());
            } catch (AiCoverWorkflowException ex) {
                throw new SocialCopyProviderException(
                        providerName(), classify(ex), null, null,
                        AiCoverDiagnostics.safeExceptionSummary(ex), ex
                );
            }
        }
        throw new SocialCopyProviderException(
                providerName(), SocialCopyFailureReason.UNKNOWN, null, null,
                "OpenAI social-copy request failed without a result."
        );
    }

    private SocialCopyFailureReason classify(AiCoverWorkflowException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("json") || message.contains("structured")) {
            return SocialCopyFailureReason.INVALID_RESPONSE;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return SocialCopyFailureReason.TIMEOUT;
        }
        return SocialCopyFailureReason.UNKNOWN;
    }

    private void awaitBackoff(int retryIndex, Long retryAfterSeconds) {
        long exponential = Math.max(0L, initialBackoffSeconds) * (1L << Math.min(retryIndex, 10));
        long requested = retryAfterSeconds == null ? exponential : Math.max(exponential, retryAfterSeconds);
        long seconds = Math.min(Math.max(0L, maxBackoffSeconds), requested);
        logger.warn("OpenAI social-copy request will retry after {} seconds.", seconds);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SocialCopyProviderException(
                    providerName(), SocialCopyFailureReason.TEMPORARY_ERROR, null, null,
                    "OpenAI retry wait was interrupted.", ex
            );
        }
    }
}
