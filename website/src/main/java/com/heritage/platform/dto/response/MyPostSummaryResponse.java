package com.heritage.platform.dto.response;

import com.heritage.platform.enums.PostStatus;

import java.time.LocalDateTime;

public record MyPostSummaryResponse(
        Long id,
        String participantId,
        String title,
        String coverImageUrl,
        String heritageName,
        String region,
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
        PostStatus status,
        String categoryName,
        String rejectReason,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        LocalDateTime createdAt
) {
}
