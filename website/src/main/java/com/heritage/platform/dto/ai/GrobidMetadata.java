package com.heritage.platform.dto.ai;

import java.util.List;

public record GrobidMetadata(
        String title,
        String abstractText,
        List<String> authors,
        Integer year,
        String venue,
        String doi,
        List<String> keywords,
        String source,
        boolean available,
        List<String> warnings
) {
    public GrobidMetadata {
        authors = authors == null ? List.of() : List.copyOf(authors);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public GrobidMetadata(
            String title,
            String abstractText,
            List<String> authors,
            Integer year,
            String source,
            boolean available,
            List<String> warnings
    ) {
        this(title, abstractText, authors, year, null, null, List.of(), source, available, warnings);
    }

    public static GrobidMetadata unavailable(String warning) {
        return new GrobidMetadata(
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                "GROBID",
                false,
                warning == null || warning.isBlank() ? List.of() : List.of(warning)
        );
    }
}
