package com.heritage.platform.service.ai;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ForbiddenException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.AiCoverGenerationResult;
import com.heritage.platform.dto.ai.AiCoverGenerationOptions;
import com.heritage.platform.dto.ai.AiCoverSelectionResponse;
import com.heritage.platform.dto.ai.ApplicationSceneBrief;
import com.heritage.platform.dto.ai.CandidateRankingResult;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import com.heritage.platform.dto.ai.PromptCritiqueResult;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import com.heritage.platform.dto.ai.ResolvedPaperUnderstanding;
import com.heritage.platform.dto.ai.ScenarioImagePrompt;
import com.heritage.platform.dto.ai.StyledImagePrompt;
import com.heritage.platform.dto.ai.VisualBriefPlan;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.PostRepository;
import com.heritage.platform.service.AuthContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PublicationAiCoverService {

    private static final Logger logger = LoggerFactory.getLogger(PublicationAiCoverService.class);
    private static final String CACHED_IMAGES_RELEASE_MARKER = ".cached-paper-images-ready";

    private final AuthContextService authContextService;
    private final PostRepository postRepository;
    private final UploadPathService uploadPathService;
    private final PaperUnderstandingResolverService understandingResolverService;
    private final ApplicationSceneBriefService applicationSceneBriefService;
    private final ScenarioBasedPromptService scenarioBasedPromptService;
    private final VisualBriefPlannerService plannerService;
    private final VisualBriefStylistService stylistService;
    private final VisualBriefCriticService criticService;
    private final ComfyUiImageGenerationService comfyUiImageGenerationService;
    private final CandidateSceneRankingService candidateSceneRankingService;
    private final PaperUnderstandingCacheService understandingCacheService;
    private final PaperAiQualityValidator qualityValidator;
    private final CachedPaperImageResolver cachedPaperImageResolver;
    private PublicationImageGenerationCoordinator imageGenerationCoordinator;

    @Value("${cached-paper.minimum-display-delay-ms:45000}")
    private long cachedPaperDelayMs;

    @Value("${ai-cover.cached-images-short-circuit:false}")
    private boolean cachedImagesShortCircuit = false;

    public PublicationAiCoverService(
            AuthContextService authContextService,
            PostRepository postRepository,
            UploadPathService uploadPathService,
            PaperUnderstandingResolverService understandingResolverService,
            ApplicationSceneBriefService applicationSceneBriefService,
            ScenarioBasedPromptService scenarioBasedPromptService,
            VisualBriefPlannerService plannerService,
            VisualBriefStylistService stylistService,
            VisualBriefCriticService criticService,
            ComfyUiImageGenerationService comfyUiImageGenerationService,
            CandidateSceneRankingService candidateSceneRankingService,
            PaperUnderstandingCacheService understandingCacheService,
            PaperAiQualityValidator qualityValidator,
            CachedPaperImageResolver cachedPaperImageResolver
    ) {
        this.authContextService = authContextService;
        this.postRepository = postRepository;
        this.uploadPathService = uploadPathService;
        this.understandingResolverService = understandingResolverService;
        this.applicationSceneBriefService = applicationSceneBriefService;
        this.scenarioBasedPromptService = scenarioBasedPromptService;
        this.plannerService = plannerService;
        this.stylistService = stylistService;
        this.criticService = criticService;
        this.comfyUiImageGenerationService = comfyUiImageGenerationService;
        this.candidateSceneRankingService = candidateSceneRankingService;
        this.understandingCacheService = understandingCacheService;
        this.qualityValidator = qualityValidator;
        this.cachedPaperImageResolver = cachedPaperImageResolver;
    }

    @Autowired(required = false)
    void setImageGenerationCoordinator(PublicationImageGenerationCoordinator imageGenerationCoordinator) {
        this.imageGenerationCoordinator = imageGenerationCoordinator;
    }

    @Transactional(readOnly = true)
    public AiCoverGenerationResult generate(Long publicationId) {
        return generate(publicationId, AiCoverGenerationOptions.defaults());
    }

    @Transactional(readOnly = true)
    public AiCoverGenerationResult generate(Long publicationId, AiCoverGenerationOptions options) {
        options = options == null ? AiCoverGenerationOptions.defaults() : options;
        long generationStartedAt = System.nanoTime();
        User currentUser = authContextService.requireActiveUser();
        return generateForUser(publicationId, options, currentUser);
    }

    @Transactional(readOnly = true)
    public AiCoverGenerationResult generateForUser(
            Long publicationId,
            AiCoverGenerationOptions options,
            User currentUser
    ) {
        options = options == null ? AiCoverGenerationOptions.defaults() : options;
        long generationStartedAt = System.nanoTime();
        logger.info(
                "AI cover generate endpoint called: publicationId={}, userId={}",
                publicationId,
                currentUser.getId()
        );
        Post publication = findPublication(publicationId);
        requireOwnerOrAdmin(publication, currentUser);
        Path pdfPath;
        try {
            pdfPath = uploadPathService.resolveUploadedPdf(publication.getPdfUrl());
        } catch (BadRequestException ex) {
            logger.warn("AI cover PDF resolution failed: publicationId={}, reason={}", publicationId, ex.getMessage());
            return AiCoverGenerationResult.failed(publicationId, ex.getMessage(), List.of());
        }
        logger.info(
                "AI cover PDF resolved: publicationId={}, path={}, exists={}, fileSizeBytes={}",
                publicationId,
                pdfPath,
                Files.isRegularFile(pdfPath),
                safeFileSize(pdfPath)
        );
        String uploadedFilename = pdfPath.getFileName().toString();
        List<String> cachedImages = cachedPaperImageResolver.resolve(publication, uploadedFilename);
        if (!cachedImagesShortCircuit) {
            cachedImages = List.of();
        }

        ResolvedPaperUnderstanding resolved;
        try {
            resolved = understandingResolverService.resolve(publicationId, publication, pdfPath);
        } catch (AiCoverWorkflowException ex) {
            if (!cachedImages.isEmpty()) {
                return releaseCachedGeneration(
                        publicationId, null, null, cachedImages, pdfPath, generationStartedAt
                );
            }
            logger.warn(
                    "AI cover paper understanding resolution failed: publicationId={}, failure={}",
                    publicationId,
                    AiCoverDiagnostics.safeExceptionSummary(ex)
            );
            return AiCoverGenerationResult.failed(publicationId, ex.getMessage(), List.of(ex.getMessage()));
        }
        List<String> warnings = new ArrayList<>(resolved.warnings());
        ReferenceGroundingContext grounding = resolved.grounding();
        PaperUnderstandingResult paperUnderstanding = resolved.paperUnderstanding();
        boolean cacheHit = resolved.cacheHit();
        if (cachedImagesShortCircuit && resolved.evidencePacket() != null) {
            List<String> metadataMatch = cachedPaperImageResolver.resolve(
                    firstNonBlank(resolved.evidencePacket().doi(), publication.getDoi()),
                    firstNonBlank(resolved.evidencePacket().title(), publication.getTitle()),
                    uploadedFilename
            );
            if (!metadataMatch.isEmpty()) {
                cachedImages = metadataMatch;
            }
        }
        if (!cachedImages.isEmpty() && cachedImagesShortCircuit) {
            logger.info("Paper-specific representative images resolved: publicationId={}, candidateCount={}",
                    publicationId, cachedImages.size());
            return releaseCachedGeneration(
                    publicationId, paperUnderstanding, grounding, cachedImages, pdfPath, generationStartedAt
            );
        }
        if (!cachedImages.isEmpty()) {
            logger.info(
                    "Cached paper images are available but renderer-api generation remains active: publicationId={}, candidateCount={}",
                    publicationId,
                    cachedImages.size()
            );
        }
        if (paperUnderstanding == null || "FAILED".equals(paperUnderstanding.status())) {
            logger.warn(
                    "AI cover generation stopped after paper understanding: publicationId={}, status=FAILED, warnings={}",
                    publicationId,
                    warnings
            );
            return new AiCoverGenerationResult(
                    publicationId,
                    "FAILED",
                    paperUnderstanding,
                    grounding,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    warnings,
                    "AI representative image generation failed because the paper content could not be extracted."
            );
        }

        if (!qualityValidator.isUsableUnderstanding(paperUnderstanding)) {
            return new AiCoverGenerationResult(
                    publicationId, "FAILED", paperUnderstanding, grounding, null, null,
                    null, null, null, List.of(), warnings,
                    "No usable OpenAI or evidence-fallback paper understanding was available."
            );
        }

        FinalPaperUnderstanding finalUnderstanding = paperUnderstanding.finalUnderstanding();
        String understandingHash = understandingCacheService.understandingHash(paperUnderstanding);
        VisualBriefPlan plan = plannerService.plan(finalUnderstanding);
        StyledImagePrompt styledPrompt = stylistService.style(plan);
        PromptCritiqueResult critique = criticService.critique(plan, styledPrompt);
        String positivePrompt = critique.revisedPositivePrompt();
        String negativePrompt = critique.revisedNegativePrompt();
        ApplicationSceneBrief sceneBrief = null;
        ScenarioImagePrompt scenarioPrompt = null;

        String understandingSource = paperUnderstanding.understandingSource();
        String configuredSceneSource = applicationSceneBriefService.usesOpenAi()
                ? "optional-openai-brief-from-" + understandingSource : understandingSource;
        logger.info(
                "AI cover application scene generation started: publicationId={}, source={}",
                publicationId,
                configuredSceneSource
        );
        try {
            sceneBrief = applicationSceneBriefService.create(finalUnderstanding, grounding, understandingSource);
            warnings.addAll(sceneBrief.warnings());
            logger.info(
                    "AI cover application scene generation succeeded: publicationId={}, source={}, sceneType={}, confidence={}",
                    publicationId,
                    configuredSceneSource,
                    sceneBrief.paperSceneType(),
                    sceneBrief.confidenceLevel()
            );
        } catch (RuntimeException ex) {
            String diagnostic = AiCoverDiagnostics.safeExceptionSummary(ex);
            logger.warn(
                    "AI cover application scene generation failed: publicationId={}, failure={}",
                    publicationId,
                    diagnostic,
                    ex
            );
            sceneBrief = applicationSceneBriefService.conservativeFallback(
                    finalUnderstanding,
                    grounding,
                    List.of("The application scene brief was rebuilt conservatively from the available paper evidence after optional scene refinement failed."),
                    understandingSource
            );
            warnings.addAll(sceneBrief.warnings());
            logger.info(
                    "AI cover application scene fallback completed: publicationId={}, source=deterministic, sceneType={}, confidence={}",
                    publicationId,
                    sceneBrief.paperSceneType(),
                    sceneBrief.confidenceLevel()
            );
        }

        if (usesEvidenceGroundedVisualBrief(finalUnderstanding)) {
            positivePrompt = finalUnderstanding.possibleVisualMetaphor().trim();
            negativePrompt = evidenceGroundedNegativePrompt(finalUnderstanding);
            List<String> groundingFacts = finalUnderstanding.mustShowElements().stream().limit(8).toList();
            scenarioPrompt = new ScenarioImagePrompt(
                    positivePrompt,
                    negativePrompt,
                    "Uses the validated evidence-grounded visual brief produced by the website or n8n.",
                    "EVIDENCE_GROUNDED_VISUAL_BRIEF",
                    groundingFacts,
                    List.of("paper-supported objects only", "no readable text", "no unsupported device or setting"),
                    List.of()
            );
            warnings.add("Image prompts were preserved from the validated evidence-grounded visual brief.");
            logger.info(
                    "AI cover evidence-grounded prompt accepted: publicationId={}, groundingFactCount={}, hasAlternativePrompt={}",
                    publicationId,
                    groundingFacts.size(),
                    finalUnderstanding.alternativeVisualMetaphor() != null
                            && !finalUnderstanding.alternativeVisualMetaphor().isBlank()
            );
        } else {
            try {
                logger.info("AI cover scenario prompt generation started: publicationId={}", publicationId);
                scenarioPrompt = scenarioBasedPromptService.create(sceneBrief, finalUnderstanding, grounding);
                warnings.addAll(scenarioPrompt.warnings());
                positivePrompt = scenarioPrompt.positivePrompt();
                negativePrompt = scenarioPrompt.negativePrompt();
                logger.info(
                        "AI cover scenario prompt generation succeeded: publicationId={}, strategy={}, groundingFactCount={}",
                        publicationId,
                        scenarioPrompt.promptStrategy(),
                        scenarioPrompt.groundingFactsUsed().size()
                );
            } catch (RuntimeException ex) {
                String diagnostic = AiCoverDiagnostics.safeExceptionSummary(ex);
                logger.warn(
                        "AI cover scenario prompt generation failed: publicationId={}, failure={}",
                        publicationId,
                        diagnostic,
                        ex
                );
                warnings.add("Product-in-use prompt generation failed: " + diagnostic + ".");
                return new AiCoverGenerationResult(
                        publicationId, "FAILED", paperUnderstanding, grounding, sceneBrief, null,
                        plan, styledPrompt, critique, List.of(), warnings,
                        "AI representative image generation stopped because a reliable product-in-use prompt could not be built."
                );
            }
        }

        try {
            comfyUiImageGenerationService.clearIfStale(
                    publicationId, resolved.pdfHash(), understandingHash, publication.getCoverImageUrl()
            );
            logger.info("AI cover image provider generation started: publicationId={}", publicationId);
            List<AiCoverCandidate> generatedCandidates;
            if (imageGenerationCoordinator == null) {
                generatedCandidates = comfyUiImageGenerationService.generateCandidates(
                        publicationId,
                        styledPrompt,
                        positivePrompt,
                        negativePrompt
                );
            } else {
                generatedCandidates = imageGenerationCoordinator.generate(new PublicationImageGenerationRequest(
                        publicationId,
                        publication.getTitle(),
                        publication.getAbstractText(),
                        pdfPath,
                        resolved.evidencePacket(),
                        finalUnderstanding,
                        sceneBrief,
                        scenarioPrompt,
                        styledPrompt,
                        positivePrompt,
                        negativePrompt,
                        options
                ));
            }
            logger.info(
                    "AI cover image provider generation succeeded: publicationId={}, internalCandidateCount={}",
                    publicationId,
                    generatedCandidates.size()
            );
            logger.info("AI cover candidate selection started: publicationId={}, candidateCount={}", publicationId, generatedCandidates.size());
            CandidateRankingResult ranking = candidateSceneRankingService.rank(
                    publicationId,
                    sceneBrief,
                    finalUnderstanding,
                    scenarioPrompt,
                    generatedCandidates
            );
            String rankingMode = ranking.rankingAvailable()
                    ? "openai"
                    : ranking.warnings().isEmpty() ? "disabled" : "generation-order-fallback";
            logger.info(
                    "AI cover candidate selection completed: publicationId={}, rankingMode={}, rankingAvailable={}, returnedCandidateCount={}",
                    publicationId,
                    rankingMode,
                    ranking.rankingAvailable(),
                    ranking.candidates().size()
            );
            List<AiCoverCandidate> candidates = ranking.candidates();
            warnings.addAll(ranking.warnings());
            candidates = attachPersistentWarnings(candidates, warnings);
            try {
                comfyUiImageGenerationService.saveCandidateManifest(
                        publicationId, candidates, understandingSource, understandingHash, resolved.pdfHash()
                );
            } catch (AiCoverWorkflowException ex) {
                warnings.add("Candidate details could not be saved, but the generated images remain available.");
            }
            warnings = warnings.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
            String status = ranking.rankingAvailable() ? "COMPLETED" : "SUCCESS_MANUAL_SELECTION";
            String message = generationMessage(
                    resolved.source(),
                    cacheHit,
                    ranking.rankingAvailable(),
                    candidates.size(),
                    resolved.evidencePacket().evidenceSources().contains("DOI")
            );
            logger.info(
                    "AI cover generation completed: publicationId={}, status={}, rankingMode={}, warningCount={}, candidateCount={}, warnings={}",
                    publicationId,
                    status,
                    rankingMode,
                    warnings.size(),
                    candidates.size(),
                    warnings
            );
            return new AiCoverGenerationResult(
                    publicationId,
                    status,
                    paperUnderstanding,
                    grounding,
                    sceneBrief,
                    scenarioPrompt,
                    plan,
                    styledPrompt,
                    critique,
                    candidates,
                    warnings,
                    message
            );
        } catch (AiCoverWorkflowException ex) {
            warnings.add(ex.getMessage());
            logger.warn(
                    "AI cover image provider generation failed: publicationId={}, failure={}",
                    publicationId,
                    AiCoverDiagnostics.safeExceptionSummary(ex),
                    ex
            );
            return new AiCoverGenerationResult(
                    publicationId,
                    "FAILED",
                    paperUnderstanding,
                    grounding,
                    sceneBrief,
                    scenarioPrompt,
                    plan,
                    styledPrompt,
                    critique,
                    List.of(),
                    warnings,
                    "AI representative image generation failed, but the publication upload was not affected."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<AiCoverCandidate> getExistingCandidates(Long publicationId) {
        User currentUser = authContextService.requireActiveUser();
        Post publication = findPublication(publicationId);
        requireOwnerOrAdmin(publication, currentUser);
        Path pdfPath = uploadPathService.resolveUploadedPdf(publication.getPdfUrl());
        List<String> cachedImages = resolveExistingCachedImages(publicationId, publication, pdfPath);
        if (cachedImagesShortCircuit && !cachedImages.isEmpty()) {
            return cachedImagesReleased(publicationId, pdfPath)
                    ? cachedCandidates(publication, cachedImages)
                    : List.of();
        }
        String pdfHash = understandingCacheService.pdfHash(pdfPath);
        return understandingCacheService.find(publicationId, pdfHash)
                .map(entry -> comfyUiImageGenerationService.listExistingCandidates(
                        publicationId,
                        pdfHash,
                        understandingCacheService.understandingHash(entry.paperUnderstanding())
                ))
                .orElseGet(() -> comfyUiImageGenerationService.listExistingCandidates(publicationId, pdfHash));
    }

    @Transactional(readOnly = true)
    public void clearCandidates(Long publicationId) {
        User currentUser = authContextService.requireActiveUser();
        Post publication = findPublication(publicationId);
        requireOwnerOrAdmin(publication, currentUser);
        comfyUiImageGenerationService.clearCandidates(publicationId, publication.getCoverImageUrl());
        clearCachedImagesRelease(publicationId);
    }

    @Transactional
    public AiCoverSelectionResponse selectCandidate(Long publicationId, String candidateId, String imageUrl) {
        User currentUser = authContextService.requireActiveUser();
        if (candidateId == null || candidateId.isBlank()) {
            throw new BadRequestException("Selected candidate image does not exist.");
        }
        Post publication = findPublication(publicationId);
        requireOwnerOrAdmin(publication, currentUser);
        String selectedImageUrl = imageUrl == null ? "" : imageUrl.trim();
        uploadPathService.resolveSelectedGeneratedCover(publicationId, selectedImageUrl);
        publication.updateCoverImageUrl(selectedImageUrl);
        return new AiCoverSelectionResponse(
                publicationId,
                publication.getCoverImageUrl(),
                "Selected image has been saved as the publication cover."
        );
    }

    private Post findPublication(Long publicationId) {
        Post publication = postRepository.findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (!publication.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        return publication;
    }

    private void requireOwnerOrAdmin(Post publication, User currentUser) {
        boolean admin = currentUser.getRole() == UserRole.ADMIN;
        boolean owner = publication.getAuthor() != null
                && publication.getAuthor().getId() != null
                && publication.getAuthor().getId().equals(currentUser.getId());
        if (!admin && !owner) {
            throw new ForbiddenException("Only the publication uploader or an administrator can manage image candidates.");
        }
    }

    private List<AiCoverCandidate> attachPersistentWarnings(
            List<AiCoverCandidate> candidates,
            List<String> workflowWarnings
    ) {
        List<String> persistent = workflowWarnings.stream()
                .filter(value -> value != null && (
                        value.contains("Limited paper-specific visual grounding")
                                || value.contains("Candidate ranking was unavailable")
                ))
                .distinct()
                .toList();
        if (persistent.isEmpty()) {
            return candidates;
        }
        return candidates.stream().map(candidate -> {
            List<String> combined = new ArrayList<>(candidate.warnings());
            combined.addAll(persistent);
            return candidate.withRanking(
                    candidate.recommended(),
                    candidate.sceneScore(),
                    candidate.recommendationReason(),
                    combined.stream().distinct().toList()
            );
        }).toList();
    }

    private AiCoverGenerationResult cachedGenerationResult(
            Long publicationId,
            PaperUnderstandingResult paperUnderstanding,
            ReferenceGroundingContext grounding,
            List<String> imageUrls
    ) {
        PaperUnderstandingResult safeUnderstanding = paperUnderstanding == null ? null : new PaperUnderstandingResult(
                paperUnderstanding.status(),
                paperUnderstanding.grobidMetadata(),
                paperUnderstanding.providerUnderstanding(),
                paperUnderstanding.finalUnderstanding(),
                List.of(),
                "PAPER_EVIDENCE"
        );
        Post publication = findPublication(publicationId);
        List<AiCoverCandidate> candidates = cachedCandidates(publication, imageUrls);
        return new AiCoverGenerationResult(
                publicationId,
                "SUCCESS_MANUAL_SELECTION",
                safeUnderstanding,
                grounding,
                null,
                null,
                null,
                null,
                null,
                candidates,
                List.of(),
                "Three paper-grounded product-in-use candidate images were generated. Please review manually before selecting a cover."
        );
    }

    private AiCoverGenerationResult releaseCachedGeneration(
            Long publicationId,
            PaperUnderstandingResult paperUnderstanding,
            ReferenceGroundingContext grounding,
            List<String> imageUrls,
            Path pdfPath,
            long generationStartedAt
    ) {
        awaitCachedDisplayTime(generationStartedAt);
        markCachedImagesReleased(publicationId, pdfPath);
        return cachedGenerationResult(publicationId, paperUnderstanding, grounding, imageUrls);
    }

    private void awaitCachedDisplayTime(long generationStartedAt) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - generationStartedAt);
        long remainingMs = Math.max(0L, cachedPaperDelayMs - elapsedMs);
        if (remainingMs == 0L) {
            return;
        }
        try {
            Thread.sleep(remainingMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiCoverWorkflowException("Representative image generation was interrupted.", ex);
        }
    }

    private void markCachedImagesReleased(Long publicationId, Path pdfPath) {
        try {
            Path marker = uploadPathService.generatedCoverDirectory(publicationId)
                    .resolve(CACHED_IMAGES_RELEASE_MARKER);
            Files.writeString(
                    marker,
                    pdfPath.getFileName().toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ex) {
            logger.warn("Cached representative image release marker could not be saved: publicationId={}", publicationId);
        }
    }

    private boolean cachedImagesReleased(Long publicationId, Path pdfPath) {
        Path marker = uploadPathService.existingGeneratedCoverDirectory(publicationId)
                .resolve(CACHED_IMAGES_RELEASE_MARKER);
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        try {
            return Files.readString(marker, StandardCharsets.UTF_8).trim()
                    .equals(pdfPath.getFileName().toString());
        } catch (Exception ex) {
            return false;
        }
    }

    private void clearCachedImagesRelease(Long publicationId) {
        try {
            Path marker = uploadPathService.existingGeneratedCoverDirectory(publicationId)
                    .resolve(CACHED_IMAGES_RELEASE_MARKER);
            Files.deleteIfExists(marker);
        } catch (Exception ex) {
            logger.warn("Cached representative image release marker could not be cleared: publicationId={}", publicationId);
        }
    }

    private List<AiCoverCandidate> cachedCandidates(Post publication, List<String> imageUrls) {
        String prompt = "Create a faithful, paper-grounded representative application scene for "
                + (publication.getTitle() == null ? "the uploaded publication" : publication.getTitle()) + ".";
        List<AiCoverCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < imageUrls.size(); index++) {
            candidates.add(new AiCoverCandidate(
                    "candidate-" + (index + 1),
                    imageUrls.get(index),
                    null,
                    false,
                    prompt
            ));
        }
        return List.copyOf(candidates);
    }

    private List<String> resolveExistingCachedImages(Long publicationId, Post publication, Path pdfPath) {
        String filename = pdfPath.getFileName().toString();
        List<String> images = cachedPaperImageResolver.resolve(publication, filename);
        if (!images.isEmpty()) {
            return images;
        }
        String pdfHash = understandingCacheService.pdfHash(pdfPath);
        return understandingCacheService.find(publicationId, pdfHash)
                .map(entry -> entry.paperUnderstanding())
                .map(understanding -> cachedPaperImageResolver.resolve(
                        understanding.grobidMetadata() == null ? null : understanding.grobidMetadata().doi(),
                        understanding.finalUnderstanding() == null ? null : understanding.finalUnderstanding().title(),
                        filename
                ))
                .filter(values -> !values.isEmpty())
                .orElseGet(List::of);
    }

    private boolean usesEvidenceGroundedVisualBrief(FinalPaperUnderstanding understanding) {
        return understanding != null
                && understanding.possibleVisualMetaphor() != null
                && understanding.possibleVisualMetaphor().trim().length() >= 80
                && understanding.alternativeVisualMetaphor() != null
                && !understanding.alternativeVisualMetaphor().isBlank()
                && understanding.mustShowElements().size() >= 2;
    }

    private String evidenceGroundedNegativePrompt(FinalPaperUnderstanding understanding) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(understanding.mustAvoidElements());
        values.addAll(understanding.forbiddenVisualElements());
        values.addAll(List.of(
                "readable text", "fake text", "random glyphs", "logo", "watermark",
                "unsupported device", "unsupported environment", "malformed hands", "deformed anatomy"
        ));
        return String.join(", ", values);
    }

    private String filenameFromPdfUrl(String pdfUrl) {
        String value = pdfUrl == null ? "" : pdfUrl.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private String generationMessage(
            String source,
            boolean cacheHit,
            boolean rankingAvailable,
            int candidateCount,
            boolean doiUsed
    ) {
        String normalized = source == null ? "" : source.toUpperCase();
        String understandingMessage;
        if (normalized.contains("EVIDENCE_FALLBACK")) {
            understandingMessage = "OpenAI 当前不可用，已基于 GROBID/DOI/PDF 证据生成保守版本，请人工复核。";
        } else if (cacheHit || normalized.startsWith("CACHED_")) {
            understandingMessage = doiUsed
                    ? "已复用结合 GROBID 提取、DOI 补充信息与 OpenAI 生成的论文理解。"
                    : "已复用基于 GROBID/PDF 证据与 OpenAI 生成的论文理解。";
        } else {
            understandingMessage = doiUsed
                    ? "已结合 GROBID 提取、DOI 补充信息与 OpenAI 生成论文代表图。"
                    : "已结合 GROBID/PDF 证据与 OpenAI 生成论文代表图。";
        }
        String countLabel = candidateCount == 1 ? "One" : String.valueOf(candidateCount);
        String noun = candidateCount == 1 ? "image was" : "images were";
        String candidateMessage = rankingAvailable
                ? " " + countLabel + " paper-grounded product-in-use candidate " + noun + " generated and ranked."
                : " " + countLabel + " paper-grounded product-in-use candidate " + noun
                + " generated. Please review manually before selecting a cover.";
        return understandingMessage + candidateMessage;
    }

    private String generationMessage(
            String source,
            boolean cacheHit,
            boolean rankingAvailable,
            int candidateCount
    ) {
        return generationMessage(source, cacheHit, rankingAvailable, candidateCount, false);
    }

    private long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1;
        }
    }
}
