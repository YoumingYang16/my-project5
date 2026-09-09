package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordRecoveryQuestionLookupRequest(
        @NotBlank(message = "Username cannot be empty.")
        String username
) {
}
