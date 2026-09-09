package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.AiCoverGenerationResult;
import com.heritage.platform.dto.ai.AiCoverGenerationOptions;
import com.heritage.platform.dto.ai.AiCoverSelectionRequest;
import com.heritage.platform.dto.ai.AiCoverSelectionResponse;
import com.heritage.platform.dto.ai.CoverImageVariantRequest;
import com.heritage.platform.dto.ai.XhsCoverVariantRequest;
import com.heritage.platform.service.ai.PublicationAiCoverService;
import com.heritage.platform.service.ai.AiCoverGenerationJobService;
import com.heritage.platform.service.ai.CoverImageVariantService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/publications/{publicationId}/ai-cover")
public class AiCoverGenerationController {

    private final PublicationAiCoverService publicationAiCoverService;
    private final CoverImageVariantService coverImageVariantService;
    private final AiCoverGenerationJobService aiCoverGenerationJobService;

    public AiCoverGenerationController(PublicationAiCoverService publicationAiCoverService) {
        this(publicationAiCoverService, null, null);
    }

    @Autowired
    public AiCoverGenerationController(
            PublicationAiCoverService publicationAiCoverService,
            CoverImageVariantService coverImageVariantService,
            AiCoverGenerationJobService aiCoverGenerationJobService
    ) {
        this.publicationAiCoverService = publicationAiCoverService;
        this.coverImageVariantService = coverImageVariantService;
        this.aiCoverGenerationJobService = aiCoverGenerationJobService;
    }

    @PostMapping("/generate")
    public ApiResponse<AiCoverGenerationJobService.JobStatus> generate(
            @PathVariable Long publicationId,
            @RequestBody(required = false) AiCoverGenerationOptions options
    ) {
        AiCoverGenerationJobService.JobStatus job = aiCoverGenerationJobService.start(publicationId, options);
        return ApiResponse.success(job.message(), job);
    }

    @GetMapping("/generate/status/{jobId}")
    public ApiResponse<AiCoverGenerationJobService.JobStatus> generationStatus(@PathVariable String jobId) {
        AiCoverGenerationJobService.JobStatus job = aiCoverGenerationJobService.status(jobId);
        return ApiResponse.success(job.message(), job);
    }

    @GetMapping("/candidates")
    public ApiResponse<List<AiCoverCandidate>> candidates(@PathVariable Long publicationId) {
        return ApiResponse.success(publicationAiCoverService.getExistingCandidates(publicationId));
    }

    @DeleteMapping("/candidates")
    public ApiResponse<Boolean> clearCandidates(@PathVariable Long publicationId) {
        publicationAiCoverService.clearCandidates(publicationId);
        return ApiResponse.success("Stale AI candidate images were cleared without changing the publication or PDF.", true);
    }

    @PostMapping("/derive")
    public ApiResponse<java.util.Map<String, String>> derive(
            @PathVariable Long publicationId,
            @RequestBody CoverImageVariantRequest request
    ) {
        if (coverImageVariantService == null) {
            return ApiResponse.failure("Image editing is unavailable.");
        }
        String imageUrl = coverImageVariantService.derive(
                publicationId, request.imageUrl(), request.width(), request.height(), request.mode(),
                request.focusX(), request.focusY(),
                  request.cropX(), request.cropY(), request.cropWidth(), request.cropHeight(),
                  request.backgroundColor()
        );
        return ApiResponse.success(
                "An edited candidate was created without changing the original image.",
                java.util.Map.of("imageUrl", imageUrl)
        );
    }
    @PostMapping("/select")
    public ApiResponse<AiCoverSelectionResponse> select(
            @PathVariable Long publicationId,
            @Valid @RequestBody AiCoverSelectionRequest request
    ) {
        AiCoverSelectionResponse response = publicationAiCoverService.selectCandidate(
                publicationId,
                request.candidateId(),
                request.imageUrl()
        );
        return ApiResponse.success(response.message(), response);
    }

    @PostMapping("/social-cover")
    public ApiResponse<java.util.Map<String, String>> socialCover(
            @PathVariable Long publicationId,
            @RequestBody XhsCoverVariantRequest request
    ) {
        if (coverImageVariantService == null) return ApiResponse.failure("Cover editing is unavailable.");
        String imageUrl = coverImageVariantService.createXhsCover(
                publicationId, request.imageUrl(), request.headline(), request.textColor(),
                request.backgroundColor(), request.showTitle(), request.templatePreset(), request.backgroundMode());
        return ApiResponse.success("An editable Xiaohongshu cover was created without changing the original image.",
                java.util.Map.of("imageUrl", imageUrl));
    }
}
