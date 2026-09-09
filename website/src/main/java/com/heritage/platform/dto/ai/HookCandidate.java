package com.heritage.platform.dto.ai;

public record HookCandidate(
        String hookText,
        String hookType,
        String whyItWorks,
        String groundingEvidence
) {
}
