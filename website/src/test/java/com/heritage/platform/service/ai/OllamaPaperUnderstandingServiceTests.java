package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaPaperUnderstandingServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> responseContent = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> respond(
                exchange, 200, "{\"models\":[{\"name\":\"qwen3:14b\"}]}"
        ));
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String content = responseContent.get() == null
                    ? objectMapper.writeValueAsString(validUnderstanding())
                    : responseContent.get();
            String response = objectMapper.writeValueAsString(Map.of(
                    "message", Map.of(
                            "role", "assistant",
                            "content", content,
                            "thinking", "SECRET_THINKING_MUST_NEVER_BE_PARSED"
                    )
            ));
            respond(exchange, 200, response);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesNativeChatJsonAndParsesOnlyMessageContent() throws Exception {
        OllamaPaperUnderstandingService service = service();

        assertThat(service.isAvailable()).isTrue();
        OpenAiPaperUnderstanding result = service.understand(packet());
        JsonNode sent = objectMapper.readTree(requestBody.get());

        assertThat(sent.path("model").asText()).isEqualTo("qwen3:14b");
        assertThat(sent.path("stream").asBoolean()).isFalse();
        assertThat(sent.path("think").asBoolean()).isFalse();
        assertThat(sent.path("format").asText()).isEqualTo("json");
        assertThat(sent.path("options").path("temperature").asDouble()).isEqualTo(0.1);
        assertThat(sent.path("options").path("num_ctx").asInt()).isEqualTo(8192);
        assertThat(sent.path("messages").get(1).path("content").asText()).contains("compact evidence");
        assertThat(authorization.get()).isNull();
        assertThat(result.proposedSystemOrMethod()).isEqualTo("a paper-specific inspection assistant");
        assertThat(objectMapper.writeValueAsString(result)).doesNotContain("SECRET_THINKING");
    }

    @Test
    void repairsOneSimpleMalformedJsonResponse() {
        responseContent.set("{\"title\":\"Repaired result\",\"confidence_level\":\"MEDIUM\",}");

        OpenAiPaperUnderstanding result = service().understand(packet());

        assertThat(result.title()).isEqualTo("Repaired result");
        assertThat(result.confidenceLevel()).isEqualTo("MEDIUM");
    }

    private OllamaPaperUnderstandingService service() {
        OllamaPaperUnderstandingService service = new OllamaPaperUnderstandingService(objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(service, "model", "qwen3:14b");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 10L);
        ReflectionTestUtils.setField(service, "temperature", 0.1);
        ReflectionTestUtils.setField(service, "numCtx", 8192);
        ReflectionTestUtils.setField(service, "maxInputChars", 16000);
        ReflectionTestUtils.setField(service, "useStructuredOutput", true);
        return service;
    }

    private Map<String, Object> validUnderstanding() {
        return Map.ofEntries(
                Map.entry("title", "Generic inspection paper"),
                Map.entry("abstract_summary", "Summary"),
                Map.entry("authors", List.of("A. Author")),
                Map.entry("year", 2026),
                Map.entry("research_problem", "Inspection results are difficult to review consistently."),
                Map.entry("target_users_or_domain", "quality inspection teams"),
                Map.entry("method", "a multimodal inspection method"),
                Map.entry("proposed_system_or_method", "a paper-specific inspection assistant"),
                Map.entry("key_implementation", "an inspection assistant"),
                Map.entry("key_contribution", "consistent review support"),
                Map.entry("important_system_components", List.of("inspection interface")),
                Map.entry("input_output_relationship", "sensor observations produce a highlighted review result"),
                Map.entry("why_it_matters", "supports consistent review"),
                Map.entry("likely_application_scenario", "inspection team reviewing an item"),
                Map.entry("visualizable_entities", List.of("operator", "inspection interface", "item")),
                Map.entry("visualizable_interactions", List.of("operator reviews highlighted findings")),
                Map.entry("visualizable_environment", "quality inspection workspace"),
                Map.entry("visible_interface_or_device", "inspection interface"),
                Map.entry("visible_output", "highlighted review result"),
                Map.entry("must_show_elements", List.of("operator", "inspection interface", "review result")),
                Map.entry("must_avoid_elements", List.of("generic AI wallpaper")),
                Map.entry("forbidden_visual_elements", List.of("fake logo")),
                Map.entry("warnings", List.of()),
                Map.entry("confidence_level", "HIGH")
        );
    }

    private PaperEvidencePacket packet() {
        String evidence = "Title: Generic inspection paper\nImplementation evidence:\n- compact evidence";
        return new PaperEvidencePacket(
                "Generic inspection paper", "Summary", List.of(), 2026, null, null, List.of(),
                List.of(), List.of(), List.of("compact evidence"), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                evidence, evidence.length(), List.of()
        );
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
