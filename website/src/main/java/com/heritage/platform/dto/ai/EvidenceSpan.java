package com.heritage.platform.dto.ai;

/** A small, independently citeable piece of paper evidence. */
public record EvidenceSpan(
        String id,
        String source,
        Integer pageNumber,
        String section,
        String text,
        Double extractionConfidence
) {
}
