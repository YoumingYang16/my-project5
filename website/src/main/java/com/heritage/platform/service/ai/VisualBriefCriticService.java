package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.PromptCritiqueResult;
import com.heritage.platform.dto.ai.StyledImagePrompt;
import com.heritage.platform.dto.ai.VisualBriefPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class VisualBriefCriticService {

    public PromptCritiqueResult critique(VisualBriefPlan plan, StyledImagePrompt styledPrompt) {
        List<String> problems = new ArrayList<>();
        String positive = styledPrompt.positivePrompt();
        String negative = styledPrompt.negativePrompt();
        String lower = positive.toLowerCase(Locale.ROOT);

        if (!lower.contains(cleanNeedle(plan.mainVisualSubject()))) {
            problems.add("Prompt does not clearly mention the main visual subject.");
            positive += ", central visual subject must be " + plan.mainVisualSubject();
        }
        if (!lower.contains("little or no readable text")) {
            problems.add("Prompt needs a stronger instruction to avoid text-heavy output.");
            positive += ", no captions, no labels, no paragraphs inside the image";
        }
        if (!negative.toLowerCase(Locale.ROOT).contains("boring flowchart")) {
            problems.add("Negative prompt should reject flowchart-like output.");
            negative += ", boring flowchart";
        }
        if (positive.length() < 300) {
            problems.add("Prompt is too thin for a specific paper teaser.");
            positive += ", concrete visual details, polished editorial lighting, balanced foreground and background";
        }

        return new PromptCritiqueResult(
                problems.isEmpty(),
                problems,
                positive,
                negative
        );
    }

    private String cleanNeedle(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] words = value.toLowerCase(Locale.ROOT).split("\\s+");
        return words.length == 0 ? "" : words[0];
    }
}
