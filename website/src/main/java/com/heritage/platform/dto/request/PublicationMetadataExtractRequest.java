package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PublicationMetadataExtractRequest(
        @NotBlank(message = "PDF URL is required.")
        String pdfUrl
) {
}
