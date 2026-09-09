package com.heritage.platform.dto.ai;

import java.util.List;

public record PaperUnderstandingCacheEntry(
        int cacheVersion,
        String pdfHash,
        PaperEvidencePacket evidencePacket,
        PaperUnderstandingResult paperUnderstanding,
        ReferenceGroundingContext grounding,
        String understandingSource,
        String understandingUpdatedAt,
        String modelName,
        List<String> warnings
) {
    public PaperUnderstandingCacheEntry {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
