package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.AiCoverGenerationResult;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.AiCoverSelectionResponse;
import com.heritage.platform.entity.Category;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.repository.PostRepository;
import com.heritage.platform.service.AuthContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicationAiCoverSelectionTests {

    @Test
    void singleCandidateResponseRequiresManualConfirmation() {
        PublicationAiCoverService service = service(
                mock(AuthContextService.class),
                mock(PostRepository.class),
                mock(UploadPathService.class)
        );

        String message = ReflectionTestUtils.invokeMethod(
                service, "generationMessage", "CACHED_OPENAI", true, false, 1
        );

        assertThat(message)
                .contains("已复用基于 GROBID/PDF 证据与 OpenAI 生成的论文理解")
                .contains("One paper-grounded product-in-use candidate image was generated")
                .contains("review manually before selecting a cover")
                .doesNotContain("Ollama", "automatically", "three");
    }

    @Test
    void selectingCandidateStillUpdatesPublicationCover() {
        AuthContextService auth = mock(AuthContextService.class);
        PostRepository repository = mock(PostRepository.class);
        UploadPathService uploadPaths = mock(UploadPathService.class);
        Post publication = Post.create(
                "Paper", "Content", null, null, null, mock(User.class), mock(Category.class)
        );
        String imageUrl = "/uploads/generated-covers/9/publication-9-candidate-1-seed-1.png";
        when(repository.findById(9L)).thenReturn(Optional.of(publication));
        when(uploadPaths.resolveSelectedGeneratedCover(9L, imageUrl)).thenReturn(Path.of("candidate.png"));

        PublicationAiCoverService service = service(auth, repository, uploadPaths);

        AiCoverSelectionResponse response = service.selectCandidate(9L, "candidate-1", imageUrl);

        assertThat(publication.getCoverImageUrl()).isEqualTo(imageUrl);
        assertThat(response.coverImageUrl()).isEqualTo(imageUrl);
        verify(auth).requireAdmin();
        verify(uploadPaths).resolveSelectedGeneratedCover(9L, imageUrl);
    }

    @Test
    void understandingFailureNeverFallsBackToHardcodedImages(@TempDir Path tempDir) throws Exception {
        AuthContextService auth = mock(AuthContextService.class);
        PostRepository repository = mock(PostRepository.class);
        UploadPathService uploadPaths = mock(UploadPathService.class);
        PaperUnderstandingResolverService understanding = mock(PaperUnderstandingResolverService.class);
        VisualBriefPlannerService planner = mock(VisualBriefPlannerService.class);
        ScenarioBasedPromptService scenarioPrompts = mock(ScenarioBasedPromptService.class);
        ComfyUiImageGenerationService comfyUi = mock(ComfyUiImageGenerationService.class);
        User admin = mock(User.class);
        Post publication = Post.create(
                "Augmented Reality Continuum: Categorising On-Site Digital Heritage Experiences",
                "Content", null, null, null, mock(User.class), mock(Category.class)
        );
        publication.updatePublicationMetadata(
                true, null, null, null, null, null,
                "10.1007/978-981-96-4749-1_13", null, null,
                "/uploads/id-C56-ARContinuum.pdf", null, null
        );
        Path pdfPath = Path.of("id-C56-ARContinuum.pdf");
        Path generatedDirectory = Files.createDirectories(tempDir.resolve("generated-covers/56"));
        when(auth.requireAdmin()).thenReturn(admin);
        when(admin.getId()).thenReturn(1L);
        when(repository.findById(56L)).thenReturn(Optional.of(publication));
        when(uploadPaths.resolveUploadedPdf(publication.getPdfUrl())).thenReturn(pdfPath);
        when(uploadPaths.generatedCoverDirectory(56L)).thenReturn(generatedDirectory);
        when(uploadPaths.existingGeneratedCoverDirectory(56L)).thenReturn(generatedDirectory);
        when(understanding.resolve(56L, publication, pdfPath))
                .thenThrow(new AiCoverWorkflowException("Understanding unavailable"));

        PublicationAiCoverService service = new PublicationAiCoverService(
                auth, repository, uploadPaths, understanding,
                mock(ApplicationSceneBriefService.class), scenarioPrompts, planner,
                mock(VisualBriefStylistService.class), mock(VisualBriefCriticService.class),
                comfyUi, mock(CandidateSceneRankingService.class),
                mock(PaperUnderstandingCacheService.class), new PaperAiQualityValidator(),
                new CachedPaperImageResolver()
        );

        assertThat(service.getExistingCandidates(56L)).isEmpty();
        AiCoverGenerationResult result = service.generate(56L);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.message()).contains("Understanding unavailable");
        assertThat(service.getExistingCandidates(56L)).isEmpty();
        verifyNoInteractions(planner, scenarioPrompts);
    }
    @Test
    void selectingMappedStaticImageStillRequiresPublicationScopedValidation() {
        AuthContextService auth = mock(AuthContextService.class);
        PostRepository repository = mock(PostRepository.class);
        UploadPathService uploadPaths = mock(UploadPathService.class);
        Post publication = Post.create(
                "Creating Panorama Virtual Tour Systems for the Built Environment: A Practitioner Perspective",
                "Content", null, null, null, mock(User.class), mock(Category.class)
        );
        publication.updatePublicationMetadata(
                true, null, null, null, null, null,
                "10.1007/978-981-96-4749-1_12", null, null,
                "/uploads/id-C55-Panorama.pdf", null, null
        );
        String imageUrl = "/cached-paper-images/C55_Panorama/02_tour_design_workflow.png";
        when(repository.findById(55L)).thenReturn(Optional.of(publication));

        PublicationAiCoverService service = new PublicationAiCoverService(
                auth, repository, uploadPaths,
                mock(PaperUnderstandingResolverService.class),
                mock(ApplicationSceneBriefService.class),
                mock(ScenarioBasedPromptService.class),
                mock(VisualBriefPlannerService.class),
                mock(VisualBriefStylistService.class),
                mock(VisualBriefCriticService.class),
                mock(ComfyUiImageGenerationService.class),
                mock(CandidateSceneRankingService.class),
                mock(PaperUnderstandingCacheService.class),
                new PaperAiQualityValidator(),
                new CachedPaperImageResolver()
        );

        AiCoverSelectionResponse response = service.selectCandidate(55L, "candidate-2", imageUrl);

        assertThat(response.coverImageUrl()).isEqualTo(imageUrl);
        assertThat(publication.getCoverImageUrl()).isEqualTo(imageUrl);
        verify(uploadPaths).resolveSelectedGeneratedCover(55L, imageUrl);
    }

    private PublicationAiCoverService service(
            AuthContextService auth,
            PostRepository repository,
            UploadPathService uploadPaths
    ) {
        return new PublicationAiCoverService(
                auth,
                repository,
                uploadPaths,
                mock(PaperUnderstandingResolverService.class),
                mock(ApplicationSceneBriefService.class),
                mock(ScenarioBasedPromptService.class),
                mock(VisualBriefPlannerService.class),
                mock(VisualBriefStylistService.class),
                mock(VisualBriefCriticService.class),
                mock(ComfyUiImageGenerationService.class),
                mock(CandidateSceneRankingService.class),
                mock(PaperUnderstandingCacheService.class),
                new PaperAiQualityValidator(),
                mock(CachedPaperImageResolver.class)
        );
    }
}
