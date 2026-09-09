package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialCopyProviderRouterTests {

    private OpenAiSocialCopyService openAiPipeline;
    private SocialCopyStructuredJsonClient openAiClient;
    private DeepSeekSocialCopyService deepSeek;
    private EvidenceFallbackSocialCopyService fallback;

    @BeforeEach
    void setUp() {
        openAiPipeline = mock(OpenAiSocialCopyService.class);
        openAiClient = mock(SocialCopyStructuredJsonClient.class);
        deepSeek = mock(DeepSeekSocialCopyService.class);
        fallback = mock(EvidenceFallbackSocialCopyService.class);
        when(deepSeek.isEnabled()).thenReturn(true);
    }

    @Test
    void alwaysUsesDeepSeekAndNeverFallsBackToOpenAiOrTemplates() {
        SocialCopyProviderRouter router = router();
        when(deepSeek.generate(any(), any(), any(), any(), any())).thenReturn(copy("DeepSeek polished copy"));

        SocialCopyProviderRouter.RoutedSocialCopy result = router.generate(null, null, "zh");

        assertThat(result.source()).isEqualTo("DEEPSEEK");
        assertThat(result.attempts()).extracting(attempt -> attempt.provider()).containsExactly("DEEPSEEK");
        verify(deepSeek).generate(any(), any(), any(), any(), any());
        verify(openAiPipeline, never()).generateCompact(any(), any(), any(), any());
        verify(fallback, never()).generate(any(), any(), any());
    }

    @Test
    void returnsTheDeepSeekFailureInsteadOfGeneratingAReplacementCopy() {
        SocialCopyProviderRouter router = router();
        when(deepSeek.generate(any(), any(), any(), any(), any())).thenThrow(
                new SocialCopyProviderException(
                        "DEEPSEEK", SocialCopyFailureReason.INSUFFICIENT_CREDIT, 402, null,
                        "DeepSeek account credit is insufficient."
                )
        );

        assertThatThrownBy(() -> router.generate(null, null, "zh"))
                .isInstanceOfSatisfying(SocialCopyProviderException.class, failure -> {
                    assertThat(failure.failureReason()).isEqualTo(SocialCopyFailureReason.INSUFFICIENT_CREDIT);
                    assertThat(failure.statusCode()).isEqualTo(402);
                });
        verify(openAiPipeline, never()).generateCompact(any(), any(), any(), any());
        verify(fallback, never()).generate(any(), any(), any());
    }

    @Test
    void retriesOnlyDeepSeekWhenItsJsonNeedsOneRepairAttempt() {
        SocialCopyProviderRouter router = router();
        ReflectionTestUtils.setField(router, "maxRetries", 1);
        ReflectionTestUtils.setField(router, "initialBackoffSeconds", 0L);
        ReflectionTestUtils.setField(router, "maxBackoffSeconds", 0L);
        when(deepSeek.generate(any(), any(), any(), any(), any()))
                .thenThrow(new SocialCopyProviderException(
                        "DEEPSEEK", SocialCopyFailureReason.JSON_PARSE_FAILURE, 200, null,
                        "Invalid JSON response."
                ))
                .thenReturn(copy("DeepSeek repaired copy"));

        SocialCopyProviderRouter.RoutedSocialCopy result = router.generate(null, null, "zh");

        assertThat(result.source()).isEqualTo("DEEPSEEK");
        verify(deepSeek, times(2)).generate(any(), any(), any(), any(), any());
        verify(openAiPipeline, never()).generateCompact(any(), any(), any(), any());
    }

    private SocialCopyProviderRouter router() {
        return new SocialCopyProviderRouter(
                openAiPipeline, openAiClient, deepSeek, fallback, true, "deepseek"
        );
    }

    private OpenAiSocialCopyService.GeneratedSocialCopy copy(String value) {
        return new OpenAiSocialCopyService.GeneratedSocialCopy(
                Map.of("engaging", value, "concise", value, "professional", value, "academic", value),
                List.of("Research", "Heritage", "Interaction")
        );
    }
}
