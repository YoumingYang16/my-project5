package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class PaperUnderstandingMergeService {

    public PaperUnderstandingResult merge(
            GrobidMetadata grobidMetadata,
            OpenAiPaperUnderstanding providerUnderstanding,
            String providerFailureMessage
    ) {
        return merge(
                grobidMetadata, providerUnderstanding, providerFailureMessage,
                null, null, null, providerUnderstanding == null ? "FAILED" : "OPENAI"
        );
    }

    public PaperUnderstandingResult merge(
            GrobidMetadata grobidMetadata,
            OpenAiPaperUnderstanding providerUnderstanding,
            String providerFailureMessage,
            ReferenceGroundingContext grounding,
            Integer existingPublicationYear
    ) {
        return merge(
                grobidMetadata, providerUnderstanding, providerFailureMessage,
                grounding, existingPublicationYear, null,
                providerUnderstanding == null ? "FAILED" : "OPENAI"
        );
    }

    public PaperUnderstandingResult merge(
            GrobidMetadata grobidMetadata,
            OpenAiPaperUnderstanding providerUnderstanding,
            String providerFailureMessage,
            ReferenceGroundingContext grounding,
            Integer existingPublicationYear,
            PaperEvidencePacket evidencePacket
    ) {
        return merge(
                grobidMetadata, providerUnderstanding, providerFailureMessage,
                grounding, existingPublicationYear, evidencePacket,
                providerUnderstanding == null ? "FAILED" : "OPENAI"
        );
    }

    public PaperUnderstandingResult merge(
            GrobidMetadata grobidMetadata,
            OpenAiPaperUnderstanding providerUnderstanding,
            String providerFailureMessage,
            ReferenceGroundingContext grounding,
            Integer existingPublicationYear,
            PaperEvidencePacket evidencePacket,
            String sourceProvider
    ) {
        GrobidMetadata grobid = grobidMetadata == null
                ? GrobidMetadata.unavailable("GROBID metadata was not available.")
                : grobidMetadata;
        List<String> warnings = new ArrayList<>();
        warnings.addAll(safe(grobid.warnings()));
        if (evidencePacket != null) {
            warnings.addAll(safe(evidencePacket.warnings()));
        }
        if (providerFailureMessage != null && !providerFailureMessage.isBlank()) {
            warnings.add(AiCoverDiagnostics.sanitize(providerFailureMessage));
        }

        String source = normalizeSource(sourceProvider, providerUnderstanding);
        OpenAiPaperUnderstanding semantic = providerUnderstanding;
        boolean openAi = "OPENAI".equals(source);
        boolean evidenceFallback = "EVIDENCE_FALLBACK".equals(source);
        if (semantic == null || !openAi && !evidenceFallback) {
            warnings.add("No supported paper understanding provider produced usable output.");
            warnings.add(PaperAiQualityValidator.AUTHORITATIVE_UNDERSTANDING_MESSAGE);
            return new PaperUnderstandingResult("FAILED", grobid, null, null, distinct(warnings), source);
        }
        if (evidenceFallback) {
            warnings.add(DeterministicPaperUnderstandingProvider.FALLBACK_WARNING);
        }

        warnings.addAll(safe(semantic.warnings()));
        FinalPaperUnderstanding merged = new FinalPaperUnderstanding(
                prefer(grobid.title(), semantic.title()),
                prefer(grobid.abstractText(), semantic.abstractSummary()),
                safe(grobid.authors()).isEmpty() ? safe(semantic.authors()) : safe(grobid.authors()),
                firstNonNull(grobid.year(), existingPublicationYear, semantic.year()),
                semantic.researchProblem(),
                semantic.targetUsersOrDomain(),
                semantic.method(),
                semantic.keyImplementation(),
                semantic.keyContribution(),
                safe(semantic.importantSystemComponents()),
                semantic.inputOutputRelationship(),
                semantic.whyItMatters(),
                semantic.possibleVisualMetaphor(),
                mergeLimited(12, semantic.forbiddenVisualElements(), semantic.mustAvoidElements()),
                semantic.likelyApplicationScenario(),
                safe(semantic.visualizableEntities()),
                safe(semantic.visualizableInteractions()),
                semantic.visualizableEnvironment(),
                evidenceFallback ? "LOW" : confidence(semantic.confidenceLevel()),
                prefer(grobid.venue(), semantic.venue()),
                firstUseful(semantic.proposedSystemOrMethod(), semantic.keyImplementation(), semantic.method()),
                semantic.visibleInterfaceOrDevice(),
                semantic.visibleInput(),
                semantic.visibleOutput(),
                firstUseful(semantic.expectedOutcome(), semantic.whyItMatters(), semantic.keyContribution()),
                safe(semantic.mustShowElements()),
                mergeLimited(12, semantic.mustAvoidElements(), semantic.forbiddenVisualElements()),
                firstUseful(semantic.applicationEnvironment(), semantic.visualizableEnvironment()),
                firstUseful(semantic.mainTaskOrWorkflow(), semantic.likelyApplicationScenario()),
                firstUseful(semantic.visibleInteraction(), first(semantic.visualizableInteractions())),
                semantic.alternativeVisualMetaphor()
        );
        return new PaperUnderstandingResult("COMPLETED", grobid, semantic, merged, distinct(warnings), source);
    }

    private OpenAiPaperUnderstanding minimalFallback(
            GrobidMetadata grobid,
            PaperEvidencePacket evidence,
            Integer existingPublicationYear
    ) {
        if (evidence != null && !evidence.compactEvidenceText().isBlank()) {
            return new DeterministicPaperUnderstandingProvider().understand(evidence);
        }
        String title = firstUseful(evidence == null ? null : evidence.title(), grobid.title());
        String summary = firstUseful(evidence == null ? null : evidence.abstractText(), grobid.abstractText(), title);
        String proposed = firstUseful(
                first(evidence == null ? null : evidence.implementationSnippets()),
                first(evidence == null ? null : evidence.methodDesignSnippets()),
                title
        );
        String target = firstUseful(
                first(evidence == null ? null : evidence.applicationClues()),
                first(evidence == null ? null : evidence.domainTerms()),
                "the application domain described by the paper"
        );
        String environment = firstUseful(
                first(evidence == null ? null : evidence.applicationClues()),
                "the paper-supported application or analytical context"
        );
        List<String> components = mergeLimited(8,
                evidence == null ? List.of() : evidence.visualClues(),
                evidence == null ? List.of() : evidence.keywords(),
                evidence == null ? List.of() : evidence.domainTerms()
        );
        String visibleArtifact = firstUseful(first(components), proposed);
        String output = firstUseful(
                first(evidence == null ? null : evidence.evaluationResultSnippets()),
                first(evidence == null ? null : evidence.conclusionSnippets()),
                summary
        );
        List<String> mustShow = mergeLimited(10,
                values(proposed, target, environment, visibleArtifact, output), components
        );
        List<String> mustAvoid = List.of(
                "generic abstract topic wallpaper", "unsupported actors or equipment",
                "unsupported environment", "pure infographic or flowchart", "fake readable text",
                "fake logo", "watermark"
        );
        return new OpenAiPaperUnderstanding(
                title, summary, safe(grobid.authors()),
                firstNonNull(grobid.year(), existingPublicationYear, evidence == null ? null : evidence.year()),
                summary, target, proposed, proposed, output, components,
                proposed + " is applied to produce or support " + output,
                output, null, mustAvoid,
                target + " applying " + proposed + " in " + environment,
                components, List.of(), environment,
                List.of("Deterministic understanding was derived from compact paper evidence."),
                "LOW", firstUseful(grobid.venue(), evidence == null ? null : evidence.venue()),
                proposed, visibleArtifact, null, output, output, mustShow, mustAvoid
        );
    }

    private boolean hasUsefulFallback(GrobidMetadata grobid, PaperEvidencePacket evidence) {
        return useful(grobid == null ? null : grobid.title())
                || useful(evidence == null ? null : evidence.title())
                || useful(grobid == null ? null : grobid.abstractText())
                || evidence != null && !evidence.compactEvidenceText().isBlank();
    }

    private String normalizeSource(String source, OpenAiPaperUnderstanding understanding) {
        if (source == null || source.isBlank()) {
            return understanding == null ? "FAILED" : "OPENAI";
        }
        String value = source.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            // n8n is the transport layer for the user's DeepSeek call.
            case "N8N", "DEEPSEEK" -> "OPENAI";
            case "OPENAI", "EVIDENCE_FALLBACK" -> value;
            default -> value;
        };
    }

    private String prefer(String first, String second) {
        return useful(first) ? first.trim() : second;
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (useful(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer firstNonNull(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private boolean useful(String value) {
        return value != null && !value.isBlank() && !"not clearly specified".equalsIgnoreCase(value.trim());
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
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (List<String> list : lists) {
            values.addAll(safe(list));
        }
        return values.stream().limit(limit).toList();
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(this::useful)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(safe(values)));
    }

    private String confidence(String value) {
        String normalized = value == null ? "LOW" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "LOW";
        };
    }
}
