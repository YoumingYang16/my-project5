package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaperAiQualityValidatorTests {

    private final PaperAiQualityValidator validator = new PaperAiQualityValidator();

    @Test
    void acceptsCompactFullTextEvidenceWithAConcreteProposedSystem() {
        assertThat(validator.hasSufficientEvidence(evidence("sensor-guided inspection assistant"))).isTrue();
    }

    @Test
    void rejectsTitleOnlyAndTest1EvidenceBeforeOpenAiIsCalled() {
        String text = "Title: test1";
        PaperEvidencePacket weak = new PaperEvidencePacket(
                "test1", null, List.of(), null, null, null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                text, text.length(), List.of()
        );

        assertThatThrownBy(() -> validator.requireSufficientEvidence(weak))
                .isInstanceOf(AiCoverWorkflowException.class)
                .hasMessageContaining("paper content extraction is insufficient");
    }

    private PaperEvidencePacket evidence(String system) {
        String body = "The authors propose a " + system
                + " that combines sensor observations with a review interface for quality inspectors. ";
        body = body.repeat(5);
        return new PaperEvidencePacket(
                "Industrial inspection assistant", body, List.of(), 2026, null, null, List.of("inspection"),
                List.of(body), List.of(body), List.of(body), List.of(body), List.of(body), List.of(), List.of(),
                List.of("inspection"), List.of(body), List.of("review interface"), body, body.length(), List.of()
        );
    }
}
