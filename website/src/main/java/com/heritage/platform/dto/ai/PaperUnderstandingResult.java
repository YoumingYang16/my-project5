package com.heritage.platform.dto.ai;

import java.util.List;

public record PaperUnderstandingResult(
        String status,
        GrobidMetadata grobidMetadata,
        OpenAiPaperUnderstanding providerUnderstanding,
        FinalPaperUnderstanding finalUnderstanding,
        List<String> warnings,
        String understandingSource
) {
    public PaperUnderstandingResult(
            String status,
            GrobidMetadata grobidMetadata,
            OpenAiPaperUnderstanding providerUnderstanding,
            FinalPaperUnderstanding finalUnderstanding,
            List<String> warnings
    ) {
        this(status, grobidMetadata, providerUnderstanding, finalUnderstanding, warnings,
                providerUnderstanding == null ? "FAILED" : "OPENAI");
    }
}
