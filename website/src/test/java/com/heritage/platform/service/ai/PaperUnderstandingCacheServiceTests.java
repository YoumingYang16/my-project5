package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingCacheEntry;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaperUnderstandingCacheServiceTests {

    @TempDir
    Path tempDirectory;

    private UploadPathService paths;
    private ObjectMapper objectMapper;
    private PaperUnderstandingCacheService cache;

    @BeforeEach
    void setUp() {
        paths = new UploadPathService();
        Path uploads = tempDirectory.resolve("uploads");
        ReflectionTestUtils.setField(paths, "uploadDir", uploads.toString());
        ReflectionTestUtils.setField(paths, "generatedCoverOutputDirectory", uploads.resolve("generated-covers").toString());
        objectMapper = new ObjectMapper();
        cache = new PaperUnderstandingCacheService(objectMapper, paths, new PaperAiQualityValidator());
    }

    @Test
    void reusesOnlyStrongOpenAiCacheForTheMatchingPdfHash() throws Exception {
        Path pdf = tempDirectory.resolve("paper.pdf");
        Files.writeString(pdf, "first pdf content");
        String firstHash = cache.pdfHash(pdf);

        cache.save(
                42L, firstHash, packet(), understanding("OPENAI"), ReferenceGroundingContext.empty(null),
                "OPENAI", "gpt-test", List.of()
        );

        assertThat(cache.find(42L, firstHash)).isPresent();
        assertThat(cache.find(42L, firstHash).orElseThrow().understandingSource()).isEqualTo("OPENAI");

        Files.writeString(pdf, "changed pdf content");
        assertThat(cache.find(42L, cache.pdfHash(pdf))).isEmpty();
    }

    @Test
    void rejectsOllamaFallbackAndPlaceholderCaches() throws Exception {
        Path pdf = tempDirectory.resolve("paper.pdf");
        Files.writeString(pdf, "pdf content");
        String hash = cache.pdfHash(pdf);

        cache.save(
                43L, hash, packet(), understanding("OLLAMA"), ReferenceGroundingContext.empty(null),
                "OLLAMA", "qwen3:14b", List.of()
        );
        assertThat(cache.find(43L, hash)).isEmpty();

        Path cachePath = paths.generatedCoverDirectory(44L).resolve("paper-understanding-cache.json");
        PaperUnderstandingResult placeholder = understanding("OPENAI", "test1");
        objectMapper.writeValue(cachePath.toFile(), new PaperUnderstandingCacheEntry(
                PaperUnderstandingCacheService.CACHE_VERSION, hash, packet(), placeholder,
                ReferenceGroundingContext.empty(null), "OPENAI", "2026-06-29T00:00:00Z", "gpt-test", List.of()
        ));
        assertThat(cache.find(44L, hash)).isEmpty();
    }

    private PaperEvidencePacket packet() {
        String body = "The paper proposes a sensor-guided inspection assistant for industrial quality review. "
                .repeat(5);
        String text = "Title: Inspection assistant\nAbstract: " + body + "\nMethod: " + body;
        return new PaperEvidencePacket(
                "Inspection assistant", body, List.of("A. Researcher"), 2026, "Inspection Journal", null,
                List.of("inspection", "sensor guidance"), List.of(body), List.of(body), List.of(body),
                List.of(body), List.of(body), List.of(), List.of(), List.of("inspection"),
                List.of(body), List.of("inspection interface"), text, text.length(), List.of()
        );
    }

    private PaperUnderstandingResult understanding(String source) {
        return understanding(source, "sensor-guided inspection assistant");
    }

    private PaperUnderstandingResult understanding(String source, String proposed) {
        FinalPaperUnderstanding value = new FinalPaperUnderstanding(
                "Inspection assistant", "The paper presents an assistant for reliable industrial inspection.",
                List.of("A. Researcher"), 2026,
                "manual inspection can produce inconsistent defect review",
                "industrial quality inspectors",
                "sensor observations guide an inspector's review workflow",
                "a sensor module and inspection interface",
                "the assistant supports more consistent defect review",
                List.of("sensor module", "inspection interface", "finding overlay"),
                "sensor observations become highlighted findings",
                "inspectors can focus their review on likely defects", null,
                List.of("generic AI wallpaper"),
                "an inspector reviews a manufactured part at an inspection station",
                List.of("inspector", "part", "interface"),
                List.of("inspector reviews highlighted findings"),
                "industrial quality inspection station", "HIGH", "Inspection Journal", proposed,
                "inspection workstation interface", "sensor observations", "highlighted findings",
                "more consistent defect review",
                List.of("inspector", "inspection assistant", "inspection station", "highlighted findings"),
                List.of("generic technology poster"),
                "industrial quality inspection station",
                "inspect a manufactured part and review likely defects",
                "the inspector positions the part and reviews highlighted findings"
        );
        return new PaperUnderstandingResult(
                "COMPLETED", GrobidMetadata.unavailable(null), null, value, List.of(), source
        );
    }
}
