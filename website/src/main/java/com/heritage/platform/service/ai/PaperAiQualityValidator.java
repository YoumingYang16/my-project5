package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PaperAiQualityValidator {

    public static final String INSUFFICIENT_EVIDENCE_MESSAGE =
            "Reliable paper understanding cannot be generated because paper content extraction is insufficient. "
                    + "Ensure GROBID can extract the full PDF, then retry.";
    public static final String AUTHORITATIVE_UNDERSTANDING_MESSAGE =
            "Reliable AI output requires a successful OpenAI paper understanding. Please check OpenAI and retry.";

    private static final Set<String> PLACEHOLDERS = Set.of(
            "test", "test1", "sample", "placeholder", "untitled", "unknown", "n/a", "none",
            "not specified", "not clearly specified", "the paper", "the proposed system", "lorem ipsum"
    );
    private static final Pattern PROPOSAL_SIGNAL = Pattern.compile(
            "(?iu)\\b(propose[sd]?|present[sd]?|introduce[sd]?|develop(?:ed|s)?|design(?:ed|s)?|"
                    + "implement(?:ed|s)?|build(?:s|ing|t)?|framework|architecture|platform|prototype|"
                    + "system|method|model|tool|interface|algorithm|application|workflow)\\b"
                    + "|提出|设计|开发|构建|实现|系统|方法|模型|工具|平台|框架|界面|算法");

    public void requireSufficientEvidence(PaperEvidencePacket evidence) {
        if (!hasUsableEvidence(evidence)) {
            throw new AiCoverWorkflowException(INSUFFICIENT_EVIDENCE_MESSAGE);
        }
    }

    public void requireUsableEvidence(PaperEvidencePacket evidence) {
        requireSufficientEvidence(evidence);
    }

    public boolean hasUsableEvidence(PaperEvidencePacket evidence) {
        if (evidence == null || containsTestPlaceholder(evidence.compactEvidenceText())) {
            return false;
        }
        List<String> values = new ArrayList<>();
        values.add(evidence.title());
        values.add(evidence.abstractText());
        values.add(evidence.doi());
        values.add(evidence.publisher());
        values.add(evidence.citationMetadata());
        values.addAll(merge(
                evidence.problemContextSnippets(), evidence.methodDesignSnippets(),
                evidence.implementationSnippets(), evidence.evaluationResultSnippets(),
                evidence.conclusionSnippets(), evidence.applicationClues(), evidence.visualClues()
        ));
        int meaningfulCharacters = values.stream()
                .filter(this::isMeaningful)
                .mapToInt(value -> value.trim().length())
                .sum();
        return meaningfulCharacters >= 40 && !evidence.compactEvidenceText().isBlank();
    }

    public boolean hasSufficientEvidence(PaperEvidencePacket evidence) {
        return hasUsableEvidence(evidence);
    }

    public void requireAuthoritativeOpenAi(PaperUnderstandingResult understanding) {
        if (!isAuthoritativeOpenAi(understanding)) {
            throw new AiCoverWorkflowException(AUTHORITATIVE_UNDERSTANDING_MESSAGE);
        }
    }

    public boolean isAuthoritativeOpenAi(PaperUnderstandingResult understanding) {
        if (understanding == null
                || !"COMPLETED".equalsIgnoreCase(understanding.status())
                || !"OPENAI".equalsIgnoreCase(understanding.understandingSource())
                || understanding.finalUnderstanding() == null) {
            return false;
        }
        FinalPaperUnderstanding value = understanding.finalUnderstanding();
        String environment = firstUseful(value.applicationEnvironment(), value.visualizableEnvironment());
        if (!isMeaningful(value.proposedSystemOrMethod())
                || !isMeaningful(environment)
                || value.mustShowElements().stream().filter(this::isMeaningful).distinct().count() < 3) {
            return false;
        }

        List<String> semanticFields = List.of(
                safe(value.researchProblem()), safe(value.proposedSystemOrMethod()), safe(value.method()),
                safe(value.keyImplementation()), safe(value.keyContribution()), safe(value.targetUsersOrDomain()),
                safe(environment), safe(value.mainTaskOrWorkflow()), safe(value.expectedOutcome())
        );
        if (semanticFields.stream().anyMatch(this::containsTestPlaceholder)) {
            return false;
        }
        long usefulCount = semanticFields.stream().filter(this::isMeaningful).count();
        long titleCopies = semanticFields.stream().filter(field -> sameText(field, value.title())).count();
        return usefulCount >= 6 && titleCopies * 2 < usefulCount;
    }

    public void requireUsableUnderstanding(PaperUnderstandingResult understanding) {
        if (!isUsableUnderstanding(understanding)) {
            throw new AiCoverWorkflowException("No usable paper understanding could be produced from the available evidence.");
        }
    }

    public boolean isUsableUnderstanding(PaperUnderstandingResult understanding) {
        if (isAuthoritativeOpenAi(understanding)) {
            return true;
        }
        if (understanding == null
                || !"COMPLETED".equalsIgnoreCase(understanding.status())
                || !"EVIDENCE_FALLBACK".equalsIgnoreCase(understanding.understandingSource())
                || understanding.finalUnderstanding() == null) {
            return false;
        }
        FinalPaperUnderstanding value = understanding.finalUnderstanding();
        return "LOW".equalsIgnoreCase(value.confidenceLevel())
                && isMeaningful(value.title())
                && isMeaningful(value.proposedSystemOrMethod())
                && !containsTestPlaceholder(value.researchProblem())
                && !containsTestPlaceholder(value.proposedSystemOrMethod());
    }

    public boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = normalize(value);
        return PLACEHOLDERS.contains(normalized)
                || containsTestPlaceholder(normalized)
                || normalized.matches("(?:sample|test|placeholder)[-_ ]*\\d*");
    }

    public boolean containsTestPlaceholder(String value) {
        if (value == null) {
            return false;
        }
        String normalized = normalize(value);
        return normalized.matches(".*(?:^|[^\\p{L}\\p{N}])test1(?:$|[^\\p{L}\\p{N}]).*")
                || normalized.equals("test1");
    }

    public boolean isMeaningful(String value) {
        return value != null && value.trim().length() >= 4 && !isPlaceholder(value);
    }

    private int meaningfulLength(String value) {
        return isMeaningful(value) ? value.trim().length() : 0;
    }

    private boolean sameText(String left, String right) {
        return isMeaningful(left) && isMeaningful(right) && normalize(left).equals(normalize(right));
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (isMeaningful(value)) {
                return value.trim();
            }
        }
        return null;
    }

    @SafeVarargs
    private final List<String> merge(List<String>... lists) {
        List<String> values = new ArrayList<>();
        for (List<String> list : lists) {
            if (list != null) {
                list.stream().filter(this::isMeaningful).map(String::trim).forEach(values::add);
            }
        }
        return values;
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
