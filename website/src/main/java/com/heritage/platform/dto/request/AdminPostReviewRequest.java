package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPostReviewRequest(
        @NotBlank(message = "Review action cannot be empty.")
        String action,

        @Size(max = 255, message = "Rejection reason cannot exceed 255 characters.")
        String reason
) {
}
