package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.SocialCopyEvidenceDigest;
import com.heritage.platform.dto.ai.SocialCopyFinalValidation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SocialCopyFinalValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])(?:test1?|unknown|untitled|n/?a|null|placeholder|lorem ipsum)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern BRACKET_PLACEHOLDER = Pattern.compile(
            "\\[(?!\\d+])[^]\\r\\n]{1,80}]"
    );
    private static final Pattern TRUNCATED_ENGLISH = Pattern.compile(
            "(?is).*\\b(?:and|or|the|a|an|of|to|with|through|by|for|in|from|that|which)\\s*[.,:;!?，。；：！？]*$"
    );
    private static final Pattern ABSTRACT_OPENING = Pattern.compile(
            "(?is)^(?:本文|本研究|该研究)(?:旨在|研究了|探讨了|提出了)|^(?:abstract|this (?:paper|study) (?:investigates|examines|proposes|presents))\\b"
    );
    private static final Pattern UNSUPPORTED_PROMOTION = Pattern.compile(
            "(?i)(?:首次|全球领先|行业第一|彻底解决|保证|显著提升\\s*\\d+%|用户研究证明|state[- ]of[- ]the[- ]art|best[- ]in[- ]class|guarantees?)"
    );

    public SocialCopyFinalValidation validate(
            String copy,
            String language,
            SocialCopyEvidenceDigest digest,
            boolean repairAttempted
    ) {
        List<String> issues = new ArrayList<>();
        String value = copy == null ? "" : copy.trim();
        if (value.isBlank()) {
            issues.add("Final copy is empty.");
            return new SocialCopyFinalValidation(false, issues, 0, repairAttempted);
        }
        if (PLACEHOLDER.matcher(value).find() || BRACKET_PLACEHOLDER.matcher(value).find()) {
            issues.add("Placeholder text remains in the final copy.");
        }
        if (containsEllipsis(value)) {
            issues.add("Ellipses are not allowed in the final copy.");
        }
        if (containsTruncatedEnglish(value)) {
            issues.add("The final copy appears to contain a truncated English fragment.");
        }
        if (!hasProblemHook(value)) {
            issues.add("A relatable problem or scenario hook is missing.");
        }
        if (!hasProposedSystemSection(value)) {
            issues.add("The proposed system or method section is missing.");
        }
        if (!hasUsageSection(value)) {
            issues.add("The explanation of how the system or method is used is missing.");
        }
        if (!hasValueSection(value)) {
            issues.add("The value explanation is missing.");
        }
        if (!hasReaderSection(value)) {
            issues.add("The target-reader section is missing.");
        }
        if (!hasHashtags(value)) {
            issues.add("Topic hashtags are missing.");
        }
        if (!hasLightEmojis(value, language)) {
            issues.add("Light structural emojis are missing.");
        }
        if (abstractLikeOpening(value)) {
            issues.add("The opening sounds like a paper abstract rather than a recommendation post.");
        }
        if (unsupportedPromotionalClaim(value, digest)) {
            issues.add("The copy contains a strong promotional claim that is not supported by the evidence digest.");
        }
        if ("en".equalsIgnoreCase(language) && mostlyChinese(value)) {
            issues.add("The requested English copy is predominantly Chinese.");
        }
        if ("zh".equalsIgnoreCase(language) && !containsChinese(value)) {
            issues.add("The requested Chinese copy does not contain Chinese text.");
        }
        int contentLength = contentCharacterCount(value);
        if ("zh".equalsIgnoreCase(language) && contentLength < 120) {
            issues.add("The engaging Chinese copy is too short to contain the required grounded structure.");
        }
        if (contentLength > 1000) {
            issues.add("The final copy is too long for the requested social format.");
        }
        return new SocialCopyFinalValidation(issues.isEmpty(), issues, contentLength, repairAttempted);
    }

    private boolean containsEllipsis(String value) {
        return value.contains("…") || value.matches("(?s).*\\.{3,}.*");
    }

    private boolean containsTruncatedEnglish(String value) {
        String withoutTags = value.replaceAll("(?m)(?:^|\\s)#[\\p{L}\\p{N}_-]+", " ").trim();
        return TRUNCATED_ENGLISH.matcher(withoutTags).matches()
                || withoutTags.matches("(?is).*\\b(?:and|or|the|a|an|of|to|with|through|by|for|in|from)\\s*\\R.*")
                || withoutTags.matches("(?s).*[,:;，：；/\\-(\\[]$");
    }

    private boolean hasProblemHook(String value) {
        return value.contains("🤔") || value.matches("(?is).*(?:你是否|你有没有|有没有遇到|想象一下|当你|have you|ever wondered|when you|what if).*?");
    }

    private boolean hasProposedSystemSection(String value) {
        return value.contains("🧩") || value.matches("(?is).*(?:论文|paper).*(?:系统|方法|工具|框架|界面|system|method|tool|framework|interface).*?");
    }

    private boolean hasUsageSection(String value) {
        return value.contains("🛠") || value.matches("(?is).*(?:怎么用|如何使用|使用者|用户可以|工作流|how (?:it|to) use|users? (?:can|use)|workflow).*?");
    }

    private boolean hasValueSection(String value) {
        return value.contains("✅") || value.matches("(?is).*(?:价值|有意思|值得关注|帮助|why it matters|valuable|interesting).*?");
    }

    private boolean hasReaderSection(String value) {
        return value.contains("👀") || value.matches("(?is).*(?:适合.*(?:读者|研究者|实践者)|如果你关注|值得.*(?:读|看)|who should read|worth reading for|recommended for).*?");
    }

    private boolean hasHashtags(String value) {
        return Pattern.compile("(?U)(?:^|\\s)#[\\p{L}\\p{N}_-]{2,}").matcher(value).find();
    }

    private boolean hasLightEmojis(String value, String language) {
        long count = List.of("📎", "🤔", "🧩", "🛠", "✅", "👀").stream().filter(value::contains).count();
        return "zh".equalsIgnoreCase(language) ? count >= 1 : count >= 1;
    }

    private boolean abstractLikeOpening(String value) {
        String opening = value.replaceFirst("(?s)^\\s*(?:📎)?\\s*(?:【论文分享】|Paper share)?\\s*", "").trim();
        opening = opening.replaceFirst("^[🤔🧩\\s]+", "").trim();
        return ABSTRACT_OPENING.matcher(opening.substring(0, Math.min(opening.length(), 180))).find();
    }

    private boolean unsupportedPromotionalClaim(String value, SocialCopyEvidenceDigest digest) {
        var matcher = UNSUPPORTED_PROMOTION.matcher(value);
        if (!matcher.find()) {
            return false;
        }
        String evidence = digest == null ? "" : String.join(" ", List.of(
                safe(digest.problemEvidence()), safe(digest.proposedSystemEvidence()),
                safe(digest.usageEvidence()), safe(digest.valueEvidence())
        )).toLowerCase(Locale.ROOT);
        return !evidence.contains(matcher.group().toLowerCase(Locale.ROOT));
    }

    private int contentCharacterCount(String value) {
        return value.replaceAll("(?m)(?:^|\\s)#[\\p{L}\\p{N}_-]+", "")
                .replaceAll("\\s+", "")
                .codePointCount(0, value.replaceAll("(?m)(?:^|\\s)#[\\p{L}\\p{N}_-]+", "")
                        .replaceAll("\\s+", "").length());
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints()
                .anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN);
    }

    private boolean mostlyChinese(String value) {
        long han = value.codePoints()
                .filter(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN)
                .count();
        long letters = value.codePoints().filter(Character::isLetter).count();
        return han > 12 && han * 5 > Math.max(1, letters);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
