package com.heritage.platform.dto.response;

import com.heritage.platform.enums.ContributorApplicationStatus;

import java.time.LocalDateTime;

public record MyContributorApplicationResponse(
        Long id,
        String applicationReason,
        String attachmentPath,
        ContributorApplicationStatus status,
        String reviewerName,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
}
