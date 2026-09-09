package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateContributorApplicationRequest(
        @NotBlank(message = "Application reason cannot be empty.")
        String applicationReason
) {
}
