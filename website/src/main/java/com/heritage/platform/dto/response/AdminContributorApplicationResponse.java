package com.heritage.platform.dto.response;

import com.heritage.platform.enums.ContributorApplicationStatus;
import com.heritage.platform.enums.UserRole;

import java.time.LocalDateTime;

public record AdminContributorApplicationResponse(
        Long id,
        Long applicantId,
        String applicantUsername,
        String applicantNickname,
        UserRole applicantRole,
        String applicationReason,
        String attachmentPath,
        ContributorApplicationStatus status,
        Long reviewerId,
        String reviewerName,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
}
