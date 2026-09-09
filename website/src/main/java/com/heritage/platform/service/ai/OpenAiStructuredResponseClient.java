package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OpenAiStructuredResponseClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiStructuredResponseClient.class);
    private static final URI RESPONSES_ENDPOINT = URI.create("https://api.openai.com/v1/responses");

    @Value("${openai.enabled:true}")
    private boolean openAiEnabled;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-5.5}")
    private String model;

    @Value("${openai.connect-timeout-seconds:20}")
    private long connectTimeoutSeconds;

    @Value("${openai.read-timeout-seconds:${openai.timeout-seconds:180}}")
    private long readTimeoutSeconds;

    @Value("${openai.scene-brief-timeout-seconds:90}")
    private long sceneBriefTimeoutSeconds;

    private final ObjectMapper objectMapper;

    public OpenAiStructuredResponseClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String requestJson(
            String prompt,
            String schemaName,
            Map<String, Object> schema,
            List<VisionInput> visionInputs
    ) {
        ensureConfigured();
        try {
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "input_text", "text", prompt));
            for (VisionInput input : visionInputs == null ? List.<VisionInput>of() : visionInputs) {
                content.add(Map.of("type", "input_text", "text", input.label()));
                content.add(Map.of(
                        "type", "input_image",
                        "image_url", toDataUrl(input.path()),
                        "detail", "low"
                ));
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "user");
            message.put("content", content);

            Map<String, Object> format = new LinkedHashMap<>();
            format.put("type", "json_schema");
            format.put("name", schemaName);
            format.put("schema", schema);
            format.put("strict", true);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", effectiveModel());
            requestBody.put("input", List.of(message));
            requestBody.put("text", Map.of("format", format));
            requestBody.put("store", false);

            logger.info(
                    "OpenAI structured analysis started: schema={}, model={}, imageCount={}",
                    schemaName,
                    effectiveModel(),
                    visionInputs == null ? 0 : visionInputs.size()
            );

            long timeout = effectiveTimeout(schemaName);
            HttpRequest request = HttpRequest.newBuilder(RESPONSES_ENDPOINT)
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody),
                            StandardCharsets.UTF_8
                    ))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(effectiveConnectTimeout()))
                    .build();
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            logger.info(
                    "OpenAI structured analysis response received: schema={}, status={}, requestIdPresent={}",
                    schemaName,
                    response.statusCode(),
                    response.headers().firstValue("x-request-id").isPresent()
            );
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SocialCopyProviderException(
                        "OPENAI", SocialCopyFailureReason.AUTHENTICATION, response.statusCode(), null,
                        "OpenAI authentication failed. Please check OPENAI_API_KEY."
                );
            }
            if (response.statusCode() == 429) {
                throw new SocialCopyProviderException(
                        "OPENAI", SocialCopyFailureReason.RATE_LIMITED, 429,
                        retryAfterSeconds(response.headers().firstValue("Retry-After").orElse(null)),
                        "OpenAI rate limit was reached. Please try again later."
                );
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw openAiHttpFailure(response.statusCode(), response.body());
            }
            return extractOutputText(response.body());
        } catch (AiCoverWorkflowException ex) {
            logger.warn("OpenAI structured analysis failed: schema={}, reason={}", schemaName, ex.getMessage());
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw loggedFailure(schemaName, "OpenAI structured analysis was interrupted.", ex);
        } catch (IOException ex) {
            if (ex instanceof HttpTimeoutException) {
                throw new SocialCopyProviderException(
                        "OPENAI", SocialCopyFailureReason.TIMEOUT, null, null,
                        "OpenAI request timed out.", ex
                );
            }
            throw loggedFailure(
                    schemaName,
                    "OpenAI structured analysis failed: " + AiCoverDiagnostics.safeExceptionSummary(ex),
                    ex
            );
        } catch (RuntimeException ex) {
            throw loggedFailure(
                    schemaName,
                    "OpenAI structured request preparation failed: " + AiCoverDiagnostics.safeExceptionSummary(ex),
                    ex
            );
        }
    }

    private void ensureConfigured() {
        if (!openAiEnabled) {
            throw new SocialCopyProviderException(
                    "OPENAI", SocialCopyFailureReason.DISABLED, null, null, "OpenAI is disabled."
            );
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new SocialCopyProviderException(
                    "OPENAI", SocialCopyFailureReason.CONFIGURATION, null, null,
                    "OpenAI is not configured. Please set OPENAI_API_KEY."
            );
        }
    }

    private String toDataUrl(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String mimeType = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")
                ? "image/jpeg"
                : fileName.endsWith(".webp") ? "image/webp" : "image/png";
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
    }

    private String extractOutputText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.path("output_text").isTextual() && !root.path("output_text").asText().isBlank()) {
            return root.path("output_text").asText();
        }
        String nested = findOutputText(root);
        if (nested != null) {
            return nested;
        }
        throw new AiCoverWorkflowException("OpenAI response did not contain structured JSON.");
    }

    private AiCoverWorkflowException openAiHttpFailure(int statusCode, String responseBody) {
        String detail = null;
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            String code = AiCoverDiagnostics.sanitize(error.path("code").asText(null));
            String type = AiCoverDiagnostics.sanitize(error.path("type").asText(null));
            String message = AiCoverDiagnostics.sanitize(error.path("message").asText(null));
            detail = String.join(", ", List.of(
                    code == null ? "" : "code=" + code,
                    type == null ? "" : "type=" + type,
                    message == null ? "" : "message=" + message
            ).stream().filter(value -> !value.isBlank()).toList());
        } catch (Exception ignored) {
            // The status code remains enough for a safe diagnostic.
        }
        String message = "OpenAI structured request failed with HTTP " + statusCode
                + (detail == null || detail.isBlank() ? "." : " (" + detail + ").");
        return new SocialCopyProviderException(
                "OPENAI",
                statusCode >= 500 ? SocialCopyFailureReason.TEMPORARY_ERROR : SocialCopyFailureReason.UNKNOWN,
                statusCode,
                null,
                message
        );
    }

    private AiCoverWorkflowException loggedFailure(String schemaName, String message, Throwable cause) {
        String safeMessage = AiCoverDiagnostics.sanitize(message);
        logger.warn("OpenAI structured analysis failed: schema={}, reason={}", schemaName, safeMessage, cause);
        return new SocialCopyProviderException(
                "OPENAI", SocialCopyFailureReason.TEMPORARY_ERROR, null, null, safeMessage, cause
        );
    }

    private Long retryAfterSeconds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                long seconds = java.time.Duration.between(
                        java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC),
                        java.time.ZonedDateTime.parse(value.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                ).toSeconds();
                return Math.max(0L, seconds);
            } catch (Exception invalidDate) {
                return null;
            }
        }
    }

    private String findOutputText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            if ("output_text".equals(node.path("type").asText()) && node.path("text").isTextual()) {
                return node.path("text").asText();
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findOutputText(fields.next().getValue());
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findOutputText(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String effectiveModel() {
        return model == null || model.isBlank() ? "gpt-5.5" : model.trim();
    }

    private long effectiveTimeout(String schemaName) {
        if ("application_scene_brief".equals(schemaName) && sceneBriefTimeoutSeconds > 0) {
            return sceneBriefTimeoutSeconds;
        }
        return readTimeoutSeconds > 0 ? readTimeoutSeconds : 180;
    }

    private long effectiveConnectTimeout() {
        return connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 20;
    }

    public record VisionInput(String label, Path path) {
    }
}
