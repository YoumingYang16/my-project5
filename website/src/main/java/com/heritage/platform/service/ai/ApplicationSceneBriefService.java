package com.heritage.platform.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.ApplicationSceneBrief;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApplicationSceneBriefService {

    private static final Set<String> SCENE_TYPES = Set.of(
            "APPLICATION_SCENE",
            "USER_IN_CONTEXT_APPLICATION",
            "PRODUCT_IN_USE",
            "TASK_ASSISTANCE",
            "ENVIRONMENT_AND_OUTCOME",
            "INTERFACE_OR_DEVICE_USE",
            "FIELD_DEPLOYMENT",
            "GENERAL_RESEARCH_SUMMARY"
    );

    @Value("${ai-cover.scene-brief-enabled:false}")
    private boolean enabled;

    private final ObjectMapper objectMapper;
    private final OpenAiStructuredResponseClient openAiClient;

    public ApplicationSceneBriefService(
            ObjectMapper objectMapper,
            OpenAiStructuredResponseClient openAiClient
    ) {
        this.objectMapper = objectMapper;
        this.openAiClient = openAiClient;
    }

    public ApplicationSceneBrief create(
            FinalPaperUnderstanding understanding,
            ReferenceGroundingContext grounding
    ) {
        return create(understanding, grounding, "UNKNOWN");
    }

    public ApplicationSceneBrief create(
            FinalPaperUnderstanding understanding,
            ReferenceGroundingContext grounding,
            String sourceProvider
    ) {
        if (!enabled) {
            return conservativeFallback(understanding, grounding, List.of(), sourceProvider);
        }
        String response = openAiClient.requestJson(
                buildPrompt(understanding, grounding),
                "application_scene_brief",
                responseSchema(),
                List.of()
        );
        try {
            return parse(response, understanding, grounding, sourceProvider);
        } catch (JsonProcessingException ex) {
            throw new AiCoverWorkflowException("Application scene brief JSON could not be parsed.", ex);
        }
    }

    ApplicationSceneBrief parse(
            String json,
            FinalPaperUnderstanding understanding,
            ReferenceGroundingContext grounding
    ) throws JsonProcessingException {
        return parse(json, understanding, grounding, "UNKNOWN");
    }

    private ApplicationSceneBrief parse(
            String json,
            FinalPaperUnderstanding understanding,
            ReferenceGroundingContext grounding,
            String sourceProvider
    ) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        ApplicationSceneBrief fallback = conservativeFallback(understanding, grounding, List.of(), sourceProvider);
        List<String> warnings = new ArrayList<>(strings(root, "warnings"));
        if (hasMissingRequiredField(root)) {
            warnings.add("Some application scene fields were missing and were filled conservatively.");
        }

        String sceneType = cleanText(root, "paperSceneType");
        if (!SCENE_TYPES.contains(sceneType)) {
            sceneType = fallback.paperSceneType();
            warnings.add("The scene type was unclear, so a conservative scene type was selected.");
        }

        return new ApplicationSceneBrief(
                sceneType,
                valueOrFallback(root, "targetUser", fallback.targetUser()),
                valueOrFallback(root, "userRole", fallback.userRole()),
                valueOrFallback(root, "environment", fallback.environment()),
                valueOrFallback(root, "technologyOrProduct", fallback.technologyOrProduct()),
                valueOrFallback(root, "technologyFormFactor", fallback.technologyFormFactor()),
                valueOrFallback(root, "mainTask", fallback.mainTask()),
                valueOrFallback(root, "taskContext", fallback.taskContext()),
                valueOrFallback(root, "supportAction", fallback.supportAction()),
                valueOrFallback(root, "expectedOutcome", fallback.expectedOutcome()),
                valueOrFallback(root, "sceneMoment", fallback.sceneMoment()),
                listOrFallback(root, "visibleObjects", fallback.visibleObjects()),
                listOrFallback(root, "visibleInteractions", fallback.visibleInteractions()),
                listOrFallback(root, "domainSpecificDetails", fallback.domainSpecificDetails()),
                listOrFallback(root, "mustShowElements", fallback.mustShowElements()),
                listOrFallback(root, "mustNotShowElements", fallback.mustNotShowElements()),
                valueOrFallback(root, "realismNotes", fallback.realismNotes()),
                valueOrFallback(root, "compositionHint", fallback.compositionHint()),
                valueOrFallback(root, "cameraView", fallback.cameraView()),
                valueOrFallback(root, "styleDirection", fallback.styleDirection()),
                confidence(valueOrFallback(root, "confidenceLevel", fallback.confidenceLevel())),
                distinct(warnings),
                normalizedSource(sourceProvider)
        );
    }

    ApplicationSceneBrief conservativeFallback(
            FinalPaperUnderstanding understanding,
            ReferenceGroundingContext grounding,
            List<String> warnings
    ) {
        return conservativeFallback(understanding, grounding, warnings, "UNKNOWN");
    }

    ApplicationSceneBrief conservativeFallback(
            FinalPaperUnderstanding understanding,
            ReferenceGroundingContext grounding,
            List<String> warnings,
            String sourceProvider
    ) {
        String targetUser = firstUseful(
                understanding == null ? null : understanding.targetUsersOrDomain(),
                first(safe(grounding == null ? null : grounding.userGroupMentions())),
                "the primary subject or operator described by the paper"
        );
        String technology = firstUseful(
                understanding == null ? null : understanding.proposedSystemOrMethod(),
                understanding == null ? null : understanding.keyImplementation(),
                understanding == null ? null : understanding.method(),
                productName(understanding == null ? null : understanding.title()),
                "the proposed research system"
        );
        String environment = firstUseful(
                understanding == null ? null : understanding.applicationEnvironment(),
                understanding == null ? null : understanding.visualizableEnvironment(),
                first(safe(grounding == null ? null : grounding.environmentMentions())),
                understanding == null ? null : understanding.likelyApplicationScenario(),
                "the paper-supported application or analytical context"
        );
        String task = firstUseful(
                understanding == null ? null : understanding.mainTaskOrWorkflow(),
                first(safe(understanding == null ? null : understanding.visualizableInteractions())),
                understanding == null ? null : understanding.likelyApplicationScenario(),
                understanding == null ? null : understanding.researchProblem(),
                "performing the task addressed by the paper"
        );
        String formFactor = firstUseful(
                understanding == null ? null : understanding.visibleInterfaceOrDevice(),
                first(safe(understanding == null ? null : understanding.importantSystemComponents())),
                first(safe(understanding == null ? null : understanding.visualizableEntities())),
                first(safe(grounding == null ? null : grounding.interfaceOrSystemMentions())),
                "a concrete representation of the central method or system"
        );
        String supportAction = firstUseful(
                understanding == null ? null : understanding.visibleInteraction(),
                understanding == null ? null : understanding.inputOutputRelationship(),
                "the proposed technology assists the user during the main task"
        );
        String outcome = firstUseful(
                understanding == null ? null : understanding.expectedOutcome(),
                understanding == null ? null : understanding.whyItMatters(),
                understanding == null ? null : understanding.keyContribution(),
                "a practical improvement supported by the proposed system"
        );
        List<String> objects = mergeLimited(6,
                List.of(formFactor),
                safe(understanding == null ? null : understanding.visualizableEntities()),
                safe(understanding == null ? null : understanding.importantSystemComponents()),
                safe(grounding == null ? null : grounding.extractedVisualClues())
        );
        List<String> mustShow = mergeLimited(7,
                safe(understanding == null ? null : understanding.mustShowElements()),
                List.of(targetUser, formFactor, environment),
                safe(understanding == null ? null : understanding.visualizableEntities()),
                safe(understanding == null ? null : understanding.importantSystemComponents())
        );
        List<String> visibleInteractions = mergeLimited(5,
                safe(understanding == null ? null : understanding.visualizableInteractions()),
                List.of(task)
        );
        String sceneType = !visibleInteractions.isEmpty() || !objects.isEmpty()
                ? "APPLICATION_SCENE"
                : "GENERAL_RESEARCH_SUMMARY";
        List<String> fallbackWarnings = new ArrayList<>(warnings == null ? List.of() : warnings);

        return new ApplicationSceneBrief(
                sceneType,
                targetUser,
                targetUser,
                environment,
                technology,
                formFactor,
                task,
                "the normal operating context described or implied by the paper",
                supportAction,
                outcome,
                firstUseful(
                        understanding == null ? null : understanding.likelyApplicationScenario(),
                        targetUser + " engaging with " + formFactor + " while " + task
                ),
                objects,
                visibleInteractions,
                mergeLimited(6,
                        safe(understanding == null ? null : understanding.visualizableEntities()),
                        safe(understanding == null ? null : understanding.visualizableInteractions()),
                        safe(understanding == null ? null : understanding.importantSystemComponents()),
                        safe(grounding == null ? null : grounding.extractedVisualClues())
                ),
                mustShow,
                mergeLimited(12,
                        safe(understanding == null ? null : understanding.mustAvoidElements()),
                        safe(understanding == null ? null : understanding.forbiddenVisualElements())
                ),
                "Use only supported details, natural body posture, plausible equipment, and realistic lighting.",
                "One-glance visual story: user, proposed contribution, action, real setting, and visible response in one coherent frame.",
                "participant-level or over-the-shoulder product-in-use view when supported",
                "clear editorial application scene, realistic or polished illustrative",
                understanding == null ? "LOW" : confidence(understanding.confidenceLevel()),
                distinct(fallbackWarnings),
                normalizedSource(sourceProvider)
        );
    }

    public boolean usesOpenAi() {
        return enabled;
    }

    private String buildPrompt(FinalPaperUnderstanding understanding, ReferenceGroundingContext grounding) {
        return """
                Read the paper understanding and reference evidence below. Infer the clearest realistic application scene for the paper's proposed system, method, product, tool, model, or contribution.

                The finished image must let a non-expert understand, at a glance, what the paper built or proposed and how it is used, applied, demonstrated, or experienced. Design one coherent moment containing the relevant actor or stakeholder when supported, the paper's contribution, its task or workflow, the setting, and an immediate visible input, output, response, or benefit.

                Rules:
                - Choose one exact use moment, not a general summary of the field and not a list of components.
                - Make the relationship between the person's action, the proposed system, and the real-world subject unmistakable in a single frame.
                - The proposed contribution must be visually identifiable and central enough that the scene would not still mean the same thing without it.
                - Do not invent unsupported scenario details.
                - Do not create a futuristic scene unless the paper clearly supports it.
                - Do not invent actors, equipment, interfaces, environments, or visual effects unless supported or strongly implied.
                - If product appearance is unclear, describe it generically but plausibly.
                - For software-only technology, use a realistic device or workstation only when appropriate.
                - For an algorithm without a user-facing system, show a realistic environment where its output is used.
                - For theoretical work, use GENERAL_RESEARCH_SUMMARY, keep details conservative, and set confidence LOW.
                - Avoid generic AI backgrounds, fake equations, fake UI text, fake code, and fake logos.
                - Return only valid JSON matching the required schema.

                Paper understanding:
                """ + json(understanding) + "\n\nReference grounding:\n" + json(grounding);
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String field : List.of(
                "targetUser", "userRole", "environment", "technologyOrProduct", "technologyFormFactor",
                "mainTask", "taskContext", "supportAction", "expectedOutcome", "sceneMoment", "realismNotes",
                "compositionHint", "cameraView", "styleDirection"
        )) {
            properties.put(field, Map.of("type", "string"));
        }
        for (String field : List.of(
                "visibleObjects", "visibleInteractions", "domainSpecificDetails", "mustShowElements",
                "mustNotShowElements", "warnings"
        )) {
            properties.put(field, Map.of("type", "array", "items", Map.of("type", "string")));
        }
        properties.put("paperSceneType", Map.of("type", "string", "enum", SCENE_TYPES.stream().sorted().toList()));
        properties.put("confidenceLevel", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("properties", properties);
        return schema;
    }

    private boolean hasMissingRequiredField(JsonNode root) {
        return root == null || !root.isObject()
                || cleanText(root, "targetUser") == null
                || cleanText(root, "environment") == null
                || cleanText(root, "technologyOrProduct") == null
                || cleanText(root, "mainTask") == null;
    }

    private String valueOrFallback(JsonNode root, String field, String fallback) {
        String value = cleanText(root, field);
        return value == null ? fallback : value;
    }

    private List<String> listOrFallback(JsonNode root, String field, List<String> fallback) {
        List<String> values = strings(root, field);
        return values.isEmpty() ? safe(fallback) : values;
    }

    private String cleanText(JsonNode root, String field) {
        if (root == null || !root.path(field).isTextual()) {
            return null;
        }
        String value = root.path(field).asText().replaceAll("\\s+", " ").trim();
        return value.isEmpty() ? null : value;
    }

    private List<String> strings(JsonNode root, String field) {
        if (root == null || !root.path(field).isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : root.path(field)) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().replaceAll("\\s+", " ").trim());
            }
        }
        return distinct(values);
    }

    private String confidence(String value) {
        String normalized = value == null ? "LOW" : value.trim().toUpperCase();
        return Set.of("HIGH", "MEDIUM", "LOW").contains(normalized) ? normalized : "LOW";
    }

    private boolean isSpecific(String value) {
        return value != null && !value.equals("the intended user") && !value.equalsIgnoreCase("not clearly specified");
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"not clearly specified".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "not clearly specified";
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String productName(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        int separator = title.indexOf(':');
        if (separator > 1 && separator <= 80) {
            return title.substring(0, separator).trim();
        }
        return null;
    }

    @SafeVarargs
    private final List<String> mergeLimited(int limit, List<String>... lists) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (List<String> list : lists) {
            values.addAll(safe(list));
        }
        return values.stream().limit(limit).toList();
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(safe(values)));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String normalizedSource(String source) {
        if (source != null && source.toUpperCase().contains("OPENAI")) {
            return "OPENAI";
        }
        return source != null && source.toUpperCase().contains("EVIDENCE_FALLBACK")
                ? "EVIDENCE_FALLBACK" : "UNKNOWN";
    }
}
