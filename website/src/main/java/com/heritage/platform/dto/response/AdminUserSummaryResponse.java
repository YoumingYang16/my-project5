package com.heritage.platform.dto.response;

import com.heritage.platform.enums.UserRole;

import java.time.LocalDateTime;

public record AdminUserSummaryResponse(
        Long id,
        String username,
        String nickname,
        UserRole role,
        Boolean active,
        Long contributionCount,
        LocalDateTime updatedAt
) {
}
