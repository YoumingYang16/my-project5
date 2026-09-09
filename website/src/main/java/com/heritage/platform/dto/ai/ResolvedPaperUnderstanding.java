package com.heritage.platform.dto.ai;

import java.util.List;

public record ResolvedPaperUnderstanding(
        String pdfHash,
        PaperEvidencePacket evidencePacket,
        ReferenceGroundingContext grounding,
        PaperUnderstandingResult paperUnderstanding,
        boolean cacheHit,
        String source,
        String openAiFailureMessage,
        List<String> warnings
) {
    public ResolvedPaperUnderstanding {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
