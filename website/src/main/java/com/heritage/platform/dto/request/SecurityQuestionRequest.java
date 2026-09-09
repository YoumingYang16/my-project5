package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SecurityQuestionRequest(
        @NotBlank(message = "Security question cannot be empty.")
        @Size(max = 255, message = "Security question cannot exceed 255 characters.")
        String questionText,

        @NotBlank(message = "Security answer cannot be empty.")
        @Size(max = 255, message = "Security answer cannot exceed 255 characters.")
        String answer
) {
}
