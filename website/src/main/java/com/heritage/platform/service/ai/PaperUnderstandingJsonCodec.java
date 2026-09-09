package com.heritage.platform.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PaperUnderstandingJsonCodec {

    private final ObjectMapper objectMapper;

    PaperUnderstandingJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    OpenAiPaperUnderstanding parse(String json, boolean allowSingleRepair) throws JsonProcessingException {
        try {
            return fromRoot(objectMapper.readTree(json));
        } catch (JsonProcessingException firstFailure) {
            if (!allowSingleRepair) {
                throw firstFailure;
            }
            String repaired = repair(json);
            if (repaired.equals(json)) {
                throw firstFailure;
            }
            return fromRoot(objectMapper.readTree(repaired));
        }
    }

    private OpenAiPaperUnderstanding fromRoot(JsonNode root) throws JsonProcessingException {
        if (root == null || !root.isObject()) {
            throw new JsonProcessingException("Paper understanding output was not a JSON object.") { };
        }
        return new OpenAiPaperUnderstanding(
                text(root, "title"),
                text(root, "abstract_summary", "abstractSummary"),
                strings(root, "authors"),
                integer(root, "year"),
                text(root, "research_problem", "researchProblem"),
                text(root, "target_users_or_domain", "targetUsersOrDomain"),
                text(root, "method"),
                text(root, "key_implementation", "keyImplementation"),
                text(root, "key_contribution", "keyContribution"),
                strings(root, "important_system_components", "importantSystemComponents"),
                text(root, "input_output_relationship", "inputOutputRelationship"),
                text(root, "why_it_matters", "whyItMatters"),
                text(root, "possible_visual_metaphor", "possibleVisualMetaphor"),
                strings(root, "forbidden_visual_elements", "forbiddenVisualElements"),
                text(root, "likely_application_scenario", "likelyApplicationScenario"),
                strings(root, "visualizable_entities", "visualizableEntities"),
                strings(root, "visualizable_interactions", "visualizableInteractions"),
                text(root, "visualizable_environment", "visualizableEnvironment", "application_environment", "applicationEnvironment"),
                strings(root, "warnings"),
                confidence(text(root, "confidence_level", "confidenceLevel")),
                text(root, "venue"),
                text(root, "proposed_system_or_method", "proposedSystemOrMethod"),
                text(root, "visible_interface_or_device", "visibleInterfaceOrDevice"),
                text(root, "visible_input", "visibleInput"),
                text(root, "visible_output", "visibleOutput"),
                text(root, "expected_outcome", "expectedOutcome"),
                strings(root, "must_show_elements", "mustShowElements"),
                strings(root, "must_avoid_elements", "mustAvoidElements"),
                text(root, "application_environment", "applicationEnvironment", "visualizable_environment", "visualizableEnvironment"),
                text(root, "main_task_or_workflow", "mainTaskOrWorkflow"),
                text(root, "visible_interaction", "visibleInteraction"),
                text(root, "alternative_visual_metaphor", "alternativeVisualMetaphor")
        );
    }

    private String repair(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            value = value.substring(start, end + 1);
        }
        return value.replaceAll(",\\s*([}\\]])", "$1");
    }

    private String text(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (node.isTextual()) {
                String value = clean(node.asText());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private Integer integer(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isIntegralNumber() ? node.asInt() : null;
    }

    private List<String> strings(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (!node.isArray()) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.isTextual() ? clean(item.asText()) : null;
                if (value != null && !values.contains(value)) {
                    values.add(value);
                }
            }
            return List.copyOf(values);
        }
        return List.of();
    }

    private String confidence(String raw) {
        String value = raw == null ? "LOW" : raw.trim().toUpperCase(Locale.ROOT);
        return Set.of("HIGH", "MEDIUM", "LOW").contains(value) ? value : "LOW";
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
