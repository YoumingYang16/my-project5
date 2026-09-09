package com.heritage.platform.dto.request;

import com.heritage.platform.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(
        @NotNull(message = "Please choose a target role.")
        UserRole role
) {
}
