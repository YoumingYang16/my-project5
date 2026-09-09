package com.heritage.platform.dto.response;

public record PublicationFigureCandidateResponse(
        String imageUrl,
        Integer pageNumber,
        String figureNumber,
        String caption,
        Integer width,
        Integer height,
        double score,
        String reason,
        boolean recommended
) {
}
