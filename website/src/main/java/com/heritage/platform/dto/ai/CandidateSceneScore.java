package com.heritage.platform.dto.ai;

import java.util.List;

public record CandidateSceneScore(
        Integer sceneRealismScore,
        Integer taskAccuracyScore,
        Integer technologyRelevanceScore,
        Integer environmentAccuracyScore,
        Integer visualClarityScore,
        Integer coverSuitabilityScore,
        Integer nonGenericScore,
        Integer totalScore,
        List<String> problems,
        String reason
) {
}
