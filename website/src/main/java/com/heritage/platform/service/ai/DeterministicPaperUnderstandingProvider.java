package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class DeterministicPaperUnderstandingProvider implements PaperUnderstandingProvider {

    public static final String FALLBACK_WARNING =
            "OpenAI was unavailable, so this result was generated from GROBID/DOI/PDF evidence only.";

    private static final List<String> PROPOSAL_CUES = List.of(
            "we propose", "we present", "we introduce", "we develop", "we design", "we implement",
            "this paper proposes", "this work presents", "framework", "method", "model", "system",
            "tool", "platform", "prototype", "algorithm", "architecture"
    );
    private static final List<String> USE_CUES = List.of(
            "user", "participant", "operator", "stakeholder", "practitioner", "client", "workflow",
            "interact", "use", "perform", "apply", "deploy", "input", "output", "interface", "task"
    );

    @Override
    public String providerName() {
        return "EVIDENCE_FALLBACK";
    }

    @Override
    public String modelName() {
        return "evidence-fallback-v2";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public OpenAiPaperUnderstanding understand(PaperEvidencePacket evidence) {
        if (evidence == null) {
            throw new AiCoverWorkflowException("Deterministic paper understanding requires a compact evidence packet.");
        }
        String title = firstUseful(evidence.title(), "Research paper represented from available evidence");
        List<String> methodEvidence = merge(
                evidence.implementationSnippets(), evidence.methodDesignSnippets(), evidence.applicationClues()
        );
        String proposed = firstUseful(
                best(methodEvidence, PROPOSAL_CUES),
                "an evidence-grounded research system or method with uncertain technical details"
        );
        String researchProblem = firstUseful(first(evidence.problemContextSnippets()), evidence.abstractText(), evidence.title());
        String targetOrDomain = firstUseful(
                best(evidence.applicationClues(), USE_CUES), first(evidence.domainTerms()), "the application domain described by the paper"
        );
        String environment = firstUseful(
                best(merge(evidence.evaluationResultSnippets(), evidence.applicationClues(), evidence.methodDesignSnippets()),
                        List.of(" in ", " at ", " within ", " during ", " deployed ", " field ", " workflow",
                                " environment", " setting", " location", " site", " workplace", " facility")),
                "the paper-supported application or analytical environment"
        );
        List<String> components = distinctLimited(merge(
                withoutCaptionLabels(evidence.visualClues()), evidence.keywords(), evidence.domainTerms()
        ), 8);
        List<String> interactions = distinctLimited(
                ranked(merge(evidence.applicationClues(), evidence.methodDesignSnippets()), USE_CUES), 5
        );
        String visibleArtifact = firstUseful(first(components), proposed);
        String visibleInput = firstUseful(best(methodEvidence, List.of("input", "data", "signal", "sample", "request")));
        String visibleOutput = firstUseful(
                best(merge(evidence.evaluationResultSnippets(), evidence.conclusionSnippets()),
                        List.of("output", "result", "produces", "generates", "improves", "supports", "enables")),
                first(evidence.evaluationResultSnippets())
        );
        String outcome = firstUseful(first(evidence.conclusionSnippets()), visibleOutput, evidence.abstractText());
        List<String> mustShow = distinctLimited(merge(
                values(proposed, targetOrDomain, environment, visibleArtifact, visibleOutput),
                components, interactions
        ), 10);
        List<String> mustAvoid = List.of(
                "generic abstract topic wallpaper", "unsupported actors or devices", "unsupported environment",
                "pure infographic or flowchart", "fake readable text", "fake logo", "watermark"
        );
        String scenario = targetOrDomain + " applying " + proposed + " in " + environment;
        String confidence = "LOW";

        return new OpenAiPaperUnderstanding(
                title,
                firstUseful(evidence.abstractText(), researchProblem),
                evidence.authors(),
                evidence.year(),
                researchProblem,
                targetOrDomain,
                proposed,
                proposed,
                outcome,
                components,
                relationship(visibleInput, visibleOutput, proposed),
                outcome,
                scenario,
                mustAvoid,
                scenario,
                components,
                interactions,
                environment,
                List.of(FALLBACK_WARNING, "Uncertain details were kept generic and require manual review."),
                confidence,
                evidence.venue(),
                proposed,
                visibleArtifact,
                visibleInput,
                visibleOutput,
                outcome,
                mustShow,
                mustAvoid
        );
    }

    private String relationship(String input, String output, String proposed) {
        if (input != null && output != null) {
            return input + " is processed through " + proposed + " to produce " + output;
        }
        if (output != null) {
            return proposed + " produces or supports " + output;
        }
        return proposed + " is applied in the paper-supported task or workflow";
    }

    private String best(List<String> values, List<String> cues) {
        return ranked(values, cues).stream().findFirst().orElse(null);
    }

    private List<String> ranked(List<String> values, List<String> cues) {
        return safe(values).stream()
                .sorted(Comparator.comparingInt((String value) -> score(value, cues)).reversed())
                .filter(value -> score(value, cues) > 0)
                .toList();
    }

    private int score(String value, List<String> cues) {
        String lower = value.toLowerCase(Locale.ROOT);
        int score = value.length() >= 30 ? 1 : 0;
        for (String cue : cues) {
            if (lower.contains(cue)) {
                score += cue.startsWith("we ") || cue.startsWith("this ") ? 8 : 4;
            }
        }
        if (lower.contains("related work") || lower.contains("theoretical framework")
                || lower.contains("previous studies") || lower.contains("literature review")) {
            score -= 12;
        }
        if (lower.startsWith("figure ") || lower.startsWith("table ")) {
            score -= 8;
        }
        return score;
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.replaceAll("\\s+", " ").trim();
            }
        }
        return null;
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private List<String> values(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    @SafeVarargs
    private final List<String> merge(List<String>... lists) {
        List<String> result = new ArrayList<>();
        for (List<String> list : lists) {
            result.addAll(safe(list));
        }
        return result;
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.replaceAll("\\s+", " ").trim())
                .distinct()
                .toList();
    }

    private List<String> withoutCaptionLabels(List<String> values) {
        return safe(values).stream()
                .filter(value -> {
                    String lower = value.toLowerCase(Locale.ROOT);
                    return !lower.startsWith("figure ") && !lower.startsWith("fig. ") && !lower.startsWith("table ");
                })
                .toList();
    }

    private List<String> distinctLimited(List<String> values, int limit) {
        return new LinkedHashSet<>(safe(values)).stream().limit(limit).toList();
    }
}
