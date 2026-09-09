package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.AiCoverCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LegacyComfyUiImageProvider implements PublicationImageProvider {

    private final ComfyUiImageGenerationService comfyUiImageGenerationService;

    @Value("${comfyui.enabled:true}")
    private boolean comfyUiEnabled;

    public LegacyComfyUiImageProvider(ComfyUiImageGenerationService comfyUiImageGenerationService) {
        this.comfyUiImageGenerationService = comfyUiImageGenerationService;
    }

    @Override
    public String providerName() {
        return "legacy-comfyui";
    }

    @Override
    public boolean enabled() {
        return comfyUiEnabled;
    }

    @Override
    public List<AiCoverCandidate> generate(PublicationImageGenerationRequest request) {
        return comfyUiImageGenerationService.generateCandidates(
                request.publicationId(),
                request.styledPrompt(),
                request.positivePrompt(),
                request.negativePrompt()
        );
    }
}
