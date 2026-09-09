package com.heritage.platform.dto.ai;

import java.util.List;

public record DoiEnrichmentResult(
        GrobidMetadata metadata,
        String publisher,
        String canonicalUrl,
        List<String> subjectTerms,
        String citationMetadata,
        List<String> evidenceSources,
        List<String> warnings,
        boolean doiFound,
        boolean lookupAttempted,
        boolean lookupSucceeded,
        List<String> fieldsAdded
) {
    public DoiEnrichmentResult {
        subjectTerms = subjectTerms == null ? List.of() : List.copyOf(subjectTerms);
        evidenceSources = evidenceSources == null ? List.of() : List.copyOf(evidenceSources);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        fieldsAdded = fieldsAdded == null ? List.of() : List.copyOf(fieldsAdded);
    }

    public static DoiEnrichmentResult empty(GrobidMetadata metadata) {
        return new DoiEnrichmentResult(
                metadata, null, null, List.of(), null, List.of(), List.of(),
                false, false, false, List.of()
        );
    }
}
