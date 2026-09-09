package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateManifestTests {

    @TempDir
    Path temporaryDirectory;

    private UploadPathService paths;
    private ComfyUiImageGenerationService service;

    @BeforeEach
    void setUp() {
        paths = new UploadPathService();
        Path uploads = temporaryDirectory.resolve("uploads");
        ReflectionTestUtils.setField(paths, "uploadDir", uploads.toString());
        ReflectionTestUtils.setField(paths, "generatedCoverOutputDirectory", uploads.resolve("generated-covers").toString());
        service = new ComfyUiImageGenerationService(new ObjectMapper(), paths);
        ReflectionTestUtils.setField(service, "returnCandidateCount", 1);
    }

    @Test
    void listsCandidatesOnlyWhenProviderPdfUnderstandingAndPromptVersionMatch() throws Exception {
        Path image = paths.generatedCoverDirectory(8L).resolve("publication-8-candidate-1-seed-9.png");
        Files.write(image, new byte[]{1, 2, 3});
        AiCoverCandidate candidate = new AiCoverCandidate(
                "candidate-1", paths.toUploadUrl(image), 9L, false, "product-in-use prompt"
        );
        service.saveCandidateManifest(8L, List.of(candidate), "OPENAI", "understanding-a", "pdf-a");

        assertThat(service.listExistingCandidates(8L, "pdf-a", "understanding-a"))
                .extracting(AiCoverCandidate::candidateId).containsExactly("candidate-1");
        assertThat(service.listExistingCandidates(8L, "different-pdf", "understanding-a")).isEmpty();
        assertThat(service.listExistingCandidates(8L, "pdf-a", "different-understanding")).isEmpty();
    }

    @Test
    void clearingStaleCandidatesPreservesTheAlreadySelectedCover() throws Exception {
        Path directory = paths.generatedCoverDirectory(9L);
        Path selected = directory.resolve("publication-9-candidate-1-seed-1.png");
        Path stale = directory.resolve("publication-9-candidate-2-seed-2.png");
        Files.write(selected, new byte[]{1});
        Files.write(stale, new byte[]{2});
        service.saveCandidateManifest(
                9L,
                List.of(new AiCoverCandidate("candidate-2", paths.toUploadUrl(stale), 2L, false, "prompt")),
                "OPENAI", "old-understanding", "old-pdf"
        );

        service.clearCandidates(9L, paths.toUploadUrl(selected));

        assertThat(selected).exists();
        assertThat(stale).doesNotExist();
        assertThat(directory.resolve("candidate-manifest.json")).doesNotExist();
    }
}
