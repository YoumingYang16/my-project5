package com.heritage.platform.dto.ai;

import java.util.List;

/** A selectable copy candidate with transparent quality and evidence signals. */
public record SocialCopyCandidate(
        String id,
        String style,
        String copyText,
        double factualityScore,
        double audienceFitScore,
        double goalFitScore,
        double xiaohongshuFitScore,
        boolean recommended,
        List<SocialCopyClaimVerification> claimChecks
) {
    public SocialCopyCandidate {
        claimChecks = claimChecks == null ? List.of() : List.copyOf(claimChecks);
    }
}
