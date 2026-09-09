package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.SocialCopyContentPlan;
import com.heritage.platform.dto.ai.SocialCopyContentSpec;
import com.heritage.platform.dto.ai.SocialCopyProviderAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class SocialCopyProviderRouter {

    private static final Logger logger = LoggerFactory.getLogger(SocialCopyProviderRouter.class);

    private final OpenAiSocialCopyService openAiPipeline;
    private final SocialCopyStructuredJsonClient openAiClient;
    private final DeepSeekSocialCopyService deepSeekService;
    private final EvidenceFallbackSocialCopyService evidenceFallbackService;

    @Value("${ai.social-copy.provider-priority:openai,deepseek,evidence_fallback}")
    private String providerPriority = "openai,deepseek,evidence_fallback";

    @Value("${ai.social-copy.compact-llm-mode:true}")
    private boolean compactLlmMode = true;

    @Value("${ai.social-copy.allow-evidence-fallback-when-openai-unavailable:true}")
    private boolean allowEvidenceFallback = true;

    @Value("${ai.social-copy.max-retries:1}")
    private int maxRetries = 1;

    @Value("${ai.social-copy.initial-backoff-seconds:5}")
    private long initialBackoffSeconds = 5;

    @Value("${ai.social-copy.max-backoff-seconds:30}")
    private long maxBackoffSeconds = 30;

    @Autowired
    public SocialCopyProviderRouter(
            OpenAiSocialCopyService openAiPipeline,
            @Qualifier("openAiSocialCopyClient") SocialCopyStructuredJsonClient openAiClient,
            DeepSeekSocialCopyService deepSeekService,
            EvidenceFallbackSocialCopyService evidenceFallbackService
    ) {
        this.openAiPipeline = openAiPipeline;
        this.openAiClient = openAiClient;
        this.deepSeekService = deepSeekService;
        this.evidenceFallbackService = evidenceFallbackService;
    }

    SocialCopyProviderRouter(
            OpenAiSocialCopyService openAiPipeline,
            SocialCopyStructuredJsonClient openAiClient,
            DeepSeekSocialCopyService deepSeekService,
            EvidenceFallbackSocialCopyService evidenceFallbackService,
            boolean compactLlmMode,
            String providerPriority
    ) {
        this(openAiPipeline, openAiClient, deepSeekService, evidenceFallbackService);
        this.compactLlmMode = compactLlmMode;
        this.providerPriority = providerPriority;
    }

    public RoutedSocialCopy generate(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language,
            SocialCopyContentSpec contentSpec,
            SocialCopyContentPlan contentPlan
    ) {
        List<SocialCopyProviderAttempt> attempts = new ArrayList<>();
        AttemptOutcome outcome = tryDeepSeek(understanding, evidence, language, contentSpec, contentPlan);
        attempts.add(outcome.attempt());
        if (outcome.generated() != null) {
            return selected(outcome.generated(), "DEEPSEEK", attempts, null, true);
        }
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        throw new SocialCopyProviderException(
                "DEEPSEEK", SocialCopyFailureReason.CONFIGURATION, null, null,
                "DeepSeek is disabled or not configured."
        );
    }

    public RoutedSocialCopy generate(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language
    ) {
        SocialCopyContentSpec spec = new SocialCopyContentSpec(
                "public", "science_education", language, "engaging", "standard", null,
                SocialCopyContentSpec.XIAOHONGSHU, "personal"
        );
        SocialCopyContentPlan plan = new SocialCopyContentPlan(
                "Use a clear, evidence-led explanation.",
                "Explain the paper without unsupported promotion.",
                List.of(), List.of(), "Read the paper for the full evidence."
        );
        return generate(understanding, evidence, language, spec, plan);
    }

    private AttemptOutcome tryOpenAi(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language
    ) {
        if (openAiClient == null || !openAiClient.isEnabled()) {
            return new AttemptOutcome(null, disabledAttempt("OPENAI", "OpenAI is disabled or not configured."), null);
        }
        long started = System.nanoTime();
        int attempts = compactLlmMode ? Math.max(0, maxRetries) + 1 : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                OpenAiSocialCopyService.GeneratedSocialCopy generated = compactLlmMode
                        ? openAiPipeline.generateCompact(understanding, evidence, language, openAiClient)
                        : openAiPipeline.generate(understanding, evidence, language);
                if (generated == null) {
                    throw new SocialCopyProviderException(
                            "OPENAI", SocialCopyFailureReason.INVALID_RESPONSE, null, null,
                            "OpenAI social-copy provider returned no result."
                    );
                }
                return new AttemptOutcome(generated, successAttempt("OPENAI", elapsedMs(started)), null);
            } catch (AiCoverWorkflowException ex) {
                if (schemaFailure(ex) && attempt + 1 < attempts) {
                    awaitSchemaRetry("OPENAI", attempt);
                    continue;
                }
                return failureOutcome("OPENAI", ex, started);
            }
        }
        return failureOutcome("OPENAI", new AiCoverWorkflowException("OpenAI retry exhausted."), started);
    }

    private AttemptOutcome tryDeepSeek(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language,
            SocialCopyContentSpec contentSpec,
            SocialCopyContentPlan contentPlan
    ) {
        if (deepSeekService == null || !deepSeekService.isEnabled()) {
            return new AttemptOutcome(null, disabledAttempt("DEEPSEEK", "DeepSeek is disabled or not configured."), null);
        }
        long started = System.nanoTime();
        int attempts = Math.max(0, maxRetries) + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                OpenAiSocialCopyService.GeneratedSocialCopy generated = deepSeekService.generate(
                        understanding, evidence, language, contentSpec, contentPlan
                );
                if (generated == null) {
                    throw new SocialCopyProviderException(
                            "DEEPSEEK", SocialCopyFailureReason.INVALID_RESPONSE, null, null,
                            "DeepSeek social-copy provider returned no result."
                    );
                }
                return new AttemptOutcome(generated, successAttempt("DEEPSEEK", elapsedMs(started)), null);
            } catch (AiCoverWorkflowException ex) {
                if (schemaFailure(ex) && attempt + 1 < attempts) {
                    awaitSchemaRetry("DEEPSEEK", attempt);
                    continue;
                }
                return failureOutcome("DEEPSEEK", ex, started);
            }
        }
        return failureOutcome("DEEPSEEK", new AiCoverWorkflowException("DeepSeek retry exhausted."), started);
    }

    private AttemptOutcome failureOutcome(String provider, AiCoverWorkflowException failure, long started) {
        SocialCopyProviderException typed = failure instanceof SocialCopyProviderException value ? value : null;
        SocialCopyFailureReason reason = typed == null ? classify(failure) : typed.failureReason();
        Integer status = typed == null ? null : typed.statusCode();
        boolean rateLimited = typed != null && typed.rateLimited();
        String safeMessage = AiCoverDiagnostics.safeExceptionSummary(failure);
        logger.warn(
                "Social-copy provider failed: provider={}, failureReason={}, statusCode={}, rateLimited={}, durationMs={}",
                provider, reason, status, rateLimited, elapsedMs(started)
        );
        SocialCopyProviderAttempt attempt = new SocialCopyProviderAttempt(
                provider, true, true, false, reason.name(), status, rateLimited,
                elapsedMs(started), safeMessage
        );
        return new AttemptOutcome(null, attempt, failure);
    }

    private SocialCopyFailureReason classify(AiCoverWorkflowException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("429") || message.contains("rate limit")) {
            return SocialCopyFailureReason.RATE_LIMITED;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return SocialCopyFailureReason.TIMEOUT;
        }
        if (message.contains("json") || message.contains("validation") || message.contains("response")) {
            return SocialCopyFailureReason.INVALID_RESPONSE;
        }
        return SocialCopyFailureReason.UNKNOWN;
    }

    private boolean schemaFailure(AiCoverWorkflowException failure) {
        if (failure instanceof SocialCopyProviderException typed) {
            return typed.failureReason() == SocialCopyFailureReason.INVALID_RESPONSE
                    || typed.failureReason() == SocialCopyFailureReason.JSON_PARSE_FAILURE;
        }
        SocialCopyFailureReason reason = classify(failure);
        return reason == SocialCopyFailureReason.INVALID_RESPONSE
                || reason == SocialCopyFailureReason.JSON_PARSE_FAILURE;
    }

    private void awaitSchemaRetry(String provider, int retryIndex) {
        long exponential = Math.max(0L, initialBackoffSeconds) * (1L << Math.min(retryIndex, 10));
        long seconds = Math.min(Math.max(0L, maxBackoffSeconds), exponential);
        logger.warn("{} compact social-copy JSON will retry after {} seconds.", provider, seconds);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SocialCopyProviderException(
                    provider, SocialCopyFailureReason.TEMPORARY_ERROR, null, null,
                    provider + " compact retry wait was interrupted.", ex
            );
        }
    }

    private RoutedSocialCopy selected(
            OpenAiSocialCopyService.GeneratedSocialCopy generated,
            String source,
            List<SocialCopyProviderAttempt> attempts,
            SocialCopyProviderAttempt openAiFailure,
            boolean deepSeekUsed
    ) {
        return new RoutedSocialCopy(
                generated, source, List.copyOf(attempts), openAiFailure, deepSeekUsed, compactLlmMode
        );
    }

    private List<String> providers() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String raw = providerPriority == null ? "" : providerPriority;
        for (String value : raw.split(",")) {
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        if (values.isEmpty()) {
            return List.of("openai", "deepseek", "evidence_fallback");
        }
        return List.copyOf(values);
    }

    private SocialCopyProviderAttempt disabledAttempt(String provider, String message) {
        return new SocialCopyProviderAttempt(
                provider, false, false, false, SocialCopyFailureReason.DISABLED.name(),
                null, false, 0L, message
        );
    }

    private SocialCopyProviderAttempt successAttempt(String provider, long durationMs) {
        return new SocialCopyProviderAttempt(
                provider, true, true, true, null, null, false, durationMs, null
        );
    }

    private long elapsedMs(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    public record RoutedSocialCopy(
            OpenAiSocialCopyService.GeneratedSocialCopy generated,
            String source,
            List<SocialCopyProviderAttempt> attempts,
            SocialCopyProviderAttempt openAiFailure,
            boolean deepSeekUsed,
            boolean compactMode
    ) {
    }

    private record AttemptOutcome(
            OpenAiSocialCopyService.GeneratedSocialCopy generated,
            SocialCopyProviderAttempt attempt,
            AiCoverWorkflowException failure
    ) {
    }
}
