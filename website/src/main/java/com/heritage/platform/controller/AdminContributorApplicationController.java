package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.request.AdminContributorApplicationRejectRequest;
import com.heritage.platform.dto.response.AdminContributorApplicationResponse;
import com.heritage.platform.enums.ContributorApplicationStatus;
import jakarta.validation.Valid;
import com.heritage.platform.service.ContributorApplicationService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/contributor-applications")
public class AdminContributorApplicationController {

    private final ContributorApplicationService contributorApplicationService;

    public AdminContributorApplicationController(ContributorApplicationService contributorApplicationService) {
        this.contributorApplicationService = contributorApplicationService;
    }

    @GetMapping
    public ApiResponse<List<AdminContributorApplicationResponse>> listApplications(
            @RequestParam(required = false) ContributorApplicationStatus status,
            @RequestParam(required = false, defaultValue = "created_at_desc") String sortBy
    ) {
        return ApiResponse.success(contributorApplicationService.listAdminApplications(status, sortBy));
    }

    @PostMapping("/{applicationId}/approve")
    public ApiResponse<AdminContributorApplicationResponse> approve(@PathVariable Long applicationId) {
        return ApiResponse.success("Contributor application approved.", contributorApplicationService.approve(applicationId));
    }

    @PostMapping("/{applicationId}/reject")
    public ApiResponse<AdminContributorApplicationResponse> reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody AdminContributorApplicationRejectRequest request
    ) {
        return ApiResponse.success("Contributor application rejected.", contributorApplicationService.reject(applicationId, request));
    }
}
