package com.heritage.platform.dto.ai;

import java.util.List;

public record SocialCopyEvidenceDigest(
        String paperTitle,
        String venueAndYear,
        String domain,
        String problemEvidence,
        String proposedSystemEvidence,
        String usageEvidence,
        String valueEvidence,
        String targetAudienceEvidence,
        List<String> keywords,
        List<String> hashtagCandidates,
        List<String> evidenceSources,
        String confidenceLevel,
        List<String> warnings
) {
    public SocialCopyEvidenceDigest {
        keywords = safe(keywords);
        hashtagCandidates = safe(hashtagCandidates);
        evidenceSources = safe(evidenceSources);
        warnings = safe(warnings);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
