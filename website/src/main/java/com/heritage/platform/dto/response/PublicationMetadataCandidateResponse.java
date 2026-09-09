package com.heritage.platform.dto.response;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record PublicationMetadataCandidateResponse(
        String title,
        String authors,
        Integer year,
        String venue,
        String abstractText,
        String keywords,
        String researchArea,
        String doi,
        String bibtex,
        Map<String, PublicationMetadataFieldResponse> fields,
        List<String> warnings,
        Boolean extractionAvailable,
        Boolean success,
        String message
) {

    public static PublicationMetadataCandidateResponse unavailable(String message) {
        return new PublicationMetadataCandidateResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                Collections.emptyList(),
                false,
                false,
                message
        );
    }
}
