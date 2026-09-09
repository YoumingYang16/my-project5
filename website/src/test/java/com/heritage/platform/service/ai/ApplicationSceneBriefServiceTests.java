package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.ApplicationSceneBrief;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ApplicationSceneBriefServiceTests {

    private final OpenAiStructuredResponseClient openAiClient = mock(OpenAiStructuredResponseClient.class);
    private final ApplicationSceneBriefService service = new ApplicationSceneBriefService(
            new ObjectMapper(),
            openAiClient
    );

    @Test
    void deterministicModeDoesNotCallOpenAiSceneAnalysis() {
        ApplicationSceneBrief brief = service.create(understanding(), grounding());

        assertThat(brief).isNotNull();
        assertThat(service.usesOpenAi()).isFalse();
        verifyNoInteractions(openAiClient);
    }

    @Test
    void parsesCompleteApplicationSceneBriefJson() throws Exception {
        String json = """
                {
                  "paperSceneType": "TASK_ASSISTANCE",
                  "targetUser": "visually impaired climber",
                  "userRole": "blind climber",
                  "environment": "indoor climbing gym",
                  "technologyOrProduct": "audio-guided climbing assistance system",
                  "technologyFormFactor": "earpiece and wearable sensors",
                  "mainTask": "climbing a route safely",
                  "taskContext": "ascending a color-coded climbing wall",
                  "supportAction": "providing the next hold direction through audio cues",
                  "expectedOutcome": "safer and more independent climbing",
                  "sceneMoment": "the climber reaches for the next hold while receiving a cue",
                  "visibleObjects": ["climbing wall", "colored holds", "earpiece"],
                  "visibleInteractions": ["reaching for the next hold", "listening to an audio cue"],
                  "domainSpecificDetails": ["safety harness", "belay rope"],
                  "mustShowElements": ["climber", "earpiece", "climbing wall"],
                  "mustNotShowElements": ["hologram"],
                  "realismNotes": "Use correct climbing posture and safety equipment.",
                  "compositionHint": "Medium shot with the wall context visible.",
                  "cameraView": "side medium shot",
                  "styleDirection": "realistic product-in-use image",
                  "confidenceLevel": "HIGH",
                  "warnings": []
                }
                """;

        ApplicationSceneBrief brief = service.parse(json, understanding(), grounding());

        assertThat(brief.paperSceneType()).isEqualTo("TASK_ASSISTANCE");
        assertThat(brief.targetUser()).isEqualTo("visually impaired climber");
        assertThat(brief.environment()).isEqualTo("indoor climbing gym");
        assertThat(brief.visibleObjects()).contains("earpiece", "climbing wall");
        assertThat(brief.confidenceLevel()).isEqualTo("HIGH");
    }

    @Test
    void fillsMissingSceneFieldsConservatively() throws Exception {
        ApplicationSceneBrief brief = service.parse(
                "{\"paperSceneType\":\"UNKNOWN\",\"targetUser\":\"museum visitor\"}",
                understanding(),
                grounding()
        );

        assertThat(brief.paperSceneType()).isEqualTo("APPLICATION_SCENE");
        assertThat(brief.environment()).isEqualTo("museum");
        assertThat(brief.technologyOrProduct()).contains("audio-guided");
        assertThat(brief.mainTask()).isEqualTo("independent climbing navigation");
        assertThat(brief.warnings()).anyMatch(warning -> warning.contains("filled conservatively"));
    }

    @Test
    void deterministicFallbackBuildsConcreteSceneFromGenericVisualFields() {
        FinalPaperUnderstanding understanding = new FinalPaperUnderstanding(
                "CropSense: Adaptive field monitoring",
                "Growers use a sensing platform to inspect crop conditions.",
                List.of("A. Researcher"),
                2025,
                "Field conditions are difficult to inspect consistently.",
                "crop growers and field monitoring",
                "adaptive sensing and condition analysis",
                "a field sensing platform with a condition map",
                "supports timely inspection decisions",
                List.of("field sensing platform", "condition map", "sensor node"),
                "sensor observations produce a condition map",
                "supports timely field inspection",
                "a grower reviewing field conditions with sensing results visible",
                List.of("unsupported laboratory setting"),
                "a grower inspects crops while reviewing a condition map",
                List.of("grower", "crop rows", "sensor node", "condition map"),
                List.of("inspecting crops", "reviewing sensing results"),
                "outdoor crop field",
                "MEDIUM"
        );
        ReferenceGroundingContext grounding = ReferenceGroundingContext.empty(null);

        ApplicationSceneBrief brief = service.conservativeFallback(understanding, grounding, List.of());

        assertThat(brief.targetUser()).isEqualTo("crop growers and field monitoring");
        assertThat(brief.environment()).isEqualTo("outdoor crop field");
        assertThat(brief.technologyOrProduct()).contains("field sensing platform");
        assertThat(brief.technologyFormFactor()).isEqualTo("field sensing platform");
        assertThat(brief.mainTask()).isEqualTo("inspecting crops");
        assertThat(brief.mustShowElements()).contains("sensor node", "condition map", "outdoor crop field");
    }

    private FinalPaperUnderstanding understanding() {
        return new FinalPaperUnderstanding(
                "Accessible climbing guidance",
                "An audio system assists visually impaired climbers.",
                List.of("A. Author"),
                2026,
                "independent climbing navigation",
                "visually impaired climber",
                "audio route guidance",
                "audio-guided climbing assistance system",
                "safer independent climbing",
                List.of("earpiece", "wearable sensor"),
                "sensor input becomes an audio cue",
                "improves safe route understanding",
                null,
                List.of("hologram"),
                "HIGH"
        );
    }

    private ReferenceGroundingContext grounding() {
        return new ReferenceGroundingContext(
                List.of("A climber receives audio guidance on the wall."),
                List.of(),
                List.of("User study in an indoor climbing gym"),
                List.of("audio guidance system"),
                List.of("museum"),
                List.of("navigation"),
                List.of("earpiece"),
                List.of("visually impaired user"),
                List.of("earpiece", "climbing wall"),
                List.of()
        );
    }
}
