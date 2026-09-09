package com.heritage.platform.service.ai;

public enum SocialCopyFailureReason {
    DISABLED,
    INSUFFICIENT_CREDIT,
    RATE_LIMITED,
    TIMEOUT,
    TEMPORARY_ERROR,
    AUTHENTICATION,
    INVALID_RESPONSE,
    JSON_PARSE_FAILURE,
    CONFIGURATION,
    UNKNOWN
}
