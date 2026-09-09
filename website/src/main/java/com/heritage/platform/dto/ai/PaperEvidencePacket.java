package com.heritage.platform.dto.ai;

import java.util.List;

public record PaperEvidencePacket(
        String title,
        String abstractText,
        List<String> authors,
        Integer year,
        String venue,
        String doi,
        List<String> keywords,
        List<String> problemContextSnippets,
        List<String> methodDesignSnippets,
        List<String> implementationSnippets,
        List<String> evaluationResultSnippets,
        List<String> conclusionSnippets,
        List<String> figureCaptions,
        List<String> tableCaptions,
        List<String> domainTerms,
        List<String> applicationClues,
        List<String> visualClues,
        String compactEvidenceText,
        int characterCount,
        List<String> warnings,
        String publisher,
        String canonicalUrl,
        List<String> subjectTerms,
        String citationMetadata,
        List<String> evidenceSources,
        List<EvidenceSpan> evidenceSpans
) {
    public PaperEvidencePacket {
        authors = safe(authors);
        keywords = safe(keywords);
        problemContextSnippets = safe(problemContextSnippets);
        methodDesignSnippets = safe(methodDesignSnippets);
        implementationSnippets = safe(implementationSnippets);
        evaluationResultSnippets = safe(evaluationResultSnippets);
        conclusionSnippets = safe(conclusionSnippets);
        figureCaptions = safe(figureCaptions);
        tableCaptions = safe(tableCaptions);
        domainTerms = safe(domainTerms);
        applicationClues = safe(applicationClues);
        visualClues = safe(visualClues);
        compactEvidenceText = compactEvidenceText == null ? "" : compactEvidenceText;
        characterCount = compactEvidenceText.length();
        warnings = safe(warnings);
        subjectTerms = safe(subjectTerms);
        evidenceSources = safe(evidenceSources);
        evidenceSpans = evidenceSpans == null ? List.of() : List.copyOf(evidenceSpans);
    }

    public PaperEvidencePacket(
            String title,
            String abstractText,
            List<String> authors,
            Integer year,
            String venue,
            String doi,
            List<String> keywords,
            List<String> problemContextSnippets,
            List<String> methodDesignSnippets,
            List<String> implementationSnippets,
            List<String> evaluationResultSnippets,
            List<String> conclusionSnippets,
            List<String> figureCaptions,
            List<String> tableCaptions,
            List<String> domainTerms,
            List<String> applicationClues,
            List<String> visualClues,
            String compactEvidenceText,
            int characterCount,
            List<String> warnings
    ) {
        this(
                title, abstractText, authors, year, venue, doi, keywords,
                problemContextSnippets, methodDesignSnippets, implementationSnippets,
                evaluationResultSnippets, conclusionSnippets, figureCaptions, tableCaptions,
                domainTerms, applicationClues, visualClues, compactEvidenceText, characterCount,
                warnings, null, null, List.of(), null, List.of(), List.of()
        );
    }

    public PaperEvidencePacket(
            String title, String abstractText, List<String> authors, Integer year, String venue, String doi,
            List<String> keywords, List<String> problemContextSnippets, List<String> methodDesignSnippets,
            List<String> implementationSnippets, List<String> evaluationResultSnippets, List<String> conclusionSnippets,
            List<String> figureCaptions, List<String> tableCaptions, List<String> domainTerms,
            List<String> applicationClues, List<String> visualClues, String compactEvidenceText, int characterCount,
            List<String> warnings, String publisher, String canonicalUrl, List<String> subjectTerms,
            String citationMetadata, List<String> evidenceSources
    ) {
        this(title, abstractText, authors, year, venue, doi, keywords, problemContextSnippets, methodDesignSnippets,
                implementationSnippets, evaluationResultSnippets, conclusionSnippets, figureCaptions, tableCaptions,
                domainTerms, applicationClues, visualClues, compactEvidenceText, characterCount, warnings, publisher,
                canonicalUrl, subjectTerms, citationMetadata, evidenceSources, List.of());
    }

    public PaperEvidencePacket withEvidenceSpans(List<EvidenceSpan> spans) {
        return new PaperEvidencePacket(title, abstractText, authors, year, venue, doi, keywords,
                problemContextSnippets, methodDesignSnippets, implementationSnippets, evaluationResultSnippets,
                conclusionSnippets, figureCaptions, tableCaptions, domainTerms, applicationClues, visualClues,
                compactEvidenceText, characterCount, warnings, publisher, canonicalUrl, subjectTerms,
                citationMetadata, evidenceSources, spans);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
