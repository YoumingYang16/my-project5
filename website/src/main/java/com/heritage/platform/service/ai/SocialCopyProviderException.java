package com.heritage.platform.service.ai;

public class SocialCopyProviderException extends AiCoverWorkflowException {

    private final String provider;
    private final SocialCopyFailureReason failureReason;
    private final Integer statusCode;
    private final Long retryAfterSeconds;

    public SocialCopyProviderException(
            String provider,
            SocialCopyFailureReason failureReason,
            Integer statusCode,
            Long retryAfterSeconds,
            String message
    ) {
        super(message);
        this.provider = provider;
        this.failureReason = failureReason;
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public SocialCopyProviderException(
            String provider,
            SocialCopyFailureReason failureReason,
            Integer statusCode,
            Long retryAfterSeconds,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.provider = provider;
        this.failureReason = failureReason;
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String provider() {
        return provider;
    }

    public SocialCopyFailureReason failureReason() {
        return failureReason;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean rateLimited() {
        return failureReason == SocialCopyFailureReason.RATE_LIMITED || Integer.valueOf(429).equals(statusCode);
    }

    public boolean retryable() {
        return failureReason == SocialCopyFailureReason.RATE_LIMITED
                || failureReason == SocialCopyFailureReason.TIMEOUT
                || failureReason == SocialCopyFailureReason.TEMPORARY_ERROR
                || failureReason == SocialCopyFailureReason.INVALID_RESPONSE
                || failureReason == SocialCopyFailureReason.JSON_PARSE_FAILURE;
    }
}
