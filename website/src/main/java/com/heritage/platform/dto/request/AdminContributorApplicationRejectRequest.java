package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminContributorApplicationRejectRequest(
        @NotBlank(message = "Please provide a rejection reason.")
        String reason
) {
}
