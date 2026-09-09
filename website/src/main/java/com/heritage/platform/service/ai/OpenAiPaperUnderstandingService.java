package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OpenAiPaperUnderstandingService implements PaperUnderstandingProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiPaperUnderstandingService.class);
    private static final String RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses";

    @Value("${openai.enabled:true}")
    private boolean openAiEnabled;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.paper-understanding-model:gpt-5.4-mini}")
    private String model;

    @Value("${openai.paper-understanding-reasoning-effort:none}")
    private String reasoningEffort;

    @Value("${openai.paper-understanding-max-output-tokens:4000}")
    private int maxOutputTokens;

    @Value("${openai.connect-timeout-seconds:20}")
    private long connectTimeoutSeconds;

    @Value("${openai.read-timeout-seconds:${openai.timeout-seconds:180}}")
    private long readTimeoutSeconds;

    @Value("${openai.paper-understanding-timeout-seconds:180}")
    private long paperUnderstandingTimeoutSeconds;

    private final ObjectMapper objectMapper;

    public OpenAiPaperUnderstandingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenAiPaperUnderstanding understand(PaperEvidencePacket evidencePacket) {
        if (!openAiEnabled) {
            throw new AiCoverWorkflowException("OpenAI is disabled.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiCoverWorkflowException("OpenAI is not configured. Please set OPENAI_API_KEY.");
        }

        if (evidencePacket == null || evidencePacket.compactEvidenceText().isBlank()) {
            throw new AiCoverWorkflowException("OpenAI paper understanding failed: no compact paper evidence was available.");
        }

        logger.info(
                "OpenAI paper understanding started: model={}, evidenceCharacters={}, connectTimeoutSeconds={}, requestTimeoutSeconds={}",
                effectiveModel(),
                evidencePacket.characterCount(),
                effectiveConnectTimeout(),
                effectiveTimeout()
        );

        String responseText;
        try {
            responseText = requestUnderstanding(evidencePacket);
        } catch (AiCoverWorkflowException ex) {
            logger.warn("OpenAI paper understanding request failed: {}", AiCoverDiagnostics.safeExceptionSummary(ex));
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw loggedFailure("OpenAI request timed out after " + effectiveTimeout() + " seconds.", ex);
        } catch (ConnectException ex) {
            throw loggedFailure("OpenAI connection failed. Check network or proxy settings.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw loggedFailure("OpenAI paper understanding was interrupted.", ex);
        } catch (IOException ex) {
            throw loggedFailure("OpenAI request failed: " + AiCoverDiagnostics.safeExceptionSummary(ex), ex);
        } catch (RuntimeException ex) {
            throw loggedFailure("OpenAI request preparation failed: " + AiCoverDiagnostics.safeExceptionSummary(ex), ex);
        }

        try {
            OpenAiPaperUnderstanding understanding = parseUnderstanding(responseText);
            logger.info(
                    "OpenAI paper understanding succeeded: model={}, titlePresent={}, componentCount={}, confidence={}",
                    effectiveModel(),
                    understanding.title() != null,
                    understanding.importantSystemComponents().size(),
                    understanding.confidenceLevel()
            );
            return understanding;
        } catch (Exception ex) {
            throw loggedFailure(
                    "OpenAI response parsing failed: " + AiCoverDiagnostics.safeExceptionSummary(ex),
                    ex
            );
        }
    }

    private String requestUnderstanding(PaperEvidencePacket evidencePacket) throws IOException, InterruptedException {
        long effectiveTimeout = effectiveTimeout();
        Map<String, Object> requestBody = requestBody(evidencePacket);

        HttpRequest request = HttpRequest.newBuilder(URI.create(RESPONSES_ENDPOINT))
                .timeout(Duration.ofSeconds(effectiveTimeout))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(effectiveConnectTimeout()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        logger.info(
                "OpenAI paper understanding response received: status={}, requestIdPresent={}",
                response.statusCode(),
                response.headers().firstValue("x-request-id").isPresent()
        );
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new AiCoverWorkflowException("OpenAI authentication failed. Please check OPENAI_API_KEY.");
        }
        if (response.statusCode() == 429) {
            throw new AiCoverWorkflowException("OpenAI rate limit was reached. Please try again later.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw openAiHttpFailure(response.statusCode(), response.body());
        }
        return extractOutputText(response.body());
    }

    Map<String, Object> requestBody(PaperEvidencePacket evidencePacket) {
        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "input_text");
        textContent.put("text", buildPrompt(evidencePacket));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(textContent));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", effectiveModel());
        requestBody.put("input", List.of(message));
        requestBody.put("text", Map.of("format", responseFormat()));
        requestBody.put("reasoning", Map.of("effort", effectiveReasoningEffort()));
        requestBody.put("max_output_tokens", effectiveMaxOutputTokens());
        requestBody.put("store", false);
        return requestBody;
    }

    private String buildPrompt(PaperEvidencePacket evidencePacket) {
        return """
                Analyze the compact evidence packet from an academic paper. Produce an authoritative, structured understanding for grounded social copy and a realistic product-in-use or system-in-use cover scene.

                The evidence was selected from bibliographic metadata, section snippets, keywords, and figure or table captions. Treat it as the complete source for this request. Do not assume a particular research domain.

                Return only valid JSON with these fields:
                {
                  "title": "",
                  "abstract_summary": "",
                  "authors": [],
                  "year": null,
                  "venue": "",
                  "research_problem": "",
                  "target_users_or_domain": "",
                  "method": "",
                  "proposed_system_or_method": "",
                  "key_implementation": "",
                  "key_contribution": "",
                  "important_system_components": [],
                  "input_output_relationship": "",
                  "why_it_matters": "",
                  "likely_application_scenario": "",
                  "application_environment": "",
                  "main_task_or_workflow": "",
                  "visualizable_entities": [],
                  "visualizable_interactions": [],
                  "visualizable_environment": "",
                  "visible_interface_or_device": "",
                  "visible_input": "",
                  "visible_output": "",
                  "visible_interaction": "",
                  "expected_outcome": "",
                  "possible_visual_metaphor": "",
                  "must_show_elements": [],
                  "must_avoid_elements": [],
                  "forbidden_visual_elements": [],
                  "warnings": [],
                  "confidence_level": "HIGH | MEDIUM | LOW"
                }

                Rules:
                - Do not invent unsupported details.
                - Do not merely summarize the abstract. Identify what the paper proposes, builds, designs, evaluates, or contributes.
                - Identify the actual product, system, method, tool, model, or interface and how it is used.
                - Identify the intended user or stakeholder, application environment, main task or workflow, visible interaction, and outcome.
                - Keep uncertainty local to the unclear field; use the strongest supported evidence elsewhere.
                - Work across research domains and paper types. Never assume AR, a phone, a museum, heritage, or any other domain-specific object unless supported.
                - Focus on the core problem, method, implementation or mechanism, contribution, application context, and visible evidence.
                - Avoid generic summaries and marketing-only wording.
                - Avoid hallucinating fake system components.
                - Never invent results, metrics, deployments, awards, experiments, or user studies.
                - Never use placeholder values or repeat the title as multiple semantic fields.
                - Visualizable entities and interactions must come from the evidence, not generic technology imagery.
                - Describe a real or plausible usage setting supported by the paper, while clearly marking uncertainty in warnings.
                - Forbidden elements should include unsupported objects, environments, or visual claims plus common image-generation defects.

                Compact paper evidence:
                """ + evidencePacket.compactEvidenceText();
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(
                "title",
                "abstract_summary",
                "authors",
                "year",
                "venue",
                "research_problem",
                "target_users_or_domain",
                "method",
                "proposed_system_or_method",
                "key_implementation",
                "key_contribution",
                "important_system_components",
                "input_output_relationship",
                "why_it_matters",
                "likely_application_scenario",
                "application_environment",
                "main_task_or_workflow",
                "visualizable_entities",
                "visualizable_interactions",
                "visualizable_environment",
                "visible_interface_or_device",
                "visible_input",
                "visible_output",
                "visible_interaction",
                "expected_outcome",
                "possible_visual_metaphor",
                "must_show_elements",
                "must_avoid_elements",
                "forbidden_visual_elements",
                "warnings",
                "confidence_level"
        ));
        schema.put("properties", Map.ofEntries(
                Map.entry("title", Map.of("type", "string")),
                Map.entry("abstract_summary", Map.of("type", "string")),
                Map.entry("authors", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("year", Map.of("type", List.of("integer", "null"))),
                Map.entry("venue", Map.of("type", "string")),
                Map.entry("research_problem", Map.of("type", "string")),
                Map.entry("target_users_or_domain", Map.of("type", "string")),
                Map.entry("method", Map.of("type", "string")),
                Map.entry("proposed_system_or_method", Map.of("type", "string")),
                Map.entry("key_implementation", Map.of("type", "string")),
                Map.entry("key_contribution", Map.of("type", "string")),
                Map.entry("important_system_components", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("input_output_relationship", Map.of("type", "string")),
                Map.entry("why_it_matters", Map.of("type", "string")),
                Map.entry("likely_application_scenario", Map.of("type", "string")),
                Map.entry("application_environment", Map.of("type", "string")),
                Map.entry("main_task_or_workflow", Map.of("type", "string")),
                Map.entry("visualizable_entities", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("visualizable_interactions", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("visualizable_environment", Map.of("type", "string")),
                Map.entry("visible_interface_or_device", Map.of("type", "string")),
                Map.entry("visible_input", Map.of("type", "string")),
                Map.entry("visible_output", Map.of("type", "string")),
                Map.entry("visible_interaction", Map.of("type", "string")),
                Map.entry("expected_outcome", Map.of("type", "string")),
                Map.entry("possible_visual_metaphor", Map.of("type", "string")),
                Map.entry("must_show_elements", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("must_avoid_elements", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("forbidden_visual_elements", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("warnings", Map.of("type", "array", "items", Map.of("type", "string"))),
                Map.entry("confidence_level", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW")))
        ));

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "paper_understanding");
        format.put("schema", schema);
        format.put("strict", true);
        return format;
    }

    private String extractOutputText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }
        String recursive = findOutputText(root);
        if (recursive != null) {
            return recursive;
        }
        String status = root.path("status").asText("unknown");
        String incompleteReason = root.path("incomplete_details").path("reason").asText(null);
        String refusal = findRefusal(root);
        if (refusal != null) {
            throw new AiCoverWorkflowException("OpenAI refused the paper understanding request: " + refusal);
        }
        String detail = incompleteReason == null ? "status=" + status : "status=" + status + ", reason=" + incompleteReason;
        throw new AiCoverWorkflowException("OpenAI response did not contain structured JSON (" + detail + ").");
    }

    private String findOutputText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode type = node.path("type");
            JsonNode text = node.path("text");
            if (type.isTextual() && "output_text".equals(type.asText()) && text.isTextual()) {
                return text.asText();
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findOutputText(fields.next().getValue());
                if (found != null) {
                    return found;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findOutputText(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String findRefusal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            if ("refusal".equals(node.path("type").asText()) && node.path("refusal").isTextual()) {
                return AiCoverDiagnostics.sanitize(node.path("refusal").asText());
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findRefusal(fields.next().getValue());
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findRefusal(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private OpenAiPaperUnderstanding parseUnderstanding(String responseText) throws IOException {
        return new PaperUnderstandingJsonCodec(objectMapper).parse(responseText, false);
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() ? clean(node.asText()) : null;
    }

    private Integer integer(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }

    private List<String> stringList(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.isTextual() ? clean(item.asText()) : null;
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private String confidence(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        return switch (value) {
            case "HIGH", "MEDIUM", "LOW" -> value;
            default -> "LOW";
        };
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
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
        return new AiCoverWorkflowException(
                "OpenAI request failed with HTTP " + statusCode + (detail == null || detail.isBlank() ? "." : " (" + detail + ").")
        );
    }

    private AiCoverWorkflowException loggedFailure(String message, Throwable cause) {
        String safeMessage = AiCoverDiagnostics.sanitize(message);
        logger.warn("OpenAI paper understanding failed: {}", safeMessage, cause);
        return new AiCoverWorkflowException(safeMessage, cause);
    }

    public String modelName() {
        return effectiveModel();
    }

    public boolean isConfigured() {
        return openAiEnabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String providerName() {
        return "OPENAI";
    }

    @Override
    public boolean isEnabled() {
        return openAiEnabled;
    }

    @Override
    public boolean isAvailable() {
        return isConfigured();
    }

    public long configuredRequestTimeoutSeconds() {
        return effectiveTimeout();
    }

    private String effectiveModel() {
        return model == null || model.isBlank() ? "gpt-5.4-mini" : model.trim();
    }

    private String effectiveReasoningEffort() {
        String value = reasoningEffort == null ? "" : reasoningEffort.trim().toLowerCase(Locale.ROOT);
        return Set.of("none", "low", "medium", "high", "xhigh").contains(value) ? value : "none";
    }

    private int effectiveMaxOutputTokens() {
        return maxOutputTokens <= 0 ? 4000 : maxOutputTokens;
    }

    private long effectiveTimeout() {
        if (paperUnderstandingTimeoutSeconds > 0) {
            return paperUnderstandingTimeoutSeconds;
        }
        return readTimeoutSeconds > 0 ? readTimeoutSeconds : 180;
    }

    private long effectiveConnectTimeout() {
        return connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 20;
    }

}
