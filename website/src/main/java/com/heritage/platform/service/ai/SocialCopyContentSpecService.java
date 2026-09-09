package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.SocialCopyContentPlan;
import com.heritage.platform.dto.ai.SocialCopyContentSpec;
import com.heritage.platform.dto.ai.SocialCopyEvidenceDigest;
import com.heritage.platform.dto.ai.SocialCopyGenerationRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SocialCopyContentSpecService {

    public SocialCopyContentSpec normalize(SocialCopyGenerationRequest request, String language, String tone) {
        String mode = oneOf(request == null ? null : request.mode(), "personal", "outreach", "personal");
        return new SocialCopyContentSpec(
                oneOf(request == null ? null : request.audience(), "public", "student", "researcher", "practitioner", "public"),
                oneOf(request == null ? null : request.goal(), "science_education", "visibility", "read_more", "engagement", "collaboration", "science_education"),
                "en".equalsIgnoreCase(language) ? "en" : "zh",
                oneOf(tone, "engaging", "concise", "professional", "engaging"),
                oneOf(request == null ? null : request.length(), "short", "standard", "long", "standard"),
                clean(request == null ? null : request.callToAction()),
                SocialCopyContentSpec.XIAOHONGSHU,
                mode
        );
    }

    public SocialCopyContentPlan plan(SocialCopyContentSpec spec, SocialCopyEvidenceDigest digest) {
        String audience = switch (spec.audience()) {
            case "student" -> "用通俗语言解释必要术语，并先说明研究问题为何与学习或实践有关";
            case "researcher" -> "保留研究方法、比较边界和不确定性，不把结果写成营销结论";
            case "practitioner" -> "强调可落地的使用场景、工作流程和适用边界";
            default -> "从普通读者能理解的真实问题切入，首次出现术语时给出简短解释";
        };
        String goal = switch (spec.goal()) {
            case "visibility" -> "突出论文最值得关注的、可由原文支持的贡献，并邀请收藏或讨论";
            case "read_more" -> "用一个证据支持的发现制造阅读兴趣，CTA 引导查看原论文";
            case "engagement" -> "用可回答的问题收尾，鼓励读者分享类似经历，但不诱导夸大";
            case "collaboration" -> "说明适合哪些研究者或实践者关注，并邀请基于论文边界进行交流";
            default -> "优先解释研究问题、方法和为什么值得关注，避免营销化夸张";
        };
        String cta = spec.callToAction() == null ? defaultCta(spec.goal(), spec.language()) : spec.callToAction();
        return new SocialCopyContentPlan(
                audience,
                goal,
                List.of(nonBlank(digest.problemEvidence()), nonBlank(digest.proposedSystemEvidence()), nonBlank(digest.valueEvidence())),
                List.of("未经证据支持的数字、比较、因果、首次或最佳表述", "把论文结论写成已验证的现实效果"),
                cta
        );
    }

    private String defaultCta(String goal, String language) {
        if ("en".equals(language)) {
            return "engagement".equals(goal) ? "What question would you ask the authors?" : "Save this post and read the paper for the full evidence.";
        }
        return "engagement".equals(goal) ? "你最想进一步了解论文里的哪一部分？欢迎留言讨论。" : "如果这项研究对你有启发，建议收藏并查看原论文。";
    }

    private String oneOf(String value, String... allowedAndDefault) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (int index = 0; index < allowedAndDefault.length - 1; index++) {
            if (allowedAndDefault[index].equals(normalized)) {
                return normalized;
            }
        }
        return allowedAndDefault[allowedAndDefault.length - 1];
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned.substring(0, Math.min(160, cleaned.length()));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? "论文中可核查的关键证据" : value;
    }
}
