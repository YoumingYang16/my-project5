package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.service.ai.DeepSeekStructuredResponseClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/my/ai-settings/deepseek", "/api/admin/ai-settings/deepseek"})
public class AdminAiSettingsController {

    private final DeepSeekStructuredResponseClient deepSeekClient;

    public AdminAiSettingsController(DeepSeekStructuredResponseClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    @GetMapping
    public ApiResponse<DeepSeekKeyStatus> status() {
        return ApiResponse.success(currentStatus());
    }

    @PostMapping
    public ApiResponse<DeepSeekKeyStatus> configure(@RequestBody DeepSeekKeyRequest request) {
        try {
            deepSeekClient.configureRuntimeApiKey(request == null ? null : request.apiKey());
            return ApiResponse.success("Your DeepSeek API key is active for this server session.", currentStatus());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.failure(ex.getMessage());
        }
    }

    @DeleteMapping
    public ApiResponse<DeepSeekKeyStatus> clear() {
        deepSeekClient.clearRuntimeApiKey();
        return ApiResponse.success("Your session-only DeepSeek API key was cleared.", currentStatus());
    }

    private DeepSeekKeyStatus currentStatus() {
        String source = deepSeekClient.keySource();
        String message = switch (source) {
            case "user-session" -> "Your key is configured for this server session";
            case "environment" -> "Configured by the server environment";
            default -> "Your DeepSeek key is not configured";
        };
        return new DeepSeekKeyStatus(deepSeekClient.isEnabled(), source, message);
    }

    public record DeepSeekKeyRequest(String apiKey) {}

    public record DeepSeekKeyStatus(boolean configured, String source, String message) {}
}