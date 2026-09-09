package com.heritage.platform.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CachedPaperImageResolverTests {

    private final CachedPaperImageResolver resolver = new CachedPaperImageResolver();

    @Test
    void exactDoiTakesPriorityOverTitleAndFilename() {
        List<String> images = resolver.resolve(
                "https://doi.org/10.1007/978-981-96-4749-1_13",
                "Creating Panorama Virtual Tour Systems for the Built Environment: A Practitioner Perspective",
                "C57-CHI.ARTimeTravel.pdf"
        );

        assertThat(images).containsExactly(
                "/cached-paper-images/C56_ARContinuum/01_ar_smart_glasses.png",
                "/cached-paper-images/C56_ARContinuum/02_outdoor_ar_reconstruction.png",
                "/cached-paper-images/C56_ARContinuum/03_museum_assisted_ar.png"
        );
    }

    @Test
    void titleMatchingIgnoresCasePunctuationAndWhitespace() {
        List<String> images = resolver.resolve(
                null,
                "  creating panorama virtual tour systems for the built environment -- a practitioner perspective  ",
                "unknown.pdf"
        );

        assertThat(images).hasSize(3)
                .allMatch(url -> url.startsWith("/cached-paper-images/C55_Panorama/"));
    }

    @Test
    void filenameAliasesProvideTheFinalFallback() {
        List<String> images = resolver.resolve(null, null, "uuid-[C57]2025.04.CHI.ARTimeTravel-(1).pdf");

        assertThat(images).hasSize(3)
                .allMatch(url -> url.startsWith("/cached-paper-images/C57_ARTimeTravel/"));
    }

    @Test
    void unrelatedPapersDoNotMatch() {
        assertThat(resolver.resolve(
                "10.0000/unrelated",
                "A Different Heritage Paper",
                "different-paper.pdf"
        )).isEmpty();
    }

    @Test
    void legacyMappedImagesAreNotPackagedAsStaticResources() {
        List<String> allImages = List.of(
                resolver.resolve("10.1007/978-981-96-4749-1_12", null, null),
                resolver.resolve("10.1007/978-981-96-4749-1_13", null, null),
                resolver.resolve("10.1145/3706599.3719904", null, null)
        ).stream().flatMap(List::stream).toList();

        assertThat(allImages).hasSize(9)
                .allMatch(url -> getClass().getResource("/static" + url) == null);
    }
}
