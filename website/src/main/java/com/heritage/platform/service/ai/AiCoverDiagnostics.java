package com.heritage.platform.service.ai;

import java.util.regex.Pattern;

final class AiCoverDiagnostics {

    private static final Pattern API_KEY = Pattern.compile("(?i)\\bsk-[a-z0-9_-]{8,}\\b");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_URL = Pattern.compile("data:[^;\\s]+;base64,[a-zA-Z0-9+/=]+", Pattern.CASE_INSENSITIVE);
    private static final int MAX_MESSAGE_LENGTH = 320;

    private AiCoverDiagnostics() {
    }

    static String safeExceptionSummary(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = sanitize(root.getMessage());
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = API_KEY.matcher(value).replaceAll("[redacted-api-key]");
        cleaned = BEARER.matcher(cleaned).replaceAll("Bearer [redacted]");
        cleaned = DATA_URL.matcher(cleaned).replaceAll("[redacted-data-url]");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_MESSAGE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_MESSAGE_LENGTH).trim() + "...";
        }
        return cleaned.isEmpty() ? null : cleaned;
    }
}
