package com.heritage.platform.dto.request;

import com.heritage.platform.validation.Utf8ByteSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostCreateRequest(
        @NotBlank(message = "A title is required.")
        @Utf8ByteSize(max = 150, message = "Title cannot exceed 150 UTF-8 bytes.")
        String title,

        @Utf8ByteSize(max = 60000, message = "Publication note cannot exceed 60000 UTF-8 bytes.")
        String content,

        Long categoryId,

        @Size(max = 255, message = "Cover image URL cannot exceed 255 characters.")
        String coverImageUrl,

        @Size(max = 100, message = "Heritage item name cannot exceed 100 characters.")
        String heritageName,

        @Size(max = 100, message = "Region cannot exceed 100 characters.")
        String region,

        List<String> imageUrls,

        Boolean publication,

        @Size(max = 500, message = "Authors cannot exceed 500 characters.")
        String publicationAuthors,

        @Min(value = 1000, message = "Publication year must be valid.")
        @Max(value = 3000, message = "Publication year must be valid.")
        Integer publicationYear,

        @Size(max = 200, message = "Venue cannot exceed 200 characters.")
        String venue,

        @Utf8ByteSize(max = 10000, message = "Abstract cannot exceed 10000 UTF-8 bytes.")
        String abstractText,

        @Size(max = 500, message = "Keywords cannot exceed 500 characters.")
        String keywords,

        @Size(max = 255, message = "DOI cannot exceed 255 characters.")
        String doi,

        @Utf8ByteSize(max = 10000, message = "BibTeX cannot exceed 10000 UTF-8 bytes.")
        String bibtex,

        @Size(max = 150, message = "Research area cannot exceed 150 characters.")
        String researchArea,

        @NotBlank(message = "A paper PDF is required.")
        @Size(max = 255, message = "PDF URL cannot exceed 255 characters.")
        String pdfUrl,

        @Size(max = 255, message = "Code URL cannot exceed 255 characters.")
        String codeUrl,

        @Size(max = 255, message = "Dataset URL cannot exceed 255 characters.")
        String datasetUrl,

        @NotBlank(message = "Participant ID is required.")
        @Size(min = 2, max = 40, message = "Participant ID must contain 2 to 40 characters.")
        String participantId
) {
    public PostCreateRequest(
            String title, String content, Long categoryId, String coverImageUrl, String heritageName,
            String region, List<String> imageUrls, Boolean publication, String publicationAuthors,
            Integer publicationYear, String venue, String abstractText, String keywords, String doi,
            String bibtex, String researchArea, String pdfUrl, String codeUrl, String datasetUrl
    ) {
        this(title, content, categoryId, coverImageUrl, heritageName, region, imageUrls, publication,
                publicationAuthors, publicationYear, venue, abstractText, keywords, doi, bibtex,
                researchArea, pdfUrl, codeUrl, datasetUrl, null);
    }
}
