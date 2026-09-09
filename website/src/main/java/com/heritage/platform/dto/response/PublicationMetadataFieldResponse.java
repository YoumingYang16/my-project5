package com.heritage.platform.dto.response;

public record PublicationMetadataFieldResponse(
        Object value,
        String source,
        String confidence,
        String warning
) {
}
