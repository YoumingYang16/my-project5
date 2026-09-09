package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank(message = "Collection name cannot be empty.")
        @Size(max = 100, message = "Collection name cannot exceed 100 characters.")
        String name,

        @Size(max = 255, message = "Collection description cannot exceed 255 characters.")
        String description
) {
}
