package com.heritage.platform.dto.ai;

import java.util.List;

public record CopyQualityReview(
        int hookAttractiveness,
        int groundingInPaperEvidence,
        int specificity,
        int positiveTone,
        int xiaohongshuReadability,
        int notAbstractLike,
        int structureCompleteness,
        int noHallucination,
        int noPlaceholderText,
        int noEllipses,
        int overallScore,
        List<String> problems,
        List<String> suggestedImprovements,
        boolean shouldRewrite
) {
    public CopyQualityReview {
        problems = problems == null ? List.of() : List.copyOf(problems);
        suggestedImprovements = suggestedImprovements == null ? List.of() : List.copyOf(suggestedImprovements);
    }

    public boolean requiresRewrite() {
        return shouldRewrite
                || hookAttractiveness < 4
                || groundingInPaperEvidence < 4
                || specificity < 4
                || positiveTone < 4
                || xiaohongshuReadability < 4
                || notAbstractLike < 4
                || structureCompleteness < 4
                || noHallucination < 4
                || noPlaceholderText < 4
                || noEllipses < 4
                || overallScore < 4;
    }
}
