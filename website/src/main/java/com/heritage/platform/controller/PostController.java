package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.request.CommentCreateRequest;
import com.heritage.platform.dto.request.PostCreateRequest;
import com.heritage.platform.dto.request.PostUpdateRequest;
import com.heritage.platform.dto.response.CommentResponse;
import com.heritage.platform.dto.response.PostDetailResponse;
import com.heritage.platform.dto.response.PostSummaryResponse;
import com.heritage.platform.service.PostService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ApiResponse<List<PostSummaryResponse>> listAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.success(postService.listAll(keyword, category));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getDetail(@PathVariable Long postId) {
        return ApiResponse.success(postService.getDetail(postId));
    }

    @PostMapping
    public ApiResponse<PostDetailResponse> create(@Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.success("Draft created successfully.", postService.create(request));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostDetailResponse> update(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        return ApiResponse.success("Publication updated successfully.", postService.update(postId, request));
    }

    @PostMapping("/{postId}/submit-review")
    public ApiResponse<PostDetailResponse> submitForReview(@PathVariable Long postId) {
        return ApiResponse.success("Publication submitted for review successfully.", postService.submitForReview(postId));
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<PostDetailResponse> likePost(@PathVariable Long postId) {
        return ApiResponse.success("Publication liked successfully.", postService.likePost(postId));
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<CommentResponse> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return ApiResponse.success("Comment posted successfully.", postService.addComment(postId, request));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        postService.deleteComment(postId, commentId);
        return ApiResponse.success("Comment deleted successfully.", null);
    }
}
