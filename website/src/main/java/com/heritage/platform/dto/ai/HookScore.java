package com.heritage.platform.dto.ai;

public record HookScore(
        int candidateIndex,
        int relatability,
        int specificity,
        int attractiveness,
        int groundingInEvidence,
        int fitForXiaohongshu,
        int notAbstractLike,
        double averageScore,
        String reason
) {
}
