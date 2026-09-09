package com.heritage.platform.dto.ai;

import java.util.List;

public record SocialCopySelectedHook(
        HookCandidate selectedHook,
        int selectedCandidateIndex,
        List<HookScore> scores,
        boolean hooksRewritten,
        String rationale
) {
    public SocialCopySelectedHook {
        scores = scores == null ? List.of() : List.copyOf(scores);
    }
}
