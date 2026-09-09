package com.heritage.platform.dto.ai;

import java.util.List;

public record SocialCopyFinalValidation(
        boolean valid,
        List<String> issues,
        int copyCharacterCount,
        boolean repairAttempted
) {
    public SocialCopyFinalValidation {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public SocialCopyFinalValidation withRepairAttempted(boolean attempted) {
        return new SocialCopyFinalValidation(valid, issues, copyCharacterCount, attempted);
    }
}
