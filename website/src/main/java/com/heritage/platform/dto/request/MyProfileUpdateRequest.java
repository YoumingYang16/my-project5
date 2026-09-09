package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MyProfileUpdateRequest(
        @NotBlank(message = "Display name cannot be empty.")
        @Size(max = 50, message = "Display name cannot exceed 50 characters.")
        String nickname,

        @Size(max = 255, message = "Avatar URL cannot exceed 255 characters.")
        String avatarUrl,

        String bio
) {
}
