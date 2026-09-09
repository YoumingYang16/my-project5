package com.heritage.platform.dto.response;

import java.util.List;

public record AdminPostPageResult(
        List<AdminPostSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
}
