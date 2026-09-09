package com.heritage.platform.dto.ai;

import java.util.List;

public record CandidateManifest(
        int manifestVersion,
        String generatedFromProvider,
        String generatedFromUnderstandingHash,
        Long publicationId,
        String pdfHash,
        String generatedAt,
        String promptVersion,
        List<String> candidateImageUrls,
        List<AiCoverCandidate> candidates
) {
    public CandidateManifest {
        candidateImageUrls = candidateImageUrls == null ? List.of() : List.copyOf(candidateImageUrls);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
