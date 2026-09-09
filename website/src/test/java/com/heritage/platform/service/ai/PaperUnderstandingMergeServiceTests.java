package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaperUnderstandingMergeServiceTests {

    private final PaperUnderstandingMergeService mergeService = new PaperUnderstandingMergeService();

    @Test
    void mergePrefersGrobidBibliographyAndKeepsOpenAiSemantics() {
        GrobidMetadata grobid = new GrobidMetadata(
                "GROBID Title", "A complete extracted abstract.", List.of("G. Author"), 2025,
                "GROBID Venue", null, List.of(), "GROBID", true, List.of()
        );
        OpenAiPaperUnderstanding openAi = new OpenAiPaperUnderstanding(
                "OpenAI Title", "OpenAI summary", List.of("O. Author"), 2024,
                "manual inspection is inconsistent", "quality inspectors",
                "sensor observations guide review", "inspection assistant", "consistent review",
                List.of("sensor", "interface"), "observations become findings", "supports inspectors",
                null, List.of("generic poster"), "inspector reviews a part",
                List.of("inspector", "part"), List.of("reviews highlighted findings"),
                "inspection station", List.of(), "HIGH", "OpenAI Venue",
                "sensor-guided inspection assistant", "inspection interface", "sensor observations",
                "highlighted findings", "consistent review",
                List.of("inspector", "assistant", "station", "findings"), List.of("generic poster"),
                "inspection station", "inspect a part", "review highlighted findings"
        );

        PaperUnderstandingResult result = mergeService.merge(
                grobid, openAi, null, null, 2026, null, "OPENAI"
        );

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.understandingSource()).isEqualTo("OPENAI");
        assertThat(result.finalUnderstanding().title()).isEqualTo("GROBID Title");
        assertThat(result.finalUnderstanding().authors()).containsExactly("G. Author");
        assertThat(result.finalUnderstanding().proposedSystemOrMethod())
                .isEqualTo("sensor-guided inspection assistant");
        assertThat(result.finalUnderstanding().applicationEnvironment()).isEqualTo("inspection station");
    }

    @Test
    void openAiFailureNeverBecomesAGrobidOrDeterministicFinalUnderstanding() {
        GrobidMetadata grobid = new GrobidMetadata(
                "Extracted title", "A detailed extracted abstract and method description.",
                List.of("A. Researcher"), 2026, "GROBID", true, List.of()
        );

        PaperUnderstandingResult result = mergeService.merge(grobid, null, "OpenAI timed out.");

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.finalUnderstanding()).isNull();
        assertThat(result.warnings())
                .contains("OpenAI timed out.", PaperAiQualityValidator.AUTHORITATIVE_UNDERSTANDING_MESSAGE);
    }
}
