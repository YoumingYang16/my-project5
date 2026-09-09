package com.heritage.platform.dto.ai;

import java.util.List;

public record SocialCopyAngle(
        String painPoint,
        String openingHookIntent,
        String proposedSystem,
        String usageScenario,
        String howItWorks,
        String mostInterestingPoint,
        String whyValuable,
        List<String> targetReaders,
        List<String> topicAreas,
        List<String> hashtags,
        String confidenceLevel,
        List<String> warnings
) {
    public SocialCopyAngle {
        targetReaders = safe(targetReaders);
        topicAreas = safe(topicAreas);
        hashtags = safe(hashtags);
        warnings = safe(warnings);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
