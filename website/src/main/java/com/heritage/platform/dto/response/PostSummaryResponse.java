package com.heritage.platform.dto.response;

import com.heritage.platform.enums.PostStatus;

import java.time.LocalDateTime;

public record PostSummaryResponse(
        Long id,
        String title,
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
        String heritageName,
        String region,
        PostStatus status,
        String authorName,
        Long categoryId,
        String categoryName,
        Integer likeCount,
        Integer favoriteCount,
        Integer commentCount,
        Integer viewCount,
        LocalDateTime createdAt
) {
}
