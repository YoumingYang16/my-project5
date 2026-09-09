package com.heritage.platform.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RegisterRequest(
        @NotBlank(message = "Username cannot be empty.")
        @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters.")
        String username,

        @NotBlank(message = "Password cannot be empty.")
        @Size(min = 6, max = 30, message = "Password must be between 6 and 30 characters.")
        String password,

        @NotBlank(message = "Display name cannot be empty.")
        @Size(max = 50, message = "Display name cannot exceed 50 characters.")
        String nickname,

        @Email(message = "Please enter a valid email address.")
        @Size(max = 100, message = "Email cannot exceed 100 characters.")
        String email,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters.")
        String phone,

        @NotNull(message = "Security questions are required.")
        @Valid
        @Size(min = 3, max = 3, message = "Exactly 3 security questions are required.")
        List<SecurityQuestionRequest> securityQuestions
) {
}
