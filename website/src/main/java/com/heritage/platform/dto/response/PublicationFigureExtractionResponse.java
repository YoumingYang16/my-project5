package com.heritage.platform.dto.response;

import java.util.List;

public record PublicationFigureExtractionResponse(
        boolean success,
        String engine,
        String message,
        List<PublicationFigureCandidateResponse> candidates
) {

    public static PublicationFigureExtractionResponse success(List<PublicationFigureCandidateResponse> candidates) {
        return new PublicationFigureExtractionResponse(
                true,
                "PDFFIGURES2",
                "Figure candidates extracted.",
                candidates
        );
    }

    public static PublicationFigureExtractionResponse failure(String message) {
        return new PublicationFigureExtractionResponse(
                false,
                "PDFFIGURES2",
                message,
                List.of()
        );
    }
}
