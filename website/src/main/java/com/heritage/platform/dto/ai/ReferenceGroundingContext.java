package com.heritage.platform.dto.ai;

import java.util.List;

public record ReferenceGroundingContext(
        List<String> figureCaptions,
        List<String> tableCaptions,
        List<String> sectionHints,
        List<String> interfaceOrSystemMentions,
        List<String> environmentMentions,
        List<String> taskMentions,
        List<String> deviceMentions,
        List<String> userGroupMentions,
        List<String> extractedVisualClues,
        List<String> groundingWarnings
) {
    public static ReferenceGroundingContext empty(String warning) {
        return new ReferenceGroundingContext(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                warning == null || warning.isBlank() ? List.of() : List.of(warning)
        );
    }
}
