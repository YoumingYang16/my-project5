package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.ApplicationSceneBrief;
import com.heritage.platform.dto.ai.AiCoverGenerationOptions;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.ScenarioImagePrompt;
import com.heritage.platform.dto.ai.StyledImagePrompt;

import java.nio.file.Path;

public record PublicationImageGenerationRequest(
        Long publicationId,
        String title,
        String abstractText,
        Path pdfPath,
        PaperEvidencePacket evidencePacket,
        FinalPaperUnderstanding understanding,
        ApplicationSceneBrief sceneBrief,
        ScenarioImagePrompt scenarioPrompt,
        StyledImagePrompt styledPrompt,
        String positivePrompt,
        String negativePrompt,
        AiCoverGenerationOptions options
) {
    public PublicationImageGenerationRequest(Long publicationId, String title, String abstractText, Path pdfPath, PaperEvidencePacket evidencePacket, FinalPaperUnderstanding understanding, ApplicationSceneBrief sceneBrief, ScenarioImagePrompt scenarioPrompt, StyledImagePrompt styledPrompt, String positivePrompt, String negativePrompt) {
        this(publicationId, title, abstractText, pdfPath, evidencePacket, understanding, sceneBrief, scenarioPrompt, styledPrompt, positivePrompt, negativePrompt, AiCoverGenerationOptions.defaults());
    }
}