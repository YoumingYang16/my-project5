package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.request.AdminPostReviewRequest;
import com.heritage.platform.dto.response.AdminPostDetailResponse;
import com.heritage.platform.dto.response.AdminPostPageResult;
import com.heritage.platform.dto.response.AdminPostSummaryResponse;
import com.heritage.platform.enums.AdminPostSort;
import com.heritage.platform.enums.PostStatus;
import com.heritage.platform.service.AdminPostService;
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
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final AdminPostService adminPostService;

    public AdminPostController(AdminPostService adminPostService) {
        this.adminPostService = adminPostService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminPostSummaryResponse>>> listPosts(
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) AdminPostSort sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (page != null || size != null) {
            AdminPostPageResult result = adminPostService.listPostsPage(status, title, sort, page, size);
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

        return ResponseEntity.ok(ApiResponse.success(adminPostService.listPosts(status, title, sort)));
    }

    @GetMapping("/{postId}")
    public ApiResponse<AdminPostDetailResponse> getDetail(@PathVariable Long postId) {
        return ApiResponse.success(adminPostService.getDetail(postId));
    }

    @PostMapping("/{postId}/review")
    public ApiResponse<AdminPostDetailResponse> review(
            @PathVariable Long postId,
            @Valid @RequestBody AdminPostReviewRequest request
    ) {
        return ApiResponse.success("Review action completed successfully.", adminPostService.review(postId, request));
    }

    @PostMapping("/{postId}/archive")
    public ApiResponse<AdminPostDetailResponse> archive(@PathVariable Long postId) {
        return ApiResponse.success("Publication archived successfully.", adminPostService.archive(postId));
    }

    @PostMapping("/{postId}/restore")
    public ApiResponse<AdminPostDetailResponse> restore(@PathVariable Long postId) {
        return ApiResponse.success("Publication restored successfully.", adminPostService.restore(postId));
    }
}

