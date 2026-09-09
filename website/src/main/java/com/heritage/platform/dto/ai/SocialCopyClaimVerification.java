package com.heritage.platform.dto.ai;

import java.util.List;

/** Verification result for a potentially strong claim in generated copy. */
public record SocialCopyClaimVerification(
        String claim,
        String verdict,
        List<String> evidenceSpanIds,
        String risk
) {
    public SocialCopyClaimVerification {
        evidenceSpanIds = evidenceSpanIds == null ? List.of() : List.copyOf(evidenceSpanIds);
    }
}
