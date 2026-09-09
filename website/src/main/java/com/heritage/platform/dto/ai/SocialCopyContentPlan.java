package com.heritage.platform.dto.ai;

import java.util.List;

/** The evidence-bound editorial plan created before copy generation. */
public record SocialCopyContentPlan(
        String audienceGuidance,
        String goalGuidance,
        List<String> mustCover,
        List<String> avoidClaims,
        String callToAction
) {
    public SocialCopyContentPlan {
        mustCover = mustCover == null ? List.of() : List.copyOf(mustCover);
        avoidClaims = avoidClaims == null ? List.of() : List.copyOf(avoidClaims);
    }
}
