package com.heritage.platform.dto.ai;

public record SocialCopyProviderAttempt(
        String provider,
        boolean enabled,
        boolean attempted,
        boolean success,
        String failureReason,
        Integer statusCode,
        boolean rateLimited,
        long durationMs,
        String safeMessage
) {
}
