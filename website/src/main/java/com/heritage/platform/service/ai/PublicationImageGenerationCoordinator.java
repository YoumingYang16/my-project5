package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.AiCoverCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PublicationImageGenerationCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(PublicationImageGenerationCoordinator.class);

    private final RendererApiImageProvider rendererApiImageProvider;
    private final LegacyComfyUiImageProvider legacyComfyUiImageProvider;

    @Value("${ai-cover.image-provider:renderer-api}")
    private String preferredProvider;

    @Value("${ai-cover.legacy-comfyui-fallback:true}")
    private boolean legacyFallbackEnabled;

    public PublicationImageGenerationCoordinator(
            RendererApiImageProvider rendererApiImageProvider,
            LegacyComfyUiImageProvider legacyComfyUiImageProvider
    ) {
        this.rendererApiImageProvider = rendererApiImageProvider;
        this.legacyComfyUiImageProvider = legacyComfyUiImageProvider;
    }

    public List<AiCoverCandidate> generate(PublicationImageGenerationRequest request) {
        if (!"renderer-api".equalsIgnoreCase(preferredProvider)) {
            return legacyComfyUiImageProvider.generate(request);
        }
        try {
            if (!rendererApiImageProvider.enabled()) {
                throw new AiCoverWorkflowException("renderer-api is disabled.");
            }
            return rendererApiImageProvider.generate(request);
        } catch (RuntimeException rendererFailure) {
            if (isDoubaoGenerationFailure(rendererFailure)) {
                throw rendererFailure;
            }
            if (!legacyFallbackEnabled || !legacyComfyUiImageProvider.enabled()) {
                throw rendererFailure;
            }
            logger.warn(
                    "renderer-api failed for publicationId={}; falling back to legacy ComfyUI: {}",
                    request.publicationId(),
                    AiCoverDiagnostics.safeExceptionSummary(rendererFailure)
            );
            return legacyComfyUiImageProvider.generate(request);
        }
    }
    private boolean isDoubaoGenerationFailure(RuntimeException failure) {
        String message = failure.getMessage();
        return message != null && message.contains("DOUBAO_IMAGE_GENERATION_FAILED");
    }
}
