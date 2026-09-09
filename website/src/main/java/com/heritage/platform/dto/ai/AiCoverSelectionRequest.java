package com.heritage.platform.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record AiCoverSelectionRequest(
        @NotBlank(message = "Candidate ID is required.")
        String candidateId,

        @NotBlank(message = "Candidate image URL is required.")
        String imageUrl
) {
}
