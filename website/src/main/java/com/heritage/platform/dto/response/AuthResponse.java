package com.heritage.platform.dto.response;

import com.heritage.platform.enums.UserRole;

public record AuthResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        UserRole role,
        String token,
        String email,
        String phone
) {
}
