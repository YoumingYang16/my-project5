package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingProviderResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperUnderstandingProviderChainTests {

    @Test
    void usesN8nFirstAndDoesNotCallDirectDeepSeek() {
        PaperUnderstandingProvider n8n = provider("N8N", "workflow-test");
        PaperUnderstandingProvider deepSeek = provider("DEEPSEEK", "deepseek-test");
        PaperUnderstandingProvider ollama = provider("OLLAMA", "qwen3:14b");
        PaperUnderstandingProvider deterministic = provider("EVIDENCE_FALLBACK", "evidence-fallback");
        when(n8n.understand(packet())).thenReturn(validUnderstanding());
        PaperUnderstandingProviderChain chain = chain(n8n, deepSeek, ollama, deterministic);

        PaperUnderstandingProviderResult result = chain.resolve(packet());

        assertThat(result.sourceProvider()).isEqualTo("N8N");
        assertThat(chain.priority()).containsExactly("n8n", "deepseek", "evidence_fallback");
        verify(n8n).understand(packet());
        verify(deepSeek, never()).understand(packet());
        verify(ollama, never()).isEnabled();
        verify(ollama, never()).understand(packet());
        verify(deterministic, never()).understand(packet());
    }

    @Test
    void n8nFailureFallsBackToDirectDeepSeekAndNeverCallsOllama() {
        PaperUnderstandingProvider n8n = provider("N8N", "workflow-test");
        PaperUnderstandingProvider deepSeek = provider("DEEPSEEK", "deepseek-test");
        PaperUnderstandingProvider ollama = provider("OLLAMA", "qwen3:14b");
        PaperUnderstandingProvider deterministic = provider("EVIDENCE_FALLBACK", "evidence-fallback");
        when(n8n.understand(packet())).thenThrow(new AiCoverWorkflowException("workflow timed out"));
        when(deepSeek.understand(packet())).thenReturn(validUnderstanding());
        PaperUnderstandingProviderChain chain = chain(n8n, deepSeek, ollama, deterministic);

        PaperUnderstandingProviderResult result = chain.resolve(packet());

        assertThat(result.sourceProvider()).isEqualTo("DEEPSEEK");
        assertThat(result.openAiFailureMessage()).contains("timed out");
        verify(ollama, never()).understand(packet());
        verify(deterministic, never()).understand(packet());
    }
    private PaperUnderstandingProviderChain chain(PaperUnderstandingProvider... providers) {
        return new PaperUnderstandingProviderChain(
                List.of(providers), new PaperUnderstandingSemanticValidator()
        );
    }

    private PaperUnderstandingProvider provider(String name, String model) {
        PaperUnderstandingProvider provider = mock(PaperUnderstandingProvider.class);
        when(provider.providerName()).thenReturn(name);
        when(provider.modelName()).thenReturn(model);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.isAvailable()).thenReturn(true);
        return provider;
    }

    private OpenAiPaperUnderstanding validUnderstanding() {
        return new OpenAiPaperUnderstanding(
                "Inspection assistant for industrial review",
                "The paper studies inconsistent industrial review and presents an inspection assistant.",
                List.of("A. Researcher"), 2026,
                "quality inspectors can miss inconsistent defects during manual review",
                "industrial quality inspectors",
                "a sensor-guided inspection workflow highlights likely defects for review",
                "an inspection assistant combines sensor input with a review interface",
                "the system supports more consistent defect review",
                List.of("sensor module", "inspection interface", "finding overlay"),
                "sensor observations produce highlighted findings for an inspector",
                "it helps inspectors review likely defects consistently", null,
                List.of("generic AI wallpaper", "unrelated laboratory"),
                "an inspector reviews a manufactured part with the assistant",
                List.of("inspector", "manufactured part", "inspection interface"),
                List.of("inspector positions the part and reviews highlighted findings"),
                "an industrial quality inspection station", List.of(), "HIGH",
                "Journal of Inspection Systems", "sensor-guided inspection assistant",
                "inspection workstation interface", "sensor observations of the manufactured part",
                "highlighted likely defects", "more consistent defect review",
                List.of("inspector", "inspection assistant", "industrial station", "highlighted findings"),
                List.of("generic technology poster"),
                "an industrial quality inspection station",
                "inspect a manufactured part and review highlighted likely defects",
                "the inspector positions the part and reviews the assistant's findings"
        );
    }

    private PaperEvidencePacket packet() {
        String body = "The authors propose a sensor-guided inspection assistant for industrial quality inspectors. "
                .repeat(4);
        String text = "Title: Inspection assistant for industrial review\nAbstract: " + body + "\nMethod: " + body;
        return new PaperEvidencePacket(
                "Inspection assistant for industrial review", body, List.of(), 2026, null, null, List.of(),
                List.of(body), List.of(body), List.of(body), List.of(body), List.of(body),
                List.of(), List.of(), List.of("inspection"), List.of(body),
                List.of("inspection interface"), text, text.length(), List.of()
        );
    }
}
