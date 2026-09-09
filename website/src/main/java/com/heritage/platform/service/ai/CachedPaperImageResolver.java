package com.heritage.platform.service.ai;

import com.heritage.platform.entity.Post;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class CachedPaperImageResolver {

    private static final List<CachedPaper> PAPERS = List.of(
            new CachedPaper(
                    "10.1007/978-981-96-4749-1_12",
                    "Creating Panorama Virtual Tour Systems for the Built Environment: A Practitioner Perspective",
                    List.of("C55", "Panorama", "2025.04.AAB.Panorama"),
                    List.of(
                            "/cached-paper-images/C55_Panorama/01_data_collection.png",
                            "/cached-paper-images/C55_Panorama/02_tour_design_workflow.png",
                            "/cached-paper-images/C55_Panorama/03_virtual_tour_user.png"
                    )
            ),
            new CachedPaper(
                    "10.1007/978-981-96-4749-1_13",
                    "Augmented Reality Continuum: Categorising On-Site Digital Heritage Experiences",
                    List.of("C56", "ARContinuum", "2025.04.AAB.ARContinuum"),
                    List.of(
                            "/cached-paper-images/C56_ARContinuum/01_ar_smart_glasses.png",
                            "/cached-paper-images/C56_ARContinuum/02_outdoor_ar_reconstruction.png",
                            "/cached-paper-images/C56_ARContinuum/03_museum_assisted_ar.png"
                    )
            ),
            new CachedPaper(
                    "10.1145/3706599.3719904",
                    "ARTimeTravel: Understanding Spatial Changes in Heritage Sites Over Time through Web-Based Augmented Reality Serious Games",
                    List.of("C57", "ARTimeTravel", "CHI.ARTimeTravel"),
                    List.of(
                            "/cached-paper-images/C57_ARTimeTravel/01_ar_reconstruction_guidance.png",
                            "/cached-paper-images/C57_ARTimeTravel/02_npc_route_guidance.png",
                            "/cached-paper-images/C57_ARTimeTravel/03_timeline_chatbot_checkpoint.png"
                    )
            )
    );

    public List<String> resolve(Post publication, String originalFilename) {
        if (publication == null) {
            return List.of();
        }
        return resolve(publication.getDoi(), publication.getTitle(), originalFilename);
    }

    public List<String> resolve(String doi, String title, String originalFilename) {
        String normalizedDoi = normalizeDoi(doi);
        if (!normalizedDoi.isEmpty()) {
            for (CachedPaper paper : PAPERS) {
                if (paper.normalizedDoi().equals(normalizedDoi)) {
                    return paper.imageUrls();
                }
            }
        }

        String normalizedTitle = normalizeText(title);
        if (!normalizedTitle.isEmpty()) {
            for (CachedPaper paper : PAPERS) {
                if (paper.normalizedTitle().equals(normalizedTitle)) {
                    return paper.imageUrls();
                }
            }
        }

        String normalizedFilename = normalizeText(originalFilename);
        if (!normalizedFilename.isEmpty()) {
            for (CachedPaper paper : PAPERS) {
                boolean matches = paper.filenameAliases().stream()
                        .map(CachedPaperImageResolver::normalizeText)
                        .anyMatch(normalizedFilename::contains);
                if (matches) {
                    return paper.imageUrls();
                }
            }
        }
        return List.of();
    }

    private static String normalizeDoi(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceFirst("^doi\\s*:\\s*", "");
        normalized = normalized.replaceFirst("^https?://(?:dx\\.)?doi\\.org/", "");
        return normalized.replaceAll("[\\s\\p{Punct}&&[^/._()-]]+$", "");
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[\\p{M}\\p{P}\\p{S}\\s]+", "");
    }

    private record CachedPaper(
            String doi,
            String title,
            List<String> filenameAliases,
            List<String> imageUrls
    ) {
        private CachedPaper {
            filenameAliases = List.copyOf(filenameAliases);
            imageUrls = List.copyOf(imageUrls);
        }

        private String normalizedDoi() {
            return normalizeDoi(doi);
        }

        private String normalizedTitle() {
            return normalizeText(title);
        }
    }
}
