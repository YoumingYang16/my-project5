package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.time.Instant;

@Service
public class DeepSeekStructuredResponseClient implements SocialCopyStructuredJsonClient {

    private static final Logger logger = LoggerFactory.getLogger(DeepSeekStructuredResponseClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient injectedHttpClient;
    private final UserAiProviderSettingsService providerSettings;
    private final Map<String, String> userApiKeys = new ConcurrentHashMap<>();
    private final Map<String, ProxyKey> proxyKeys = new ConcurrentHashMap<>();

    @Value("${deepseek.enabled:false}")
    private boolean enabled;

    @Value("${deepseek.api-key:}")
    private String apiKey = "";

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl = "https://api.deepseek.com";

    @Value("${deepseek.model:deepseek-v4-flash}")
    private String model = "deepseek-v4-flash";

    @Value("${deepseek.timeout-seconds:180}")
    private long timeoutSeconds = 180;

    @Value("${deepseek.connect-timeout-seconds:60}")
    private long connectTimeoutSeconds = 60;

    @Value("${deepseek.max-input-chars:16000}")
    private int maxInputChars = 16000;

    @Value("${deepseek.paper-understanding-client-max-input-chars:52000}")
    private int paperUnderstandingMaxInputChars = 52000;

    @Value("${deepseek.max-output-tokens:2800}")
    private int maxOutputTokens = 2800;

    @Value("${deepseek.proxy-token-ttl-seconds:900}")
    private long proxyTokenTtlSeconds = 900;

    @Value("${deepseek.use-system-proxy:false}")
    private boolean useSystemProxy = false;

    @Value("${deepseek.proxy-host:}")
    private String proxyHost = "";

    @Value("${deepseek.proxy-port:0}")
    private int proxyPort;

    @Value("${ai.social-copy.max-retries:1}")
    private int maxRetries = 1;

    @Value("${ai.social-copy.initial-backoff-seconds:5}")
    private long initialBackoffSeconds = 5;

    @Value("${ai.social-copy.max-backoff-seconds:30}")
    private long maxBackoffSeconds = 30;

    @Autowired
    public DeepSeekStructuredResponseClient(ObjectMapper objectMapper, UserAiProviderSettingsService providerSettings) {
        this(objectMapper, null, providerSettings);
    }

    DeepSeekStructuredResponseClient(ObjectMapper objectMapper, HttpClient injectedHttpClient) {
        this(objectMapper, injectedHttpClient, null);
    }

    DeepSeekStructuredResponseClient(ObjectMapper objectMapper, HttpClient injectedHttpClient, UserAiProviderSettingsService providerSettings) {
        this.objectMapper = objectMapper;
        this.injectedHttpClient = injectedHttpClient;
        this.providerSettings = providerSettings;
    }

    @Override
    public String providerName() {
        return selectedTextProvider().toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean isEnabled() {
        return !effectiveApiKey().isBlank() && (enabled || !"deepseek".equals(selectedTextProvider()));
    }

    public void configureRuntimeApiKey(String value) {
        String username = requireCurrentUsername();
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() < 20 || cleaned.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Enter a valid " + providerName()
                    + " API key (at least 20 characters, without spaces).");
        }
        userApiKeys.put(username, cleaned);
    }

    public String issueProxyTokenForCurrentUser() {
        String key = effectiveApiKey();
        if (key.isBlank()) return "";
        Instant now = Instant.now();
        proxyKeys.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        String token = UUID.randomUUID().toString();
        long ttlSeconds = Math.max(300, Math.min(proxyTokenTtlSeconds, 1800));
        proxyKeys.put(token, new ProxyKey(
                key,
                selectedTextProvider(),
                effectiveModel(),
                now.plusSeconds(ttlSeconds)
        ));
        return token;
    }

    public String proxyCompletion(String token, JsonNode requestBody) {
        ProxyKey proxyKey = proxyKeys.remove(token == null ? "" : token.trim());
        if (proxyKey == null || proxyKey.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("The DeepSeek task token is invalid or expired.");
        }
        try {
            JsonNode safeBody = requestBody.deepCopy();
            if (safeBody instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                objectNode.remove("userTaskToken");
                // n8n sends its default DeepSeek model. The token is bound to the
                // user's selected provider/model so the callback cannot drift.
                objectNode.put("model", proxyKey.model());
                if (!"deepseek".equals(proxyKey.provider())) {
                    objectNode.remove("thinking");
                }
            }
            HttpRequest request = HttpRequest.newBuilder(endpointFor(proxyKey.provider()))
                    .timeout(Duration.ofSeconds(effectiveTimeoutSeconds()))
                    .header("Authorization", "Bearer " + proxyKey.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(safeBody.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException(proxyKey.provider().toUpperCase(java.util.Locale.ROOT)
                        + " returned HTTP " + response.statusCode() + ".");
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Selected text provider proxy request was interrupted.", ex);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Selected text provider proxy request failed.", ex);
        }
    }

    private record ProxyKey(String apiKey, String provider, String model, Instant expiresAt) {}
    public void clearRuntimeApiKey() {
        userApiKeys.remove(requireCurrentUsername());
    }

    public boolean hasRuntimeApiKey() {
        String username = currentUsername();
        return !username.isBlank() && userApiKeys.containsKey(username);
    }

    public boolean hasConfiguredPropertyKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String keySource() {
        if (providerSettings != null && providerSettings.platformAiEnabled()) {
            return providerSettings.current().textApiKey().isBlank() ? "none" : "platform";
        }
        if (providerSettings != null && providerSettings.hasCurrentUser()) {
            return providerSettings.current().textApiKey().isBlank() ? "none" : "user-settings";
        }
        String username = currentUsername();
        if (!username.isBlank()) {
            return userApiKeys.containsKey(username) ? "user-session" : "none";
        }
        return hasConfiguredPropertyKey() ? "environment" : "none";
    }

    private String effectiveApiKey() {
        if (providerSettings != null && providerSettings.hasCurrentUser()) {
            String selectedKey = providerSettings.current().textApiKey();
            return selectedKey == null ? "" : selectedKey.trim();
        }
        String username = currentUsername();
        if (!username.isBlank()) {
            return userApiKeys.getOrDefault(username, "");
        }
        return apiKey == null ? "" : apiKey.trim();
    }

    private String requireCurrentUsername() {
        String username = currentUsername();
        if (username.isBlank()) {
            throw new IllegalArgumentException("Sign in before configuring a DeepSeek API key.");
        }
        return username;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "";
        }
        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return "";
        }
        return username.trim();
    }

    @Override
    public String requestJson(
            String systemPrompt,
            String userPrompt,
            String schemaName,
            Map<String, Object> schema
    ) {
        logger.info(
                "DeepSeek structured client configuration: enabled={}, keyConfigured={}, keySource={}, model={}",
                enabled, !effectiveApiKey().isBlank(), keySource(), effectiveModel()
        );
        ensureConfigured();
        int attempts = Math.max(0, maxRetries) + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return execute(systemPrompt, userPrompt, schemaName);
            } catch (SocialCopyProviderException ex) {
                if (!ex.retryable() || attempt + 1 >= attempts) {
                    throw ex;
                }
                awaitBackoff(attempt, ex.retryAfterSeconds());
            }
        }
        throw failure(SocialCopyFailureReason.UNKNOWN, null, null,
                "DeepSeek social-copy request failed without a result.", null);
    }

    private String execute(String systemPrompt, String userPrompt, String schemaName) {
        try {
            String boundedSystem = clean(systemPrompt);
            String boundedUser = boundedUserPrompt(boundedSystem, userPrompt, schemaName);
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", effectiveModel());
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", boundedSystem),
                    Map.of("role", "user", "content", boundedUser)
            ));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", effectiveMaxOutputTokens());
            if ("deepseek".equals(selectedTextProvider())) {
                requestBody.put("thinking", Map.of("type", "disabled"));
            }
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("stream", false);

            URI endpoint = endpoint();
            logger.info(
                    "DeepSeek structured social-copy request started: schema={}, endpointHost={}, model={}",
                    schemaName, endpoint.getHost(), effectiveModel()
            );
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(effectiveTimeoutSeconds()))
                    .header("Authorization", "Bearer " + effectiveApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8
                    ))
                    .build();
            HttpResponse<String> response = httpClient().send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            int status = response.statusCode();
            logger.info("DeepSeek structured social-copy response received: schema={}, status={}", schemaName, status);
            String provider = providerName();
            if (status == 401 || status == 403) {
                throw failure(SocialCopyFailureReason.AUTHENTICATION, status, null,
                        provider + " authentication failed. Check the API key saved in Settings.", null);
            }
            if (status == 402 || indicatesInsufficientCredit(response.body())) {
                throw failure(SocialCopyFailureReason.INSUFFICIENT_CREDIT, status, null,
                        provider + " account credit is insufficient.", null);
            }
            if (status == 429) {
                throw failure(
                        SocialCopyFailureReason.RATE_LIMITED, 429,
                        retryAfterSeconds(response.headers().firstValue("Retry-After").orElse(null)),
                        provider + " rate limit was reached.", null
                );
            }
            if (status >= 500) {
                throw failure(SocialCopyFailureReason.TEMPORARY_ERROR, status, null,
                        provider + " temporarily failed with HTTP " + status + ".", null);
            }
            if (status < 200 || status >= 300) {
                throw failure(SocialCopyFailureReason.UNKNOWN, status, null,
                        safeHttpFailure(status, response.body()), null);
            }
            return extractContent(response.body());
        } catch (SocialCopyProviderException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw failure(SocialCopyFailureReason.TIMEOUT, null, null, "DeepSeek request timed out.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw failure(SocialCopyFailureReason.TEMPORARY_ERROR, null, null,
                    "DeepSeek request was interrupted.", ex);
        } catch (IOException ex) {
            throw failure(SocialCopyFailureReason.TEMPORARY_ERROR, null, null,
                    "DeepSeek network request failed: " + AiCoverDiagnostics.safeExceptionSummary(ex), ex);
        } catch (RuntimeException ex) {
            throw failure(SocialCopyFailureReason.INVALID_RESPONSE, null, null,
                    "DeepSeek request or response was invalid: " + AiCoverDiagnostics.safeExceptionSummary(ex), ex);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode first = root.path("choices").path(0);
            String finishReason = first.path("finish_reason").asText("");
            if ("length".equalsIgnoreCase(finishReason)) {
                throw failure(SocialCopyFailureReason.INVALID_RESPONSE, 200, null,
                        "DeepSeek JSON output was truncated because the token limit was reached.", null);
            }
            String content = first.path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw failure(SocialCopyFailureReason.INVALID_RESPONSE, 200, null,
                        "DeepSeek response did not contain JSON message content.", null);
            }
            String json = normalizeJsonObjectContent(content);
            objectMapper.readTree(json);
            return json;
        } catch (SocialCopyProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failure(SocialCopyFailureReason.JSON_PARSE_FAILURE, 200, null,
                    "DeepSeek returned content that was not valid JSON.", ex);
        }
    }

    private HttpClient httpClient() {
        if (injectedHttpClient != null) {
            return injectedHttpClient;
        }
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(effectiveConnectTimeoutSeconds()));
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0 && proxyPort <= 65535) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
        } else if (useSystemProxy && ProxySelector.getDefault() != null) {
            builder.proxy(ProxySelector.getDefault());
        }
        return builder.build();
    }

    private URI endpoint() {
        return endpointFor(selectedTextProvider());
    }

    private URI endpointFor(String provider) {
        String value = switch (provider == null ? "deepseek" : provider) {
            case "openai" -> "https://api.openai.com/v1";
            case "doubao" -> "https://ark.cn-beijing.volces.com/api/v3";
            default -> baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
        };
        value = value.replaceAll("/+$", "");
        if (value.endsWith("/chat/completions")) {
            return URI.create(value);
        }
        return URI.create(value + "/chat/completions");
    }

    private void ensureConfigured() {
        String provider = providerName();
        if (!enabled && "deepseek".equals(selectedTextProvider())) {
            throw failure(SocialCopyFailureReason.DISABLED, null, null, "DeepSeek is disabled.", null);
        }
        if (effectiveApiKey().isBlank()) {
            throw failure(SocialCopyFailureReason.CONFIGURATION, null, null,
                    provider + " is not configured. Add your key in Settings, then retry.", null);
        }
    }

    private String boundedUserPrompt(String systemPrompt, String userPrompt, String schemaName) {
        boolean paperUnderstanding = "paper_understanding".equalsIgnoreCase(clean(schemaName));
        int configuredLimit = paperUnderstanding ? paperUnderstandingMaxInputChars : maxInputChars;
        int totalLimit = configuredLimit <= 0 ? 6000 : configuredLimit;
        int userLimit = Math.max(1000, totalLimit - Math.min(totalLimit / 2, systemPrompt.length()));
        String value = clean(userPrompt);
        if (value.length() <= userLimit) {
            return value;
        }
        return value.substring(0, userLimit).trim();
    }

    private String safeHttpFailure(int status, String body) {
        String detail = null;
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            detail = AiCoverDiagnostics.sanitize(error.path("message").asText(null));
        } catch (Exception ignored) {
            // Status remains sufficient and avoids logging an untrusted raw response.
        }
        return providerName() + " request failed with HTTP " + status
                + (detail == null || detail.isBlank() ? "." : " (" + detail + ").");
    }

    private boolean indicatesInsufficientCredit(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String normalized = body.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("insufficient_balance")
                || normalized.contains("insufficient balance")
                || normalized.contains("insufficient credit")
                || normalized.contains("余额不足")
                || normalized.contains("额度不足");
    }

    private Long retryAfterSeconds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                long seconds = Duration.between(
                        ZonedDateTime.now(ZoneOffset.UTC),
                        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                ).toSeconds();
                return Math.max(0L, seconds);
            } catch (Exception invalidDate) {
                return null;
            }
        }
    }

    private void awaitBackoff(int retryIndex, Long retryAfterSeconds) {
        long exponential = Math.max(0L, initialBackoffSeconds) * (1L << Math.min(retryIndex, 10));
        long requested = retryAfterSeconds == null ? exponential : Math.max(exponential, retryAfterSeconds);
        long seconds = Math.min(Math.max(0L, maxBackoffSeconds), requested);
        logger.warn("DeepSeek social-copy request will retry after {} seconds.", seconds);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw failure(SocialCopyFailureReason.TEMPORARY_ERROR, null, null,
                    "DeepSeek retry wait was interrupted.", ex);
        }
    }

    private SocialCopyProviderException failure(
            SocialCopyFailureReason reason,
            Integer status,
            Long retryAfter,
            String message,
            Throwable cause
    ) {
        String safe = AiCoverDiagnostics.sanitize(message);
        return cause == null
                ? new SocialCopyProviderException(providerName(), reason, status, retryAfter, safe)
                : new SocialCopyProviderException(providerName(), reason, status, retryAfter, safe, cause);
    }

    private String effectiveModel() {
        if (providerSettings != null && providerSettings.hasCurrentUser()) {
            String selected = providerSettings.current().textModel();
            if (selected != null && !selected.isBlank()) return selected.trim();
        }
        return model == null || model.isBlank() ? "deepseek-v4-flash" : model.trim();
    }

    private String selectedTextProvider() {
        if (providerSettings == null || !providerSettings.hasCurrentUser()) return "deepseek";
        String selected = providerSettings.current().textProvider();
        return selected == null || selected.isBlank() ? "deepseek" : selected;
    }

    private long effectiveTimeoutSeconds() {
        return timeoutSeconds > 0 ? timeoutSeconds : 180;
    }

    private long effectiveConnectTimeoutSeconds() {
        return connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 60;
    }

    private int effectiveMaxOutputTokens() {
        return maxOutputTokens > 0 ? maxOutputTokens : 2800;
    }

    private String normalizeJsonObjectContent(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline >= 0) value = value.substring(firstNewline + 1);
            int closingFence = value.lastIndexOf("```");
            if (closingFence >= 0) value = value.substring(0, closingFence);
            value = value.trim();
        }
        int start = value.indexOf('{');
        if (start < 0) return value;
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') inString = true;
            else if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return value.substring(start, index + 1);
        }
        return value.substring(start).trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
