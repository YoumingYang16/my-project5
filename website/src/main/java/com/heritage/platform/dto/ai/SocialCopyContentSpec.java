package com.heritage.platform.dto.ai;

/**
 * Explicit user controls for a Xiaohongshu paper post.  Keeping these values
 * structured avoids treating audience and campaign intent as incidental prompt
 * text.
 */
public record SocialCopyContentSpec(
        String audience,
        String goal,
        String language,
        String tone,
        String length,
        String callToAction,
        String platform,
        String mode
) {
    public static final String XIAOHONGSHU = "xiaohongshu";

    public SocialCopyContentSpec(
            String audience,
            String goal,
            String language,
            String tone,
            String length,
            String callToAction,
            String platform
    ) {
        this(audience, goal, language, tone, length, callToAction, platform, "personal");
    }
}
