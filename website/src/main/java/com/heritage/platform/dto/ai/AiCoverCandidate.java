package com.heritage.platform.dto.ai;

import java.util.List;

public record AiCoverCandidate(
        String candidateId,
        String imageUrl,
        Long seed,
        boolean recommended,
        String prompt,
        CandidateSceneScore sceneScore,
        String recommendationReason,
        List<String> warnings,
        String socialCoverUrl,
        String sourceType
) {
    public AiCoverCandidate {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public AiCoverCandidate(
            String candidateId,
            String imageUrl,
            Long seed,
            boolean recommended,
            String prompt,
            CandidateSceneScore sceneScore,
            String recommendationReason,
            List<String> warnings
    ) {
        this(candidateId, imageUrl, seed, recommended, prompt, sceneScore,
                recommendationReason, warnings, null, null);
    }

    public AiCoverCandidate(
            String candidateId,
            String imageUrl,
            Long seed,
            boolean recommended,
            String prompt
    ) {
        this(candidateId, imageUrl, seed, recommended, prompt, null, null, List.of(), null, null);
    }

    public AiCoverCandidate withRanking(
            boolean isRecommended,
            CandidateSceneScore score,
            String reason,
            List<String> candidateWarnings
    ) {
        return new AiCoverCandidate(
                candidateId,
                imageUrl,
                seed,
                isRecommended,
                prompt,
                score,
                reason,
                candidateWarnings,
                socialCoverUrl,
                sourceType
        );
    }
}