package com.heritage.platform.dto.ai;

import java.util.List;

public record StyledImagePrompt(
        String positivePrompt,
        String negativePrompt,
        List<String> styleKeywords,
        String compositionNotes,
        Integer recommendedWidth,
        Integer recommendedHeight
) {
}
