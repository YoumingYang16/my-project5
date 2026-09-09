package com.heritage.platform.dto.ai;

import java.util.List;

public record AiCoverGenerationResult(
        Long publicationId,
        String status,
        PaperUnderstandingResult paperUnderstanding,
        ReferenceGroundingContext referenceGrounding,
        ApplicationSceneBrief applicationSceneBrief,
        ScenarioImagePrompt scenarioImagePrompt,
        VisualBriefPlan visualBriefPlan,
        StyledImagePrompt styledPrompt,
        PromptCritiqueResult critique,
        List<AiCoverCandidate> candidates,
        List<String> warnings,
        String message
) {
    public static AiCoverGenerationResult failed(Long publicationId, String message, List<String> warnings) {
        return new AiCoverGenerationResult(
                publicationId,
                "FAILED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                warnings == null ? List.of() : warnings,
                message
        );
    }
}
