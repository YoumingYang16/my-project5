package com.heritage.platform.dto.ai;

import java.util.List;

public record ScenarioImagePrompt(
        String positivePrompt,
        String negativePrompt,
        String shortCandidateExplanation,
        String promptStrategy,
        List<String> groundingFactsUsed,
        List<String> realismConstraints,
        List<String> warnings
) {
}
