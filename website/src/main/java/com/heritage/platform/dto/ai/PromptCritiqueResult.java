package com.heritage.platform.dto.ai;

import java.util.List;

public record PromptCritiqueResult(
        boolean goodEnough,
        List<String> problems,
        String revisedPositivePrompt,
        String revisedNegativePrompt
) {
}
