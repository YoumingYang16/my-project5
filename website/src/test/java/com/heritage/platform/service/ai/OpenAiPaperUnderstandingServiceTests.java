package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPaperUnderstandingServiceTests {

    @Test
    void requestContainsOnlyCompactTextEvidenceAndStructuredOutputSchema() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiPaperUnderstandingService service = new OpenAiPaperUnderstandingService(objectMapper);
        ReflectionTestUtils.setField(service, "model", "gpt-5.4-mini");
        ReflectionTestUtils.setField(service, "reasoningEffort", "none");
        ReflectionTestUtils.setField(service, "maxOutputTokens", 4000);
        PaperEvidencePacket packet = packet("Title: Generic paper\nMethod evidence:\n- compact method evidence");

        Map<String, Object> requestBody = service.requestBody(packet);
        String json = objectMapper.writeValueAsString(requestBody);

        assertThat(json).contains("input_text", "compact method evidence", "visualizable_entities");
        assertThat(json).contains("\"model\":\"gpt-5.4-mini\"")
                .contains("\"reasoning\":{\"effort\":\"none\"}")
                .contains("\"max_output_tokens\":4000");
        assertThat(json).doesNotContain("input_file", "file_data", "data:application/pdf", "base64");
    }

    private PaperEvidencePacket packet(String evidence) {
        return new PaperEvidencePacket(
                "Generic paper", "Summary", List.of(), 2026, null, null, List.of(),
                List.of(), List.of("compact method evidence"), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                evidence, evidence.length(), List.of()
        );
    }
}
