package com.heritage.platform.dto.ai;

public record AiCoverSelectionResponse(
        Long publicationId,
        String coverImageUrl,
        String message
) {
}
