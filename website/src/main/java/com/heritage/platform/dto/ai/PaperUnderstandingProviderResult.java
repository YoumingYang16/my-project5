package com.heritage.platform.dto.ai;

import java.util.List;

public record PaperUnderstandingProviderResult(
        OpenAiPaperUnderstanding understanding,
        String sourceProvider,
        String modelName,
        String openAiFailureMessage,
        List<String> warnings
) {
    public PaperUnderstandingProviderResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
