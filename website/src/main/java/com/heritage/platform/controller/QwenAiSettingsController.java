package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.service.ai.QwenRuntimeApiKeyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my/ai-settings/qwen")
public class QwenAiSettingsController {

    private final QwenRuntimeApiKeyService qwenApiKeyService;

    public QwenAiSettingsController(QwenRuntimeApiKeyService qwenApiKeyService) {
        this.qwenApiKeyService = qwenApiKeyService;
    }

    @GetMapping
    public ApiResponse<QwenKeyStatus> status() {
        return ApiResponse.success(currentStatus());
    }

    @PostMapping
    public ApiResponse<QwenKeyStatus> configure(@RequestBody QwenKeyRequest request) {
        try {
            qwenApiKeyService.configureRuntimeApiKey(request == null ? null : request.apiKey());
            return ApiResponse.success("Your Qwen API key is active for this server session.", currentStatus());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.failure(ex.getMessage());
        }
    }

    @DeleteMapping
    public ApiResponse<QwenKeyStatus> clear() {
        qwenApiKeyService.clearRuntimeApiKey();
        return ApiResponse.success("Your session-only Qwen API key was cleared.", currentStatus());
    }

    private QwenKeyStatus currentStatus() {
        String source = qwenApiKeyService.keySource();
        String message = switch (source) {
            case "user-session" -> "Your Qwen key is configured for this server session";
            case "environment" -> "Configured by the server environment";
            default -> "Your Qwen key is not configured";
        };
        return new QwenKeyStatus(qwenApiKeyService.isConfiguredForCurrentUser(), source, message);
    }

    public record QwenKeyRequest(String apiKey) {}

    public record QwenKeyStatus(boolean configured, String source, String message) {}
}