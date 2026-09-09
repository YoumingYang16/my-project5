package com.heritage.platform.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QwenRuntimeApiKeyService {

    private final Map<String, String> userApiKeys = new ConcurrentHashMap<>();

    @Value("${qwen.image.api-key:}")
    private String serverApiKey = "";

    public void configureRuntimeApiKey(String value) {
        String username = requireCurrentUsername();
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() < 20 || cleaned.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Enter a valid Qwen API key (at least 20 characters, without spaces).");
        }
        userApiKeys.put(username, cleaned);
    }

    public void clearRuntimeApiKey() {
        userApiKeys.remove(requireCurrentUsername());
    }

    public String currentRequestApiKey() {
        String username = currentUsername();
        if (!username.isBlank()) {
            return userApiKeys.getOrDefault(username, "");
        }
        return serverApiKey == null ? "" : serverApiKey.trim();
    }

    public boolean isConfiguredForCurrentUser() {
        return !currentRequestApiKey().isBlank();
    }

    public String keySource() {
        String username = currentUsername();
        if (!username.isBlank()) {
            return userApiKeys.containsKey(username) ? "user-session" : "none";
        }
        return serverApiKey == null || serverApiKey.isBlank() ? "none" : "environment";
    }

    private String requireCurrentUsername() {
        String username = currentUsername();
        if (username.isBlank()) {
            throw new IllegalArgumentException("Sign in before configuring a Qwen API key.");
        }
        return username;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "";
        }
        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return "";
        }
        return username.trim();
    }
}