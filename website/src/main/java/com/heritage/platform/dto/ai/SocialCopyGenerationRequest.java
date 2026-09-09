package com.heritage.platform.dto.ai;

public record SocialCopyGenerationRequest(
        String language,
        String tone,
        String audience,
        String goal,
        String length,
        String callToAction,
        String mode
) {
    public SocialCopyGenerationRequest(String language, String tone) {
        this(language, tone, null, null, null, null, null);
    }

    public SocialCopyGenerationRequest(
            String language,
            String tone,
            String audience,
            String goal,
            String length,
            String callToAction
    ) {
        this(language, tone, audience, goal, length, callToAction, null);
    }
}
