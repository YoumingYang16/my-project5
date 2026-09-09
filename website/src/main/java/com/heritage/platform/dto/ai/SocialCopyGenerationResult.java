package com.heritage.platform.dto.ai;

import java.util.List;
import java.util.Map;

public record SocialCopyGenerationResult(
        Long publicationId,
        String language,
        String tone,
        String style,
        String source,
        String copyText,
        Map<String, String> variants,
        List<String> hashtags,
        List<String> warnings,
        String confidenceLevel,
        SocialCopyContentSpec contentSpec,
        SocialCopyContentPlan contentPlan,
        List<EvidenceSpan> evidenceSpans,
        List<SocialCopyCandidate> candidates,
        List<SocialCopyClaimVerification> claimChecks
) {
    public SocialCopyGenerationResult {
        variants = variants == null ? Map.of() : Map.copyOf(variants);
        hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        confidenceLevel = confidenceLevel == null || confidenceLevel.isBlank() ? "LOW" : confidenceLevel;
        evidenceSpans = evidenceSpans == null ? List.of() : List.copyOf(evidenceSpans);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        claimChecks = claimChecks == null ? List.of() : List.copyOf(claimChecks);
    }

    public SocialCopyGenerationResult(
            Long publicationId,
            String language,
            String tone,
            String style,
            String source,
            String copyText,
            Map<String, String> variants,
            List<String> hashtags,
            List<String> warnings
    ) {
        this(publicationId, language, tone, style, source, copyText, variants, hashtags, warnings,
                "EVIDENCE_FALLBACK".equalsIgnoreCase(source) ? "LOW" : "HIGH", null, null,
                List.of(), List.of(), List.of());
    }

    public SocialCopyGenerationResult(
            Long publicationId, String language, String tone, String style, String source, String copyText,
            Map<String, String> variants, List<String> hashtags, List<String> warnings, String confidenceLevel
    ) {
        this(publicationId, language, tone, style, source, copyText, variants, hashtags, warnings, confidenceLevel,
                null, null, List.of(), List.of(), List.of());
    }
}
