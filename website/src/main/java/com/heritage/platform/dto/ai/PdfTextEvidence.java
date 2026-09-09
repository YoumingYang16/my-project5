package com.heritage.platform.dto.ai;

import java.util.List;

public record PdfTextEvidence(
        String firstPageText,
        String documentText,
        boolean available,
        List<String> warnings,
        List<PdfPageEvidence> pages,
        int pageCount,
        int extractedPageCount
) {
    public PdfTextEvidence {
        firstPageText = firstPageText == null ? "" : firstPageText;
        documentText = documentText == null ? "" : documentText;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        pages = pages == null ? List.of() : List.copyOf(pages);
        pageCount = Math.max(0, pageCount);
        extractedPageCount = Math.max(0, extractedPageCount);
    }

    public PdfTextEvidence(String firstPageText, String documentText, boolean available, List<String> warnings) {
        this(firstPageText, documentText, available, warnings, List.of(), 0, 0);
    }

    public PdfTextEvidence(
            String firstPageText,
            String documentText,
            boolean available,
            List<String> warnings,
            List<PdfPageEvidence> pages
    ) {
        this(firstPageText, documentText, available, warnings, pages, pages == null ? 0 : pages.size(),
                pages == null ? 0 : pages.size());
    }

    public static PdfTextEvidence unavailable(String warning) {
        return new PdfTextEvidence("", "", false, warning == null ? List.of() : List.of(warning), List.of(), 0, 0);
    }

    public boolean fullPageCoverage() {
        return pageCount > 0 && extractedPageCount >= pageCount;
    }
}
