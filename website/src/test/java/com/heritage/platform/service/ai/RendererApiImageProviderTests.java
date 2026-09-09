package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.ScenarioImagePrompt;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RendererApiImageProviderTests {

    @TempDir
    Path tempDir;

    @Test
    void downloadsSourceQwenAndComfyCandidatesIntoTheWebsiteUploadDirectory() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] image = "fake-jpeg-data".getBytes(StandardCharsets.UTF_8);
        int port = server.getAddress().getPort();
        String assets = "http://127.0.0.1:" + port;
        server.createContext("/extract/pdf", exchange -> json(exchange, """
                {"content":"paper text","sourceFigures":[{
                  "type":"source-extracted","url":"%s/source.jpg","relevanceScore":0.91,"pageNumber":4
                }]}
                """.formatted(assets)));
        server.createContext("/render/photo", exchange -> json(exchange, """
                {"prompt":"grounded prompt","images":[
                  {"type":"qwen-photo-1","provider":"qwen-image","providerStrategy":"qwen-text-to-image",
                   "url":"%s/qwen.jpg","seed":101,"qualityScore":0.91,"semanticScore":0.87,"textRisk":false,"anatomyRisk":"none"},
                  {"type":"comfy-photo-1","provider":"local-comfyui","providerStrategy":"ip-adapter",
                   "url":"%s/comfy.jpg","seed":202,"qualityScore":0.82,"semanticScore":0.8,"textRisk":false,"anatomyRisk":"none"}
                ]}
                """.formatted(assets, assets)));
        server.createContext("/render/xhs-cover", exchange -> json(exchange, """
                {"image":{"url":"%s/social.jpg"},"provider":"satori-layout"}
                """.formatted(assets)));
        server.createContext("/source.jpg", exchange -> bytes(exchange, image));
        server.createContext("/qwen.jpg", exchange -> bytes(exchange, image));
        server.createContext("/comfy.jpg", exchange -> bytes(exchange, image));
        server.createContext("/social.jpg", exchange -> bytes(exchange, image));
        server.start();

        try {
            UploadPathService paths = new UploadPathService();
            Path uploads = tempDir.resolve("uploads");
            ReflectionTestUtils.setField(paths, "uploadDir", uploads.toString());
            ReflectionTestUtils.setField(paths, "generatedCoverOutputDirectory", uploads.resolve("generated-covers").toString());
            RendererApiImageProvider provider = new RendererApiImageProvider(new ObjectMapper(), paths);
            ReflectionTestUtils.setField(provider, "rendererEnabled", true);
            ReflectionTestUtils.setField(provider, "rendererBaseUrl", assets);
            ReflectionTestUtils.setField(provider, "timeoutSeconds", 30L);
            ReflectionTestUtils.setField(provider, "imageProviderMode", "dual");
            ReflectionTestUtils.setField(provider, "sourcePolicy", "balanced");
            ReflectionTestUtils.setField(provider, "aestheticProfile", "editorial");
            ReflectionTestUtils.setField(provider, "qualityMode", "strict");
            Path pdf = tempDir.resolve("paper.pdf");
            Files.write(pdf, new byte[256]);

            List<AiCoverCandidate> candidates = provider.generate(new PublicationImageGenerationRequest(
                    42L, "Paper title", "Paper abstract", pdf,
                    null, null, null, null, null,
                    "paper-grounded prompt", "text, watermark"
            ));

            assertThat(candidates).hasSize(3);
            assertThat(candidates).allSatisfy(candidate -> {
                assertThat(candidate.imageUrl()).startsWith("/uploads/generated-covers/42/");
                assertThat(paths.resolveSelectedGeneratedCover(42L, candidate.imageUrl())).isRegularFile();
                assertThat(candidate.sceneScore()).isNotNull();
                assertThat(candidate.socialCoverUrl()).startsWith("/uploads/generated-covers/42/");
                assertThat(paths.resolveSelectedGeneratedCover(42L, candidate.socialCoverUrl())).isRegularFile();
                assertThat(candidate.sourceType()).isIn("source-original", "qwen", "comfyui");
            });
            assertThat(candidates).anyMatch(AiCoverCandidate::recommended);
            assertThat(uploads.resolve("generated-covers/42/renderer-manifest.json")).isRegularFile();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesBothPromptsFromTheEvidenceGroundedVisualBrief() {
        UploadPathService paths = new UploadPathService();
        RendererApiImageProvider provider = new RendererApiImageProvider(new ObjectMapper(), paths);
        FinalPaperUnderstanding understanding = mock(FinalPaperUnderstanding.class);
        when(understanding.alternativeVisualMetaphor()).thenReturn(
                "Alternative evidence-grounded composition focused on the paper-specific research environment and a different camera role."
        );
        String primary = "Primary evidence-grounded composition showing the paper-specific object, material, activity, environment, and technology without unsupported details.";
        ScenarioImagePrompt scenario = new ScenarioImagePrompt(
                primary, "text, unsupported device", "shared brief", "EVIDENCE_GROUNDED_VISUAL_BRIEF",
                List.of("paper-specific object", "paper-specific environment"), List.of(), List.of()
        );
        PublicationImageGenerationRequest request = new PublicationImageGenerationRequest(
                42L, "Paper title", "Paper abstract", Path.of("paper.pdf"),
                null, understanding, null, scenario, null, primary, "text, unsupported device"
        );

        List<String> variants = ReflectionTestUtils.invokeMethod(provider, "evidenceGroundedPromptVariants", request);
        String renderedPrompt = ReflectionTestUtils.invokeMethod(
                provider, "conciseVisualPrompt", request, "system-interface",
                List.of("paper-specific object"), List.of("paper-specific anchor")
        );

        assertThat(variants).containsExactly(primary, understanding.alternativeVisualMetaphor());
        assertThat(renderedPrompt).isEqualTo(primary);
    }
    @Test
    void forwardsConfiguredDoubaoKeyAndSeedreamModelToRenderer() {
        UploadPathService paths = new UploadPathService();
        RendererApiImageProvider provider = new RendererApiImageProvider(new ObjectMapper(), paths);
        UserAiProviderSettingsService settings = new UserAiProviderSettingsService();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("doubao-user", "", "ROLE_ADMIN"));
        try {
            settings.configureImage("doubao", "a".repeat(24), "doubao-seedream-4-5-251128");
            ReflectionTestUtils.invokeMethod(provider, "setProviderSettings", settings);
            JsonNode request = ReflectionTestUtils.invokeMethod(provider, "buildRenderRequest", new PublicationImageGenerationRequest(
                    42L, "Paper title", "Paper abstract", Path.of("paper.pdf"),
                    null, null, null, null, null, "paper-grounded prompt", "text, watermark"
            ), new ObjectMapper().createObjectNode());

            assertThat(request.path("cloudImageProvider").asText()).isEqualTo("doubao");
            assertThat(request.path("imageApiKey").asText()).isEqualTo("a".repeat(24));
            assertThat(request.path("cloudImageModel").asText()).isEqualTo("doubao-seedream-4-5-251128");
            assertThat(request.path("requireRequestImageKey").asBoolean()).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
    private static void json(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void bytes(HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
