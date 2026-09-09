package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MyPasswordChangeRequest(
        @NotBlank(message = "Password cannot be empty.")
        @Size(min = 6, max = 30, message = "Password must be between 6 and 30 characters.")
        String newPassword,

        @NotNull(message = "Security answers are required.")
        @Size(min = 3, max = 3, message = "Exactly 3 security answers are required.")
        List<@NotBlank(message = "Security answer cannot be empty.") String> answers
) {
}
