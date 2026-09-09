package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.service.ai.UserAiProviderSettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/my/ai-settings/providers")
public class UserAiProviderSettingsController {
    private final UserAiProviderSettingsService settingsService;

    public UserAiProviderSettingsController(UserAiProviderSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<ProviderSettingsStatus> status() {
        return ApiResponse.success(toStatus(settingsService.current()));
    }

    @PostMapping("/text")
    public ApiResponse<ProviderSettingsStatus> configureText(@RequestBody ProviderRequest request) {
        try {
            return ApiResponse.success("Text AI provider saved securely for your account.", toStatus(
                    settingsService.configureText(request.provider(), request.apiKey(), request.model())));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.failure(ex.getMessage());
        }
    }

    @PostMapping("/image")
    public ApiResponse<ProviderSettingsStatus> configureImage(@RequestBody ProviderRequest request) {
        try {
            return ApiResponse.success("Image AI provider saved securely for your account.", toStatus(
                    settingsService.configureImage(request.provider(), request.apiKey(), request.model())));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.failure(ex.getMessage());
        }
    }

    @DeleteMapping
    public ApiResponse<ProviderSettingsStatus> clear(@RequestParam String type) {
        return ApiResponse.success("Provider key cleared.", toStatus(
                "image".equalsIgnoreCase(type) ? settingsService.clearImage() : settingsService.clearText()));
    }

    private ProviderSettingsStatus toStatus(UserAiProviderSettingsService.UserSettings value) {
        return new ProviderSettingsStatus(
                value.textProvider(), value.textModel(), !value.textApiKey().isBlank(),
                value.imageProvider(), value.imageModel(),
                "comfyui".equals(value.imageProvider()) || !value.imageApiKey().isBlank(),
                settingsService.platformAiEnabled());
    }

    public record ProviderRequest(String provider, String apiKey, String model) {}
    public record ProviderSettingsStatus(
            String textProvider, String textModel, boolean textConfigured,
            String imageProvider, String imageModel, boolean imageConfigured,
            boolean platformManaged) {}
}
