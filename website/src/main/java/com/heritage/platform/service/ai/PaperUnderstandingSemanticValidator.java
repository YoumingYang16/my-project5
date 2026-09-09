package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PaperUnderstandingSemanticValidator {

    private static final Set<String> VAGUE = Set.of(
            "not clearly specified", "not specified", "unknown", "n/a", "none", "the proposed system",
            "the proposed method", "the paper", "the research", "users", "application scenario"
    );

    public OpenAiPaperUnderstanding validate(
            OpenAiPaperUnderstanding raw,
            PaperEvidencePacket evidence,
            String providerName
    ) {
        if (raw == null) {
            throw new AiCoverWorkflowException(providerName + " returned an empty paper understanding.");
        }
        List<String> warnings = new ArrayList<>(raw.warnings());
        String proposed = firstUseful(raw.proposedSystemOrMethod());
        String target = firstUseful(raw.targetUsersOrDomain());
        String environment = firstUseful(raw.applicationEnvironment(), raw.visualizableEnvironment());
        String task = firstUseful(raw.mainTaskOrWorkflow(), raw.likelyApplicationScenario());
        String interfaceOrDevice = firstUseful(
                raw.visibleInterfaceOrDevice(), first(raw.importantSystemComponents()), first(raw.visualizableEntities())
        );
        String visibleInteraction = firstUseful(raw.visibleInteraction(), first(raw.visualizableInteractions()));
        String visibleOutput = firstUseful(
                raw.visibleOutput(), raw.inputOutputRelationship(), raw.expectedOutcome(),
                first(evidence == null ? null : evidence.evaluationResultSnippets())
        );
        String expectedOutcome = firstUseful(
                raw.expectedOutcome(), raw.whyItMatters(), raw.keyContribution(),
                first(evidence == null ? null : evidence.conclusionSnippets()), visibleOutput
        );

        int sceneSignals = countUseful(target, environment, task, interfaceOrDevice, visibleOutput, visibleInteraction)
                + (raw.mustShowElements().isEmpty() ? 0 : 1);
        if (!useful(proposed) || sceneSignals < 5 || containsPlaceholder(raw, evidence)) {
            throw new AiCoverWorkflowException(
                    providerName + " returned unusable structured understanding: product, user, task, environment, interaction, or outcome fields were too vague."
            );
        }

        List<String> mustShow = mergeLimited(10,
                raw.mustShowElements(),
                values(proposed, target, environment, task, interfaceOrDevice, visibleInteraction, visibleOutput),
                raw.visualizableEntities(),
                raw.visualizableInteractions()
        );
        List<String> mustAvoid = mergeLimited(12,
                raw.mustAvoidElements(), raw.forbiddenVisualElements()
        );
        List<String> forbidden = mergeLimited(12,
                raw.forbiddenVisualElements(), raw.mustAvoidElements()
        );
        if (!useful(raw.proposedSystemOrMethod()) || !useful(raw.visualizableEnvironment())) {
            warnings.add("Some visual-scene fields were completed conservatively from the compact paper evidence.");
        }

        return new OpenAiPaperUnderstanding(
                firstUseful(raw.title(), evidence == null ? null : evidence.title()),
                firstUseful(raw.abstractSummary(), evidence == null ? null : evidence.abstractText()),
                raw.authors().isEmpty() && evidence != null ? evidence.authors() : raw.authors(),
                raw.year() == null && evidence != null ? evidence.year() : raw.year(),
                raw.researchProblem(),
                target,
                firstUseful(raw.method(), proposed),
                firstUseful(raw.keyImplementation(), proposed),
                firstUseful(raw.keyContribution(), expectedOutcome),
                raw.importantSystemComponents(),
                firstUseful(raw.inputOutputRelationship(), visibleOutput),
                firstUseful(raw.whyItMatters(), expectedOutcome),
                raw.possibleVisualMetaphor(),
                forbidden,
                firstUseful(raw.likelyApplicationScenario(), environment),
                raw.visualizableEntities(),
                raw.visualizableInteractions(),
                environment,
                distinct(warnings),
                confidence(raw.confidenceLevel(), warnings),
                firstUseful(raw.venue(), evidence == null ? null : evidence.venue()),
                proposed,
                interfaceOrDevice,
                raw.visibleInput(),
                visibleOutput,
                expectedOutcome,
                mustShow,
                mustAvoid,
                environment,
                task,
                visibleInteraction,
                raw.alternativeVisualMetaphor()
        );
    }

    private boolean containsPlaceholder(OpenAiPaperUnderstanding raw, PaperEvidencePacket evidence) {
        PaperAiQualityValidator quality = new PaperAiQualityValidator();
        List<String> values = List.of(
                safe(raw.researchProblem()), safe(raw.targetUsersOrDomain()), safe(raw.proposedSystemOrMethod()),
                safe(raw.method()), safe(raw.keyImplementation()), safe(raw.keyContribution()),
                safe(raw.applicationEnvironment()), safe(raw.mainTaskOrWorkflow()),
                safe(raw.visibleInterfaceOrDevice()), safe(raw.visibleInteraction()), safe(raw.expectedOutcome())
        );
        if (values.stream().anyMatch(quality::isPlaceholder)) {
            return true;
        }
        String title = raw.title() == null && evidence != null ? evidence.title() : raw.title();
        long useful = values.stream().filter(quality::isMeaningful).count();
        long titleCopies = values.stream()
                .filter(quality::isMeaningful)
                .filter(value -> normalize(value).equals(normalize(title)))
                .count();
        return useful < 8 || titleCopies * 2 >= useful;
    }

    private String confidence(String value, List<String> warnings) {
        String normalized = value == null ? "LOW" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(normalized)) {
            normalized = "LOW";
        }
        if (!warnings.isEmpty() && "HIGH".equals(normalized)) {
            return "MEDIUM";
        }
        return normalized;
    }

    private int countUseful(String... values) {
        int count = 0;
        for (String value : values) {
            if (useful(value)) {
                count++;
            }
        }
        return count;
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (useful(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean useful(String value) {
        return value != null && !value.isBlank()
                && !VAGUE.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return safe(value).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private List<String> values(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (useful(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }

    @SafeVarargs
    private final List<String> mergeLimited(int limit, List<String>... lists) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (List<String> list : lists) {
            if (list != null) {
                list.stream().filter(this::useful).map(String::trim).forEach(result::add);
            }
        }
        return result.stream().limit(limit).toList();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values == null ? List.of() : values));
    }
}
