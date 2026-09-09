package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.ApplicationSceneBrief;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import com.heritage.platform.dto.ai.ScenarioImagePrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ScenarioBasedPromptService {

    @Value("${ai-cover.cover-mode:APPLICATION_SCENE}")
    private String coverMode;

    private static final List<String> BASE_NEGATIVE = List.of(
            "abstract wallpaper", "generic tech poster", "generic abstract AI wallpaper",
            "unrelated futuristic background", "pure infographic", "pure flowchart",
            "concept diagram replacing the application scene", "title-only illustration", "topic-only illustration",
            "unrelated scene",
            "abstract-only poster", "scenery without the paper's product or system",
            "product or system without a user or stakeholder",
            "user or stakeholder without the visible system or interface",
            "generic domain illustration", "unrelated device scene",
            "empty environment without the stakeholder", "stakeholder without the paper's contribution",
            "hidden or tiny proposed system", "posed portrait instead of active use", "unreadable fake text",
            "fake user-interface paragraphs", "fake equations", "fake code", "fake logos", "watermark",
            "unsupported device", "unsupported environment", "irrelevant objects", "distorted hands",
            "distorted faces", "low quality", "blurry image", "messy composition"
    );

    public ScenarioImagePrompt create(
            ApplicationSceneBrief brief,
            FinalPaperUnderstanding understanding,
            ReferenceGroundingContext grounding
    ) {
        if (brief == null) {
            throw new AiCoverWorkflowException("No application scene brief was available.");
        }
        String contribution = concise(firstUseful(
                understanding == null ? null : understanding.proposedSystemOrMethod(),
                brief.technologyOrProduct(),
                understanding == null ? null : understanding.keyImplementation(),
                understanding == null ? null : understanding.method()
        ), "the paper's proposed contribution", 260);
        String stakeholder = concise(firstUseful(
                brief.targetUser(),
                understanding == null ? null : understanding.targetUsersOrDomain()
        ), "the paper-supported stakeholder or operator", 180);
        String environment = concise(firstUseful(
                brief.environment(),
                understanding == null ? null : understanding.applicationEnvironment(),
                understanding == null ? null : understanding.visualizableEnvironment(),
                understanding == null ? null : understanding.likelyApplicationScenario()
        ), "the paper-supported application or analytical environment", 260);
        String task = concise(firstUseful(
                brief.mainTask(), understanding == null ? null : understanding.mainTaskOrWorkflow(),
                brief.sceneMoment(), first(brief.visibleInteractions())
        ), "the central task or workflow described by the paper", 280);
        String interfaceOrArtifact = concise(firstUseful(
                understanding == null ? null : understanding.visibleInterfaceOrDevice(),
                brief.technologyFormFactor(), first(brief.visibleObjects()), contribution
        ), "a concrete visible representation of the proposed contribution", 220);
        String visibleInput = concise(firstUseful(
                understanding == null ? null : understanding.visibleInput(), brief.taskContext()
        ), "the task input or real-world subject supported by the evidence", 200);
        String visibleOutput = concise(firstUseful(
                understanding == null ? null : understanding.visibleOutput(),
                brief.supportAction(), brief.expectedOutcome()
        ), "the immediate visible output or response", 240);
        String outcome = concise(firstUseful(
                brief.expectedOutcome(),
                understanding == null ? null : understanding.expectedOutcome(),
                understanding == null ? null : understanding.whyItMatters()
        ), "the paper-supported practical outcome", 240);
        List<String> mustShow = mergeLimited(10,
                brief.mustShowElements(),
                understanding == null ? List.of() : understanding.mustShowElements()
        ).stream().map(value -> concise(value, "", 140)).filter(value -> !value.isBlank()).toList();
        List<String> groundingFacts = groundingFacts(grounding);

        requireConcreteScene(stakeholder, contribution, environment, task, interfaceOrArtifact, understanding);

        String positive = String.join(", ", List.of(
                "create a realistic academic cover image showing the paper-grounded product, system, method, tool, model, or interface in active use",
                "(" + contribution + " clearly visible as the central paper-specific system, method, product, tool, model, or artifact:1.70)",
                "(" + stakeholder + " actively using, applying, demonstrating, or experiencing the proposed contribution:1.45)",
                "real or plausible application environment: " + environment,
                "main task or workflow in progress: " + task,
                "visible interface, device, tool, model artifact, or output: " + interfaceOrArtifact,
                "visible input or subject: " + visibleInput,
                "visible system response or result: " + visibleOutput,
                "communicate the supported outcome: " + outcome,
                "mandatory concrete scene elements: " + join(mustShow, "stakeholder, contribution, task context, visible result"),
                "the contribution must be large and identifiable enough that removing it would change the meaning of the scene",
                "stakeholder and contribution appear together with task-consistent posture or workflow",
                "clearly show the product or system itself, the user interacting with it, and the real usage scene",
                "paper-grounded product-in-use scene, not an abstract poster, not a flowchart, and not a generic illustration",
                "single coherent moment, documentary realism, believable posture and equipment, natural lighting, clear 4:3 composition",
                "no title, no caption, no readable fake text, no fake logo, no watermark"
        ));
        positive = reinforceRequiredComposition(positive);

        List<String> negative = new ArrayList<>(BASE_NEGATIVE);
        negative.addAll(safe(brief.mustNotShowElements()));
        if (understanding != null) {
            negative.addAll(safe(understanding.mustAvoidElements()));
            negative.addAll(safe(understanding.forbiddenVisualElements()));
        }
        List<String> warnings = new ArrayList<>(safe(brief.warnings()));
        if (groundingFacts.isEmpty()) {
            warnings.add("Limited paper-specific visual grounding was found, so scene details were inferred conservatively.");
        }
        return new ScenarioImagePrompt(
                positive,
                String.join(", ", distinct(negative)),
                "Shows " + stakeholder + " applying " + contribution + " in " + environment + ".",
                "APPLICATION_SCENE_" + effectiveCoverMode(),
                groundingFacts,
                List.of(
                        "paper contribution is visually central", "stakeholder and contribution appear together",
                        "task-consistent posture or workflow", "paper-supported environment",
                        "visible input, response, or outcome", "realistic lighting and composition"
                ),
                distinct(warnings)
        );
    }

    private List<String> groundingFacts(ReferenceGroundingContext grounding) {
        if (grounding == null) {
            return List.of();
        }
        return mergeLimited(8,
                grounding.figureCaptions(), grounding.interfaceOrSystemMentions(),
                grounding.environmentMentions(), grounding.taskMentions(),
                grounding.deviceMentions(), grounding.userGroupMentions()
        );
    }

    private String reinforceRequiredComposition(String prompt) {
        String requiredComposition = "Mandatory composition: "
                + "((photorealistic single-camera product-in-use scene:1.60)), "
                + "((one clearly visible human user or stakeholder actively operating a visible paper-specific "
                + "product, system, interface, tool, or method embodiment with their hands or body:1.85)), "
                + "((surrounding real application environment clearly visible around the interaction:1.50)). "
                + "Use a third-person medium shot, stakeholder point-of-view, over-the-shoulder, or hands-on-use view. "
                + "Not a poster, diagram, infographic, collage, aerial scenery, product-only shot, or portrait.";
        return prompt.toLowerCase().contains("mandatory composition:")
                ? prompt
                : requiredComposition + ", " + prompt;
    }

    private void requireConcreteScene(
            String stakeholder,
            String contribution,
            String environment,
            String task,
            String interfaceOrArtifact,
            FinalPaperUnderstanding understanding
    ) {
        PaperAiQualityValidator quality = new PaperAiQualityValidator();
        if (!quality.isMeaningful(stakeholder)
                || !quality.isMeaningful(contribution)
                || !quality.isMeaningful(environment)
                || !quality.isMeaningful(task)
                || !quality.isMeaningful(interfaceOrArtifact)
                || quality.containsTestPlaceholder(String.join(" ", List.of(
                        stakeholder, contribution, environment, task, interfaceOrArtifact
                )))
                || understanding != null && contribution.equalsIgnoreCase(understanding.title())) {
            throw new AiCoverWorkflowException(
                    "A product-in-use prompt requires a concrete system, user, task, interface, and usage environment from OpenAI understanding."
            );
        }
    }

    private String effectiveCoverMode() {
        return coverMode == null || coverMode.isBlank() ? "APPLICATION_SCENE" : coverMode.trim().toUpperCase();
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"not clearly specified".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private String concise(String value, String fallback, int maxLength) {
        String cleaned = firstUseful(value, fallback);
        if (cleaned == null) {
            return fallback;
        }
        cleaned = cleaned.replace("...", "").replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        String prefix = cleaned.substring(0, maxLength).trim();
        int boundary = prefix.lastIndexOf(' ');
        return boundary > maxLength / 2 ? prefix.substring(0, boundary).trim() : prefix;
    }

    private String join(List<String> values, String fallback) {
        List<String> safe = safe(values);
        return safe.isEmpty() ? fallback : String.join(", ", safe);
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
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(safe(values)));
    }
}
