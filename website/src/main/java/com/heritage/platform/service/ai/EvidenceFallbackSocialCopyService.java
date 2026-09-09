package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.CopyQualityReview;
import com.heritage.platform.dto.ai.HookCandidate;
import com.heritage.platform.dto.ai.HookScore;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.SocialCopyAngle;
import com.heritage.platform.dto.ai.SocialCopyEvidenceDigest;
import com.heritage.platform.dto.ai.SocialCopyFinalValidation;
import com.heritage.platform.dto.ai.SocialCopyPipelineArtifacts;
import com.heritage.platform.dto.ai.SocialCopySelectedHook;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceFallbackSocialCopyService {

    private final SocialCopyEvidenceDigestService digestService = new SocialCopyEvidenceDigestService();
    private final SocialCopyFinalValidator finalValidator = new SocialCopyFinalValidator();

    public OpenAiSocialCopyService.GeneratedSocialCopy generate(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language
    ) {
        SocialCopyEvidenceDigest digest = digestService.build(understanding, evidence);
        List<String> hashtags = hashtags(evidence, digest, language);
        Map<String, String> variants = new LinkedHashMap<>();
        if ("en".equalsIgnoreCase(language)) {
            variants.put("engaging", english(understanding, hashtags, false));
            variants.put("concise", english(understanding, hashtags, true));
            variants.put("professional", englishAcademic(understanding, hashtags));
        } else {
            variants.put("engaging", chinese(understanding, hashtags, false));
            variants.put("concise", chinese(understanding, hashtags, true));
            variants.put("professional", chineseAcademic(understanding, hashtags));
        }
        variants.put("academic", variants.get("professional"));
        SocialCopyPipelineArtifacts artifacts = fallbackArtifacts(digest, variants, hashtags, language);
        return new OpenAiSocialCopyService.GeneratedSocialCopy(Map.copyOf(variants), hashtags, artifacts);
    }

    private String chinese(FinalPaperUnderstanding value, List<String> tags, boolean concise) {
        String problem = shortText(firstUsefulChinese("一个需要结合原文进一步确认的实际问题", value.researchProblem(), value.abstractSummary()), 150);
        String system = shortText(firstUsefulChinese("一种面向这一问题的系统或方法", value.proposedSystemOrMethod(), value.method()), 120);
        String workflow = shortText(firstUsefulChinese("提取信息中能够确认的主要任务流程", value.mainTaskOrWorkflow(), value.inputOutputRelationship()), 140);
        String environment = shortText(firstUsefulChinese("论文讨论的应用场景", value.applicationEnvironment(), value.visualizableEnvironment()), 100);
        String why = shortText(firstUsefulChinese("它为理解和处理这一问题提供了更具体的思路", value.whyItMatters(), value.keyContribution(), value.expectedOutcome()), 150);
        String audience = shortText(firstUsefulChinese("关注相关主题的研究者与实践者", value.targetUsersOrDomain()), 90);
        return concise
                ? "📎【论文分享】\n\n🤔 遇到%s时，应该从哪里开始？\n\n🧩 根据目前提取到的信息，论文主要介绍%s。🛠️ 它围绕%s展开。✅ 比较值得关注的是，%s。👀 适合%s阅读。\n\n%s"
                .formatted(problem, system, workflow, why, audience, tagLine(tags))
                : "📎【论文分享】\n\n🤔 你是否也遇到过这样的情况：面对%s时，很想知道有没有更具体的处理思路？\n\n🧩 根据目前提取到的信息，这篇论文介绍了%s，尝试回应这个问题。\n\n🛠️ 怎么用？在%s中，相关人员可以围绕%s开展任务，借此把论文的方法放回实际场景中理解。\n\n✅ 我觉得这篇论文最有意思的地方在于：%s。它没有脱离问题本身，而是提供了一条可以继续对照原文核查的研究路径。\n\n👀 如果你是%s，或正在寻找同类问题的研究思路，这篇论文值得读一读。\n\n%s"
                .formatted(problem, system, environment, workflow, why, audience, tagLine(tags));
    }

    private String chineseAcademic(FinalPaperUnderstanding value, List<String> tags) {
        return "📎【论文分享】\n\n🤔 在%s这一场景中，实践者需要更清楚的问题处理路径。\n\n🧩 根据目前提取到的信息，论文介绍了%s；🛠️ 其使用方式可概括为%s。✅ 值得关注的是%s。👀 适合%s阅读，发布前建议结合原文核对自动提取内容。\n\n%s"
                .formatted(
                        shortText(firstUsefulChinese("论文所关注的实际问题", value.researchProblem(), value.abstractSummary()), 170),
                        shortText(firstUsefulChinese("一种面向该问题的系统或方法", value.proposedSystemOrMethod(), value.method()), 130),
                        shortText(firstUsefulChinese("提取信息中能够确认的主要任务流程", value.mainTaskOrWorkflow(), value.inputOutputRelationship()), 150),
                        shortText(firstUsefulChinese("它为相关任务提供了更具体的研究思路", value.keyContribution(), value.whyItMatters()), 170),
                        shortText(firstUsefulChinese("相关领域研究者与实践者", value.targetUsersOrDomain()), 90),
                        tagLine(tags)
                );
    }

    private String english(FinalPaperUnderstanding value, List<String> tags, boolean concise) {
        String problem = shortText(firstUseful(value.researchProblem(), value.abstractSummary(), "the practical problem described by the available evidence"), 160);
        String system = shortText(firstUseful(value.proposedSystemOrMethod(), value.method(), "the evidence-supported system or method proposed by the paper"), 130);
        String workflow = shortText(firstUseful(value.mainTaskOrWorkflow(), value.inputOutputRelationship(), "the main evidence-supported task workflow"), 150);
        String environment = shortText(firstUseful(value.applicationEnvironment(), value.visualizableEnvironment(), "the evidence-supported application environment"), 100);
        String why = shortText(firstUseful(value.whyItMatters(), value.keyContribution(), "targeted support for the relevant task"), 160);
        String audience = shortText(firstUseful(value.targetUsersOrDomain(), "readers working in this research area"), 100);
        return concise
                ? "📎 Paper share\n\n🤔 Have you faced this problem: %s?\n\n🧩 The paper describes %s. 🛠️ It is used for %s. ✅ It may matter because %s. 👀 Worth reading for %s.\n\n%s"
                .formatted(problem, system, workflow, why, audience, tagLine(tags))
                : "📎 Paper share\n\n🤔 Have you encountered this situation: %s?\n\n🧩 The paper describes %s to address that problem. 🛠️ Available evidence suggests it is used in %s through %s; uncertain technical details should be checked against the paper.\n\n✅ Why it matters: %s.\n\n👀 If you are among %s, this paper may be worth your time.\n\n%s"
                .formatted(problem, system, environment, workflow, why, audience, tagLine(tags));
    }

    private String englishAcademic(FinalPaperUnderstanding value, List<String> tags) {
        return "📎 Paper share\n\n🤔 Problem context: %s. 🧩 The available evidence describes %s, 🛠️ used through %s. ✅ Its potential value is %s. 👀 Recommended for %s, with low-confidence details checked against the original paper.\n\n%s"
                .formatted(
                        shortText(firstUseful(value.researchProblem(), value.abstractSummary(), "the practical problem described by the available evidence"), 180),
                        shortText(firstUseful(value.proposedSystemOrMethod(), value.method(), "the evidence-supported system or method proposed by the paper"), 140),
                        shortText(firstUseful(value.mainTaskOrWorkflow(), value.inputOutputRelationship(), "the main evidence-supported task workflow"), 160),
                        shortText(firstUseful(value.keyContribution(), value.whyItMatters(), "targeted support for the relevant task"), 180),
                        shortText(firstUseful(value.targetUsersOrDomain(), "researchers and practitioners in the area"), 100),
                        tagLine(tags)
                );
    }

    private SocialCopyPipelineArtifacts fallbackArtifacts(
            SocialCopyEvidenceDigest digest,
            Map<String, String> variants,
            List<String> hashtags,
            String language
    ) {
        SocialCopyAngle angle = new SocialCopyAngle(
                digest.problemEvidence(),
                "Use a cautious, relatable problem-first opening grounded in the extracted evidence.",
                digest.proposedSystemEvidence(), digest.usageEvidence(), digest.usageEvidence(),
                digest.valueEvidence(), digest.valueEvidence(), List.of(digest.targetAudienceEvidence()),
                List.of(digest.domain()), hashtags, "LOW",
                List.of("OpenAI was unavailable; this conservative angle was assembled from extracted evidence.")
        );
        List<HookCandidate> hooks = List.of(
                new HookCandidate(
                        "zh".equalsIgnoreCase(language)
                                ? "当你面对" + shortText(digest.problemEvidence(), 100) + "时，会从哪里开始？"
                                : "When you face " + shortText(digest.problemEvidence(), 100) + ", where do you begin?",
                        "question", "Connects the paper to the evidence-supported problem.", digest.problemEvidence()
                ),
                new HookCandidate(
                        "zh".equalsIgnoreCase(language)
                                ? "这个看似熟悉的问题，真正放进实际场景后应该怎样处理？"
                                : "How should this familiar-looking problem be handled in its real setting?",
                        "scenario", "Keeps the opening cautious while introducing the practical setting.", digest.usageEvidence()
                ),
                new HookCandidate(
                        "zh".equalsIgnoreCase(language)
                                ? "技术概念并不难写，难的是它在真实任务里究竟怎么用。"
                                : "A technical concept is easy to name; the harder question is how it works in a real task.",
                        "contrast", "Leads naturally into the evidence-supported usage workflow.", digest.usageEvidence()
                )
        );
        List<HookScore> scores = List.of(
                new HookScore(0, 4, 4, 3, 4, 3, 4, 3.67, "Best conservative evidence-grounded hook."),
                new HookScore(1, 3, 3, 3, 3, 3, 4, 3.17, "Readable but less specific."),
                new HookScore(2, 3, 3, 3, 3, 3, 4, 3.17, "Useful contrast but less directly tied to the problem.")
        );
        SocialCopySelectedHook selected = new SocialCopySelectedHook(
                hooks.getFirst(), 0, scores, false,
                "Selected deterministically because OpenAI hook scoring was unavailable."
        );
        CopyQualityReview review = new CopyQualityReview(
                3, 4, 3, 4, 3, 4, 4, 4, 5, 5, 3,
                List.of("OpenAI quality review was unavailable."),
                List.of("Manually check the conservative copy against the original paper before publishing."),
                false
        );
        SocialCopyFinalValidation validation = finalValidator.validate(
                variants.get("engaging"), language, digest, false
        );
        return new SocialCopyPipelineArtifacts(
                digest, angle, hooks, selected, variants, review, validation,
                variants, hashtags, false, false
        );
    }

    private List<String> hashtags(PaperEvidencePacket evidence, SocialCopyEvidenceDigest digest, String language) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        List<String> candidates = new ArrayList<>();
        if (evidence != null) {
            candidates.addAll(evidence.keywords());
            candidates.addAll(evidence.subjectTerms());
            candidates.addAll(evidence.domainTerms());
        }
        if (digest != null) {
            candidates.addAll(digest.hashtagCandidates());
        }
        for (String candidate : candidates) {
            String tag = candidate == null ? "" : candidate.replaceAll("[^\\p{L}\\p{N}_-]", "").trim();
            if ("en".equalsIgnoreCase(language) && containsChinese(tag)) {
                continue;
            }
            if (tag.length() >= 2 && tag.length() <= 24) {
                values.add(tag);
            }
            if (values.size() >= 5) {
                break;
            }
        }
        List<String> fallbackTags = "en".equalsIgnoreCase(language)
                ? List.of("PaperReview", "ResearchCommunication", "AcademicResearch")
                : List.of("论文分享", "研究方法", "学术阅读");
        for (String fallback : fallbackTags) {
            if (values.size() >= 3) {
                break;
            }
            values.add(fallback);
        }
        return values.stream().limit(6).toList();
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints()
                .anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN);
    }

    private String tagLine(List<String> hashtags) {
        return hashtags.stream().map(value -> "#" + value)
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"not clearly specified".equalsIgnoreCase(value.trim())) {
                String cleaned = value.replaceAll("\\s+", " ").trim();
                if (!containsEllipsis(cleaned) && !endsAsTruncatedSnippet(cleaned)) {
                    return cleaned;
                }
            }
        }
        return "the evidence-supported research context described in the paper";
    }

    private String firstUsefulChinese(String fallback, String... values) {
        for (String value : values) {
            if (value == null || value.isBlank() || "not clearly specified".equalsIgnoreCase(value.trim())) {
                continue;
            }
            String cleaned = value.replaceAll("\\s+", " ").trim();
            if (containsEllipsis(cleaned) || endsAsTruncatedSnippet(cleaned)) {
                continue;
            }
            if (cleaned.matches("(?i)^design of [^:.;]{2,80}:.*")) {
                return cleaned.replaceFirst("(?i)^design of ([^:.;]{2,80}):.*$", "$1").trim();
            }
            boolean containsChinese = cleaned.codePoints().anyMatch(codePoint ->
                    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
            );
            long englishWordCount = List.of(cleaned.split("\\s+")).stream()
                    .filter(word -> word.matches(".*[A-Za-z].*"))
                    .count();
            if (containsChinese || cleaned.length() <= 80 && englishWordCount <= 10) {
                return cleaned;
            }
        }
        return fallback;
    }

    private String shortText(String value, int limit) {
        String cleaned = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= limit) {
            return cleaned;
        }
        int minimumBoundary = Math.max(1, limit / 2);
        for (int index = Math.min(limit, cleaned.length() - 1); index >= minimumBoundary; index--) {
            if ("。！？.!?".indexOf(cleaned.charAt(index)) >= 0) {
                return cleaned.substring(0, index + 1).trim();
            }
        }
        int maximumBoundary = Math.min(cleaned.length() - 1, limit * 2);
        for (int index = Math.min(limit + 1, maximumBoundary); index <= maximumBoundary; index++) {
            if ("。！？.!?".indexOf(cleaned.charAt(index)) >= 0) {
                return cleaned.substring(0, index + 1).trim();
            }
        }
        return cleaned;
    }

    private boolean containsEllipsis(String value) {
        return value.contains("…") || value.matches("(?s).*\\.{3,}.*");
    }

    private boolean endsAsTruncatedSnippet(String value) {
        return value.matches("(?s).*[,:;，：；/\\-(\\[]$");
    }
}
