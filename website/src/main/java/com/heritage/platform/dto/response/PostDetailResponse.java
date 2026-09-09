package com.heritage.platform.dto.response;

import com.heritage.platform.enums.PostStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long id,
        String title,
        String content,
        String coverImageUrl,
        Boolean publication,
        String publicationAuthors,
        Integer publicationYear,
        String venue,
        String abstractText,
        String keywords,
        String doi,
        String bibtex,
        String researchArea,
        String pdfUrl,
        String codeUrl,
        String datasetUrl,
        String participantId,
        String heritageName,
        String region,
        PostStatus status,
        String authorName,
        Long authorId,
        Long categoryId,
        String categoryName,
        Integer likeCount,
        Integer favoriteCount,
        Integer commentCount,
        Integer viewCount,
        Boolean likedByCurrentUser,
        List<String> imageUrls,
        List<CommentResponse> comments,
        LocalDateTime createdAt
) {
}
