package com.heritage.platform.dto.ai;

/** Text recovered from one PDF page, retained to support page-level citations. */
public record PdfPageEvidence(int pageNumber, String text, double extractionConfidence) {
    public PdfPageEvidence {
        text = text == null ? "" : text;
    }
}
