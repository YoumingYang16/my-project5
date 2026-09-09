package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaPaperUnderstandingService implements PaperUnderstandingProvider {

    private static final Logger logger = LoggerFactory.getLogger(OllamaPaperUnderstandingService.class);

    @Value("${ollama.enabled:false}")
    private boolean enabled;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:qwen3:14b}")
    private String model;

    @Value("${ollama.timeout-seconds:240}")
    private long timeoutSeconds;

    @Value("${ollama.temperature:0.1}")
    private double temperature;

    @Value("${ollama.num-ctx:8192}")
    private int numCtx;

    @Value("${ollama.max-input-chars:16000}")
    private int maxInputChars;

    @Value("${ollama.use-structured-output:true}")
    private boolean useStructuredOutput;

    private final ObjectMapper objectMapper;
    private final PaperUnderstandingJsonCodec codec;

    public OllamaPaperUnderstandingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.codec = new PaperUnderstandingJsonCodec(objectMapper);
    }

    @Override
    public String providerName() {
        return "OLLAMA";
    }

    @Override
    public String modelName() {
        return effectiveModel();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint("/api/tags"))
                    .timeout(Duration.ofSeconds(Math.min(effectiveTimeout(), 10)))
                    .GET()
                    .build();
            HttpResponse<String> response = client().send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            boolean modelPresent = response.statusCode() >= 200 && response.statusCode() < 300
                    && containsConfiguredModel(response.body());
            logger.info(
                    "Ollama availability check completed: baseUrl={}, model={}, status={}, modelPresent={}",
                    safeBaseUrl(), effectiveModel(), response.statusCode(), modelPresent
            );
            return modelPresent;
        } catch (Exception ex) {
            logger.warn(
                    "Ollama availability check failed: baseUrl={}, model={}, failure={}",
                    safeBaseUrl(), effectiveModel(), AiCoverDiagnostics.safeExceptionSummary(ex)
            );
            return false;
        }
    }

    @Override
    public OpenAiPaperUnderstanding understand(PaperEvidencePacket evidencePacket) {
        if (!enabled) {
            throw new AiCoverWorkflowException("Local Ollama paper understanding is disabled.");
        }
        if (evidencePacket == null || evidencePacket.compactEvidenceText().isBlank()) {
            throw new AiCoverWorkflowException("Ollama paper understanding failed: no compact paper evidence was available.");
        }
        String evidence = evidencePacket.compactEvidenceText();
        int limit = maxInputChars <= 0 ? 16000 : maxInputChars;
        if (evidence.length() > limit) {
            evidence = evidence.substring(0, limit).trim();
        }

        logger.info(
                "Ollama paper understanding started: baseUrl={}, model={}, timeoutSeconds={}, evidenceCharacters={}",
                safeBaseUrl(), effectiveModel(), effectiveTimeout(), evidence.length()
        );
        try {
            Map<String, Object> body = requestBody(evidence);
            HttpRequest request = HttpRequest.newBuilder(endpoint("/api/chat"))
                    .timeout(Duration.ofSeconds(effectiveTimeout()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8
                    ))
                    .build();
            HttpResponse<String> response = client().send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiCoverWorkflowException("Ollama request failed with HTTP " + response.statusCode() + ".");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("message").path("content");
            if (!contentNode.isTextual() || contentNode.asText().isBlank()) {
                throw new AiCoverWorkflowException("Ollama response did not contain message.content JSON.");
            }
            OpenAiPaperUnderstanding understanding = codec.parse(contentNode.asText(), true);
            logger.info(
                    "Ollama paper understanding succeeded: model={}, titlePresent={}, componentCount={}, confidence={}",
                    effectiveModel(), understanding.title() != null,
                    understanding.importantSystemComponents().size(), understanding.confidenceLevel()
            );
            return understanding;
        } catch (AiCoverWorkflowException ex) {
            logger.warn("Ollama paper understanding failed: {}", AiCoverDiagnostics.safeExceptionSummary(ex));
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw failure("Ollama paper understanding was interrupted.", ex);
        } catch (Exception ex) {
            throw failure("Ollama paper understanding failed: " + AiCoverDiagnostics.safeExceptionSummary(ex), ex);
        }
    }

    Map<String, Object> requestBody(String compactEvidence) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", effectiveModel());
        body.put("stream", false);
        body.put("think", false);
        // Paper understanding always requires machine-parseable output. The
        // property is retained for deployment visibility, but JSON remains a
        // safety invariant for this provider.
        body.put("format", "json");
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(compactEvidence))
        ));
        body.put("options", Map.of(
                "temperature", effectiveTemperature(),
                "num_ctx", effectiveNumCtx()
        ));
        return body;
    }

    private String systemPrompt() {
        return """
                You are a paper-understanding assistant for generating representative cover images for academic papers.
                Your job is not to write an abstract. Extract the paper's proposed system, method, product, tool, model, or contribution and identify how it can be shown in a realistic application scene.
                Return strict JSON only. Do not use markdown. Do not include explanations. Do not include thinking.
                Do not invent unsupported claims, numbers, results, deployments, awards, actors, devices, or environments.
                If evidence is uncertain, use cautious wording, lower confidence, and add a warning.
                """;
    }

    private String userPrompt(String evidence) {
        return """
                Analyze the compact, domain-independent paper evidence below. Return one JSON object using exactly these fields:
                title, abstract_summary, authors, year, venue, research_problem, target_users_or_domain,
                method, proposed_system_or_method, key_implementation, key_contribution,
                important_system_components, input_output_relationship, likely_application_scenario,
                visualizable_entities, visualizable_interactions, visualizable_environment,
                visible_interface_or_device, visible_input, visible_output, expected_outcome,
                why_it_matters, possible_visual_metaphor, must_show_elements, must_avoid_elements,
                forbidden_visual_elements, confidence_level, warnings.

                Arrays: authors, important_system_components, visualizable_entities,
                visualizable_interactions, must_show_elements, must_avoid_elements,
                forbidden_visual_elements, warnings. Year is an integer or null.
                confidence_level is HIGH, MEDIUM, or LOW.

                Focus on what the paper proposes, who uses or benefits from it, the application domain,
                the real or plausible use environment, the main task or workflow, the visible system artifact,
                interface, input, output, interaction, expected outcome, and concrete visual elements.
                The scene must show the proposed contribution being used, applied, demonstrated, or experienced.
                Do not substitute a generic topic illustration for the contribution. Do not infer a user-facing
                device or physical deployment when the evidence only supports an analytical or computational workflow.

                Compact paper evidence:
                """ + evidence;
    }

    private boolean containsConfiguredModel(String body) {
        try {
            JsonNode models = objectMapper.readTree(body).path("models");
            if (!models.isArray()) {
                return false;
            }
            for (JsonNode item : models) {
                String name = item.path("name").asText("");
                String modelValue = item.path("model").asText("");
                if (effectiveModel().equalsIgnoreCase(name) || effectiveModel().equalsIgnoreCase(modelValue)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(effectiveTimeout(), 20)))
                .build();
    }

    private URI endpoint(String path) {
        return URI.create(safeBaseUrl() + path);
    }

    private String safeBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String effectiveModel() {
        return model == null || model.isBlank() ? "qwen3:14b" : model.trim();
    }

    private long effectiveTimeout() {
        return timeoutSeconds <= 0 ? 240 : timeoutSeconds;
    }

    private double effectiveTemperature() {
        return temperature < 0 || temperature > 2 ? 0.1 : temperature;
    }

    private int effectiveNumCtx() {
        return numCtx <= 0 ? 8192 : numCtx;
    }

    private AiCoverWorkflowException failure(String message, Throwable cause) {
        String safe = AiCoverDiagnostics.sanitize(message);
        logger.warn("Ollama paper understanding failed: {}", safe);
        return new AiCoverWorkflowException(safe, cause);
    }
}
