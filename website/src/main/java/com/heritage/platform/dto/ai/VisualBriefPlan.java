package com.heritage.platform.dto.ai;

import java.util.List;

public record VisualBriefPlan(
        String mainVisualSubject,
        String coreImplementationToShow,
        String readerTakeawayIn3Seconds,
        String sceneOrMetaphor,
        List<String> foregroundElements,
        List<String> backgroundElements,
        List<String> technicalElementsToHint,
        List<String> elementsToAvoid,
        String composition
) {
}
