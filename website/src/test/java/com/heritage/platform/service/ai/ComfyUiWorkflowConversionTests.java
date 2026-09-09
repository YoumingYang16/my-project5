package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComfyUiWorkflowConversionTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsSdxlUiWorkflowToConfiguredApiWorkflow() throws Exception {
        ComfyUiImageGenerationService service = new ComfyUiImageGenerationService(objectMapper, new UploadPathService());
        ReflectionTestUtils.setField(service, "checkpoint", "sd_xl_base_1.0.safetensors");

        String template = Files.readString(Path.of("src/main/resources/comfyui/workflows/sdxl-basic-teaser-workflow.json"));
        JsonNode apiWorkflow = service.buildApiWorkflow(
                template,
                "positive academic teaser prompt",
                "negative clutter prompt",
                12345L,
                1024,
                768,
                "publication-1-candidate-1-seed-12345"
        );

        assertThat(apiWorkflow.has("nodes")).isFalse();
        assertThat(apiWorkflow.path("1").path("class_type").asText()).isEqualTo("CheckpointLoaderSimple");
        assertThat(apiWorkflow.path("1").path("inputs").path("ckpt_name").asText())
                .isEqualTo("sd_xl_base_1.0.safetensors");
        assertThat(apiWorkflow.path("4").path("inputs").path("width").asInt()).isEqualTo(1024);
        assertThat(apiWorkflow.path("4").path("inputs").path("height").asInt()).isEqualTo(768);
        assertThat(apiWorkflow.path("5").path("inputs").path("seed").asLong()).isEqualTo(12345L);
        assertThat(apiWorkflow.path("8").path("inputs").path("text").asText()).isEqualTo("positive academic teaser prompt");
        assertThat(apiWorkflow.path("9").path("inputs").path("text").asText()).isEqualTo("negative clutter prompt");
        assertThat(apiWorkflow.path("7").path("inputs").path("filename_prefix").asText())
                .isEqualTo("publication-1-candidate-1-seed-12345");
        assertThat(apiWorkflow.path("5").path("inputs").path("positive").get(0).asText()).isEqualTo("8");
        assertThat(apiWorkflow.path("5").path("inputs").path("negative").get(0).asText()).isEqualTo("9");
    }

    @Test
    void createsThreeDistinctCompositionsWhileRetainingPaperGrounding() {
        ComfyUiImageGenerationService service = new ComfyUiImageGenerationService(objectMapper, new UploadPathService());
        String basePrompt = "field operator, paper-specific inspection tool, visible sensor input, verified output";

        List<String> prompts = List.of(
                service.candidatePositivePrompt(basePrompt, 1),
                service.candidatePositivePrompt(basePrompt, 2),
                service.candidatePositivePrompt(basePrompt, 3)
        );

        assertThat(prompts).allMatch(prompt -> prompt.contains(basePrompt));
        assertThat(prompts.get(0)).contains("full application scene", "stakeholder, contribution, task, and response all visible");
        assertThat(prompts.get(1)).contains("interaction and workflow close-up", "input, and visible output especially clear");
        assertThat(prompts.get(2)).contains("outcome and context scene", "immediate benefit or outcome");
        assertThat(prompts).doesNotHaveDuplicates();
    }
}
