package com.heritage.platform.service.ai;

import com.heritage.platform.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadPathServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void resolveSelectedGeneratedCoverAcceptsOnlyPublicationCandidateDirectory() throws Exception {
        UploadPathService service = configuredService();
        Path candidateDir = tempDir.resolve("generated-covers").resolve("123");
        Files.createDirectories(candidateDir);
        Path candidate = candidateDir.resolve("publication-123-candidate-1-seed-42.png");
        Files.write(candidate, new byte[]{1, 2, 3});

        Path resolved = service.resolveSelectedGeneratedCover(
                123L,
                "/uploads/generated-covers/123/publication-123-candidate-1-seed-42.png"
        );

        assertThat(resolved).isEqualTo(candidate.toAbsolutePath().normalize());
    }

    @Test
    void resolveSelectedGeneratedCoverRejectsOtherPublicationDirectory() throws Exception {
        UploadPathService service = configuredService();
        Path otherDir = tempDir.resolve("generated-covers").resolve("999");
        Files.createDirectories(otherDir);
        Files.write(otherDir.resolve("publication-999-candidate-1-seed-42.png"), new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.resolveSelectedGeneratedCover(
                123L,
                "/uploads/generated-covers/999/publication-999-candidate-1-seed-42.png"
        )).isInstanceOf(BadRequestException.class)
                .hasMessage("Selected candidate image does not exist.");
    }

    private UploadPathService configuredService() {
        UploadPathService service = new UploadPathService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "generatedCoverOutputDirectory", tempDir.resolve("generated-covers").toString());
        return service;
    }
}
