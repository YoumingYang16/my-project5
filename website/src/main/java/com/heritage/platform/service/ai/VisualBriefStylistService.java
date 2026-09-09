package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.StyledImagePrompt;
import com.heritage.platform.dto.ai.VisualBriefPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VisualBriefStylistService {

    @Value("${comfyui.default-width:1024}")
    private int defaultWidth;

    @Value("${comfyui.default-height:768}")
    private int defaultHeight;

    public StyledImagePrompt style(VisualBriefPlan plan) {
        List<String> styleKeywords = List.of(
                "realistic application-scene cover image",
                "documentary product-in-use visual",
                "paper contribution visibly operating in context",
                "high visual clarity",
                "minimal text"
        );
        String positivePrompt = String.join(", ",
                "APPLICATION_SCENE publication cover",
                "realistic documentary-style application visual",
                "show the proposed contribution being applied or demonstrated in a paper-supported context",
                "wide publication card cover",
                "clear central subject: " + plan.mainVisualSubject(),
                "show the core implementation: " + plan.coreImplementationToShow(),
                "reader takeaway: " + plan.readerTakeawayIn3Seconds(),
                "scene or metaphor: " + plan.sceneOrMetaphor(),
                "foreground elements: " + String.join(", ", plan.foregroundElements()),
                "background elements: " + String.join(", ", plan.backgroundElements()),
                "technical hints: " + String.join(", ", plan.technicalElementsToHint()),
                "strong composition",
                "visually grounded in the paper content",
                "the proposed system, method, product, tool, model, output, or interaction must be visibly central",
                "little or no readable text inside the image",
                "not decorative topic scenery"
        );
        String negativePrompt = String.join(", ",
                "pure academic poster",
                "pure infographic",
                "topic-only concept image",
                "decorative domain scenery without the proposed contribution in use",
                "cluttered layout",
                "excessive text",
                "unreadable labels",
                "boring flowchart",
                "generic abstract background",
                "random icons",
                "distorted UI",
                "fake equations",
                "fake logos",
                "irrelevant objects",
                "messy diagram",
                "low resolution",
                "watermark",
                "blurry",
                "bad composition",
                "stock photo look",
                "meaningless technology symbols",
                String.join(", ", plan.elementsToAvoid())
        );
        return new StyledImagePrompt(
                positivePrompt,
                negativePrompt,
                styleKeywords,
                plan.composition(),
                defaultWidth <= 0 ? 1024 : defaultWidth,
                defaultHeight <= 0 ? 768 : defaultHeight
        );
    }
}
