package com.heritage.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(
        @NotBlank(message = "Comment content cannot be empty.")
        String content
) {
}
