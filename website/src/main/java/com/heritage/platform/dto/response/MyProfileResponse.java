package com.heritage.platform.dto.response;

import com.heritage.platform.enums.UserRole;

public record MyProfileResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        UserRole role,
        String email,
        String phone,
        String bio
) {
}
