package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.ApplicationSceneBrief;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import com.heritage.platform.dto.ai.ScenarioImagePrompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioBasedPromptServiceTests {

    private final ScenarioBasedPromptService service = new ScenarioBasedPromptService();

    @Test
    void buildsSpecificRealisticScenarioPrompt() {
        ScenarioImagePrompt prompt = service.create(sceneBrief(), understanding(), grounding());

        assertThat(prompt.positivePrompt())
                .contains("visually impaired climber")
                .contains("indoor climbing gym")
                .contains("audio-guided climbing assistance system")
                .contains("indoor climbing gym")
                .contains("actively using, applying, demonstrating, or experiencing the proposed contribution")
                .contains("clearly visible as the central paper-specific system")
                .contains("stakeholder and contribution appear together")
                .contains("task-consistent posture or workflow")
                .startsWith("Mandatory composition:")
                .contains("photorealistic single-camera product-in-use scene")
                .contains("one clearly visible human user or stakeholder actively operating a visible paper-specific")
                .contains("surrounding real application environment clearly visible around the interaction")
                .doesNotContain("paper-supported visual entities", "visual metaphor");
        assertThat(prompt.groundingFactsUsed()).contains("indoor climbing gym", "earpiece");
    }

    @Test
    void negativePromptRejectsGenericAndInaccurateImages() {
        ScenarioImagePrompt prompt = service.create(sceneBrief(), understanding(), grounding());

        assertThat(prompt.negativePrompt())
                .contains("generic abstract AI wallpaper")
                .contains("fake user-interface paragraphs")
                .contains("unsupported environment")
                .contains("pure flowchart")
                .contains("posed portrait instead of active use")
                .contains("stakeholder without the paper's contribution")
                .contains("empty environment without the stakeholder")
                .contains("abstract-only poster")
                .contains("scenery without the paper's product or system")
                .contains("product or system without a user or stakeholder")
                .contains("user or stakeholder without the visible system or interface")
                .contains("generic domain illustration")
                .contains("unrelated device scene")
                .contains("distorted hands");
    }

    @Test
    void promptRemainsGenericWhileRequiringStakeholderContributionAndVisibleResult() {
        ScenarioImagePrompt prompt = service.create(sceneBrief(), understanding(), ReferenceGroundingContext.empty(null));

        assertThat(prompt.positivePrompt())
                .startsWith("Mandatory composition:")
                .contains("create a realistic academic cover image")
                .contains("visually impaired climber")
                .contains("audio-guided climbing assistance system")
                .contains("earpiece and wearable sensor")
                .contains("visible system response or result")
                .contains("the contribution must be large and identifiable")
                .doesNotContain("heritage", "smartphone", "augmented reality");
        assertThat(prompt.negativePrompt())
                .contains("generic abstract AI wallpaper", "unsupported device", "fake logos");
    }

    private ApplicationSceneBrief sceneBrief() {
        return new ApplicationSceneBrief(
                "TASK_ASSISTANCE",
                "visually impaired climber",
                "blind climber",
                "indoor climbing gym",
                "audio-guided climbing assistance system",
                "earpiece and wearable sensor",
                "climbing a route safely",
                "ascending a climbing wall",
                "giving the next-hold direction through an audio cue",
                "safer independent climbing",
                "reaching for the next hold while receiving an audio cue",
                List.of("climbing wall", "colored holds", "earpiece"),
                List.of("reaching for a hold", "listening to an audio cue"),
                List.of("safety harness", "belay rope"),
                List.of("climber", "earpiece", "climbing wall"),
                List.of("hologram", "robot"),
                "Natural posture and correct safety gear.",
                "One climber with enough wall context visible.",
                "medium side view",
                "realistic product-in-use image",
                "HIGH",
                List.of()
        );
    }

    private FinalPaperUnderstanding understanding() {
        return new FinalPaperUnderstanding(
                "Accessible climbing guidance", "Summary", List.of(), 2026,
                "navigation", "visually impaired climber", "audio guidance",
                "audio-guided climbing assistance system", "safer climbing",
                List.of("earpiece"), "input to cue", "independence", null,
                List.of("futuristic display"), "HIGH"
        );
    }

    private ReferenceGroundingContext grounding() {
        return new ReferenceGroundingContext(
                List.of("A climber receives guidance through an earpiece."),
                List.of(), List.of(), List.of("audio guidance system"),
                List.of("indoor climbing gym"), List.of("climbing"),
                List.of("earpiece"), List.of("visually impaired user"),
                List.of("climbing wall"), List.of()
        );
    }

}
