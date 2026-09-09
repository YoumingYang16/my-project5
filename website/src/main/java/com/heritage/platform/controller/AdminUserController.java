package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.request.AdminUserRoleUpdateRequest;
import com.heritage.platform.dto.response.AdminUserPageResult;
import com.heritage.platform.dto.response.AdminUserSummaryResponse;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserSummaryResponse>>> listUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (page != null || size != null) {
            AdminUserPageResult result = adminUserService.listUsersPage(username, role, active, page, size);
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Page", String.valueOf(result.page()));
            headers.add("X-Size", String.valueOf(result.size()));
            headers.add("X-Total-Elements", String.valueOf(result.totalElements()));
            headers.add("X-Total-Pages", String.valueOf(result.totalPages()));
            headers.add("X-Has-Previous", String.valueOf(result.hasPrevious()));
            headers.add("X-Has-Next", String.valueOf(result.hasNext()));
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(ApiResponse.success(result.items()));
        }

        return ResponseEntity.ok(ApiResponse.success(adminUserService.listUsers(username, role, active)));
    }

    @PostMapping("/{userId}/role")
    public ApiResponse<AdminUserSummaryResponse> updateRole(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserRoleUpdateRequest request
    ) {
        return ApiResponse.success("User role updated successfully.", adminUserService.updateRole(userId, request));
    }

    @PostMapping("/{userId}/activate")
    public ApiResponse<AdminUserSummaryResponse> activate(@PathVariable Long userId) {
        return ApiResponse.success("User activated successfully.", adminUserService.activate(userId));
    }

    @PostMapping("/{userId}/deactivate")
    public ApiResponse<AdminUserSummaryResponse> deactivate(@PathVariable Long userId) {
        return ApiResponse.success("User deactivated successfully.", adminUserService.deactivate(userId));
    }
}
