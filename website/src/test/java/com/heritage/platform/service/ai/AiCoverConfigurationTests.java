package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiCoverConfigurationTests {

    @Test
    void defaultsUseDirectDeepSeekForUnderstandingAndCopyWithN8nFallback() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            properties.load(input);
        }

        assertThat(properties.getProperty("openai.enabled")).isEqualTo("${OPENAI_ENABLED:true}");
        assertThat(properties.getProperty("grobid.enabled")).isEqualTo("${GROBID_ENABLED:true}");
        assertThat(properties.getProperty("ollama.enabled")).isEqualTo("${OLLAMA_ENABLED:false}");
        assertThat(properties.getProperty("ai-understanding.provider-priority"))
                .isEqualTo("${AI_UNDERSTANDING_PROVIDER_PRIORITY:deepseek,n8n,evidence_fallback}");
        assertThat(properties.getProperty("ai.social-copy.provider"))
                .isEqualTo("${AI_SOCIAL_COPY_PROVIDER:deepseek}");
        assertThat(properties.getProperty("ai.social-copy.require-openai-for-polished"))
                .isEqualTo("false");
        assertThat(properties.getProperty("ai.social-copy.allow-evidence-fallback-when-openai-unavailable"))
                .isEqualTo("false");
        assertThat(properties.getProperty("ai.social-copy.provider-priority"))
                .isEqualTo("deepseek");
        assertThat(properties.getProperty("ai.social-copy.compact-llm-mode"))
                .isEqualTo("${AI_SOCIAL_COPY_COMPACT_LLM_MODE:true}");
        assertThat(properties.getProperty("deepseek.enabled")).isEqualTo("${DEEPSEEK_ENABLED:true}");
        assertThat(properties.getProperty("deepseek.api-key")).isEqualTo("${DEEPSEEK_API_KEY:}");
        assertThat(properties.getProperty("deepseek.base-url"))
                .isEqualTo("${DEEPSEEK_BASE_URL:https://api.deepseek.com}");
        assertThat(properties.getProperty("deepseek.model"))
                .isEqualTo("${DEEPSEEK_MODEL:deepseek-v4-flash}");
        assertThat(properties.getProperty("deepseek.timeout-seconds"))
                .isEqualTo("${DEEPSEEK_TIMEOUT_SECONDS:240}");
        assertThat(properties.getProperty("deepseek.paper-understanding.max-content-chars"))
                .isEqualTo("${DEEPSEEK_PAPER_UNDERSTANDING_MAX_CONTENT_CHARS:24000}");
        assertThat(properties.getProperty("deepseek.max-input-chars"))
                .isEqualTo("${DEEPSEEK_MAX_INPUT_CHARS:16000}");
        assertThat(properties.getProperty("deepseek.paper-understanding-client-max-input-chars"))
                .isEqualTo("${DEEPSEEK_PAPER_UNDERSTANDING_CLIENT_MAX_INPUT_CHARS:52000}");
        assertThat(properties.getProperty("deepseek.proxy-token-ttl-seconds"))
                .isEqualTo("${DEEPSEEK_PROXY_TOKEN_TTL_SECONDS:900}");
        assertThat(properties.getProperty("n8n.paper-understanding.timeout-seconds"))
                .isEqualTo("${N8N_PAPER_UNDERSTANDING_TIMEOUT_SECONDS:90}");
        assertThat(properties.getProperty("n8n.paper-understanding.connect-timeout-seconds"))
                .isEqualTo("${N8N_PAPER_UNDERSTANDING_CONNECT_TIMEOUT_SECONDS:10}");
        assertThat(properties).doesNotContainKeys(
                "ai.require-llm-understanding-for-cover",
                "ai.require-llm-understanding-for-social-copy",
                "ai.disable-weak-fallback-output"
        );
        assertThat(properties.getProperty("crossref.enabled")).isEqualTo("${CROSSREF_ENABLED:true}");
        assertThat(properties.getProperty("ai-cover.pdf-text-max-pages"))
                .isEqualTo("${AI_COVER_PDF_TEXT_MAX_PAGES:0}");
        assertThat(properties.getProperty("ai-cover.ranking-mode"))
                .isEqualTo("${AI_COVER_RANKING_MODE:disabled}");
    }

    @Test
    void usesConfiguredInternalAndReturnCandidateCounts() {
        ComfyUiImageGenerationService generationService = new ComfyUiImageGenerationService(
                new ObjectMapper(),
                mock(UploadPathService.class)
        );
        ReflectionTestUtils.setField(generationService, "internalCandidateCount", 3);
        ReflectionTestUtils.setField(generationService, "returnCandidateCount", 3);

        CandidateSceneRankingService rankingService = new CandidateSceneRankingService(
                new ObjectMapper(),
                mock(OpenAiStructuredResponseClient.class),
                mock(UploadPathService.class)
        );
        ReflectionTestUtils.setField(rankingService, "returnCandidates", 3);

        assertThat(generationService.effectiveInternalCandidateCount()).isEqualTo(3);
        assertThat(generationService.effectiveReturnCandidateCount()).isEqualTo(3);
        assertThat(rankingService.effectiveReturnCandidateCount()).isEqualTo(3);
    }

    @Test
    void invalidCandidateCountsFallBackToOne() {
        ComfyUiImageGenerationService generationService = new ComfyUiImageGenerationService(
                new ObjectMapper(),
                mock(UploadPathService.class)
        );
        ReflectionTestUtils.setField(generationService, "internalCandidateCount", 0);
        ReflectionTestUtils.setField(generationService, "returnCandidateCount", 0);

        assertThat(generationService.effectiveInternalCandidateCount()).isEqualTo(1);
        assertThat(generationService.effectiveReturnCandidateCount()).isEqualTo(1);
    }
}
