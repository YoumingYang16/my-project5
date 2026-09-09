package com.heritage.platform.dto.response;

import com.heritage.platform.enums.PostStatus;

import java.time.LocalDateTime;

public record AdminPostSummaryResponse(
        Long id,
        String participantId,
        String title,
        PostStatus status,
        String authorName,
        String categoryName,
        String rejectReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String reviewerName,
        LocalDateTime updatedAt,
        LocalDateTime createdAt
) {
}
