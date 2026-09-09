package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.SocialCopyContentPlan;
import com.heritage.platform.dto.ai.SocialCopyContentSpec;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekSocialCopyService {

    private static final String SYSTEM_PROMPT = """
            You are a research communication editor and Xiaohongshu / WeChat-style paper recommendation copywriter.

            Your task is not to summarize the abstract.
            Your task is to transform the paper evidence into an attractive, grounded, reader-friendly recommendation post.

            Use this structure:
            relatable problem -> paper's system/method -> how it is used -> why it matters -> who should read it

            Requirements:
            - start from a relatable real pain point or scenario
            - introduce the actual system, method, tool, or product from the paper
            - explain how it is used
            - explain why it is interesting or valuable
            - mention who should read it
            - use light emojis naturally
            - stay positive and attractive while remaining grounded in evidence
            - do not invent metrics, user study results, deployments, awards, or unsupported claims
            - no placeholders, no ellipses, no test1, and no truncated fragments
            - do not open with abstract-style wording such as “本文研究了”
            - return one valid JSON object with angle, hookCandidates, selectedHook, variants,
              qualityReview, finalCopy, hashtags, and warnings
            """;

    private final OpenAiSocialCopyService pipelineService;
    private final DeepSeekStructuredResponseClient client;

    public DeepSeekSocialCopyService(
            OpenAiSocialCopyService pipelineService,
            DeepSeekStructuredResponseClient client
    ) {
        this.pipelineService = pipelineService;
        this.client = client;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    public OpenAiSocialCopyService.GeneratedSocialCopy generate(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language,
            SocialCopyContentSpec contentSpec,
            SocialCopyContentPlan contentPlan
    ) {
        return pipelineService.generateCompact(
                understanding, evidence, language, client, systemPrompt(contentSpec, contentPlan)
        );
    }

    private String systemPrompt(SocialCopyContentSpec spec, SocialCopyContentPlan plan) {
        SocialCopyContentSpec safeSpec = spec == null
                ? new SocialCopyContentSpec("public", "science_education", "zh", "engaging", "standard", null,
                SocialCopyContentSpec.XIAOHONGSHU, "outreach")
                : spec;
        SocialCopyContentPlan safePlan = plan == null
                ? new SocialCopyContentPlan("Use clear language.", "Explain the paper accurately.",
                java.util.List.of(), java.util.List.of(), "Read the paper for the full evidence.")
                : plan;
        return SYSTEM_PROMPT + """

                Audience and communication-goal controls are mandatory for every edition, not decorative:
                Selected audience: %s
                Selected communication goal: %s
                Audience guidance: %s
                Goal guidance: %s
                Required call to action: %s
                Requested length: %s
                Required output language: %s

                The complete finalCopy and all variants MUST use the required output language. Do not mix Chinese
                and English, except for proper nouns, cited system names, and original paper titles.

                Adapt the vocabulary, opening situation, explanation depth, value framing, and closing CTA to this
                exact audience and goal. Do not merely append the selected audience or goal as a label. The final
                copy must make the adaptation visible in its wording and structure while preserving every research
                boundary and limitation supported by the supplied evidence.

                Edition direction: %s
                Follow this direction without weakening the required audience and goal adaptation.
                """.formatted(
                audienceLabel(safeSpec.audience()),
                goalLabel(safeSpec.goal()),
                safePlan.audienceGuidance(),
                safePlan.goalGuidance(),
                safePlan.callToAction(),
                lengthLabel(safeSpec.length(), safeSpec.language()),
                "en".equalsIgnoreCase(safeSpec.language()) ? "English" : "natural Simplified Chinese",
                editionDirection(safeSpec.mode())
        );
    }

    private String editionDirection(String mode) {
        return "personal".equals(mode)
                ? "Write with a modest first-person editorial voice, but still address the selected audience and goal."
                : "Write as a clear, audience-specific science communication post intended for public sharing.";
    }

    private String audienceLabel(String audience) {
        return switch (audience == null ? "" : audience) {
            case "student" -> "students and early-career learners";
            case "researcher" -> "researchers and academic peers";
            case "practitioner" -> "industry practitioners and decision-makers";
            default -> "the general public";
        };
    }

    private String goalLabel(String goal) {
        return switch (goal == null ? "" : goal) {
            case "visibility" -> "increase awareness of the paper's evidence-supported contribution";
            case "read_more" -> "motivate readers to consult the original paper";
            case "engagement" -> "invite a specific, answerable discussion";
            case "collaboration" -> "invite relevant, evidence-bounded collaboration or exchange";
            default -> "explain the research clearly for science communication";
        };
    }

    private String lengthLabel(String length, String language) {
        boolean english = "en".equalsIgnoreCase(language);
        return switch (length == null ? "" : length) {
            case "short" -> english ? "short post: 90-150 English words excluding hashtags" : "short post: 140-220 Chinese characters excluding hashtags";
            case "long" -> english ? "detailed post: 260-420 English words excluding hashtags" : "detailed post: 420-650 Chinese characters excluding hashtags";
            default -> english ? "standard post: 160-260 English words excluding hashtags" : "standard post: 260-420 Chinese characters excluding hashtags";
        };
    }
}
