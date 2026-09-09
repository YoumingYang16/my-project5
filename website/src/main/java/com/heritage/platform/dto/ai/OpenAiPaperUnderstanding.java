package com.heritage.platform.dto.ai;

import java.util.List;

public record OpenAiPaperUnderstanding(
        String title,
        String abstractSummary,
        List<String> authors,
        Integer year,
        String researchProblem,
        String targetUsersOrDomain,
        String method,
        String keyImplementation,
        String keyContribution,
        List<String> importantSystemComponents,
        String inputOutputRelationship,
        String whyItMatters,
        String possibleVisualMetaphor,
        List<String> forbiddenVisualElements,
        String likelyApplicationScenario,
        List<String> visualizableEntities,
        List<String> visualizableInteractions,
        String visualizableEnvironment,
        List<String> warnings,
        String confidenceLevel,
        String venue,
        String proposedSystemOrMethod,
        String visibleInterfaceOrDevice,
        String visibleInput,
        String visibleOutput,
        String expectedOutcome,
        List<String> mustShowElements,
        List<String> mustAvoidElements,
        String applicationEnvironment,
        String mainTaskOrWorkflow,
        String visibleInteraction,
        String alternativeVisualMetaphor
) {
    public OpenAiPaperUnderstanding {
        authors = authors == null ? List.of() : List.copyOf(authors);
        importantSystemComponents = importantSystemComponents == null ? List.of() : List.copyOf(importantSystemComponents);
        forbiddenVisualElements = forbiddenVisualElements == null ? List.of() : List.copyOf(forbiddenVisualElements);
        visualizableEntities = visualizableEntities == null ? List.of() : List.copyOf(visualizableEntities);
        visualizableInteractions = visualizableInteractions == null ? List.of() : List.copyOf(visualizableInteractions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        mustShowElements = mustShowElements == null ? List.of() : List.copyOf(mustShowElements);
        mustAvoidElements = mustAvoidElements == null ? List.of() : List.copyOf(mustAvoidElements);
    }

    public OpenAiPaperUnderstanding(
            String title, String abstractSummary, List<String> authors, Integer year,
            String researchProblem, String targetUsersOrDomain, String method,
            String keyImplementation, String keyContribution, List<String> importantSystemComponents,
            String inputOutputRelationship, String whyItMatters, String possibleVisualMetaphor,
            List<String> forbiddenVisualElements, String likelyApplicationScenario,
            List<String> visualizableEntities, List<String> visualizableInteractions,
            String visualizableEnvironment, List<String> warnings, String confidenceLevel,
            String venue, String proposedSystemOrMethod, String visibleInterfaceOrDevice,
            String visibleInput, String visibleOutput, String expectedOutcome,
            List<String> mustShowElements, List<String> mustAvoidElements,
            String applicationEnvironment, String mainTaskOrWorkflow, String visibleInteraction
    ) {
        this(
                title, abstractSummary, authors, year, researchProblem, targetUsersOrDomain,
                method, keyImplementation, keyContribution, importantSystemComponents,
                inputOutputRelationship, whyItMatters, possibleVisualMetaphor, forbiddenVisualElements,
                likelyApplicationScenario, visualizableEntities, visualizableInteractions,
                visualizableEnvironment, warnings, confidenceLevel, venue, proposedSystemOrMethod,
                visibleInterfaceOrDevice, visibleInput, visibleOutput, expectedOutcome,
                mustShowElements, mustAvoidElements, applicationEnvironment, mainTaskOrWorkflow,
                visibleInteraction, null
        );
    }
    public OpenAiPaperUnderstanding(
            String title, String abstractSummary, List<String> authors, Integer year,
            String researchProblem, String targetUsersOrDomain, String method,
            String keyImplementation, String keyContribution, List<String> importantSystemComponents,
            String inputOutputRelationship, String whyItMatters, String possibleVisualMetaphor,
            List<String> forbiddenVisualElements, String likelyApplicationScenario,
            List<String> visualizableEntities, List<String> visualizableInteractions,
            String visualizableEnvironment, List<String> warnings, String confidenceLevel,
            String venue, String proposedSystemOrMethod, String visibleInterfaceOrDevice,
            String visibleInput, String visibleOutput, String expectedOutcome,
            List<String> mustShowElements, List<String> mustAvoidElements
    ) {
        this(
                title, abstractSummary, authors, year, researchProblem, targetUsersOrDomain,
                method, keyImplementation, keyContribution, importantSystemComponents,
                inputOutputRelationship, whyItMatters, possibleVisualMetaphor, forbiddenVisualElements,
                likelyApplicationScenario, visualizableEntities, visualizableInteractions,
                visualizableEnvironment, warnings, confidenceLevel, venue, proposedSystemOrMethod,
                visibleInterfaceOrDevice, visibleInput, visibleOutput, expectedOutcome, mustShowElements,
                mustAvoidElements, visualizableEnvironment, first(visualizableInteractions),
                first(visualizableInteractions), null
        );
    }

    public OpenAiPaperUnderstanding(
            String title,
            String abstractSummary,
            List<String> authors,
            Integer year,
            String researchProblem,
            String targetUsersOrDomain,
            String method,
            String keyImplementation,
            String keyContribution,
            List<String> importantSystemComponents,
            String inputOutputRelationship,
            String whyItMatters,
            String possibleVisualMetaphor,
            List<String> forbiddenVisualElements,
            String likelyApplicationScenario,
            List<String> visualizableEntities,
            List<String> visualizableInteractions,
            String visualizableEnvironment,
            List<String> warnings,
            String confidenceLevel
    ) {
        this(
                title, abstractSummary, authors, year, researchProblem, targetUsersOrDomain,
                method, keyImplementation, keyContribution, importantSystemComponents,
                inputOutputRelationship, whyItMatters, possibleVisualMetaphor, forbiddenVisualElements,
                likelyApplicationScenario, visualizableEntities, visualizableInteractions,
                visualizableEnvironment, warnings, confidenceLevel, null, null, null, null,
                null, null, List.of(), List.of()
        );
    }

    public OpenAiPaperUnderstanding(
            String title,
            String abstractSummary,
            List<String> authors,
            Integer year,
            String researchProblem,
            String targetUsersOrDomain,
            String method,
            String keyImplementation,
            String keyContribution,
            List<String> importantSystemComponents,
            String inputOutputRelationship,
            String whyItMatters,
            String possibleVisualMetaphor,
            List<String> forbiddenVisualElements,
            String confidenceLevel
    ) {
        this(
                title, abstractSummary, authors, year, researchProblem, targetUsersOrDomain,
                method, keyImplementation, keyContribution, importantSystemComponents,
                inputOutputRelationship, whyItMatters, possibleVisualMetaphor, forbiddenVisualElements,
                null, List.of(), List.of(), null, List.of(), confidenceLevel,
                null, null, null, null, null, null, List.of(), List.of()
        );
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
