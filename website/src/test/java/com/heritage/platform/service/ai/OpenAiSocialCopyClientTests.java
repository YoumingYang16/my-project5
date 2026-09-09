package com.heritage.platform.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiSocialCopyClientTests {

    @Test
    void retriesHttp429OnceThenReturnsStructuredJson() {
        OpenAiStructuredResponseClient delegate = mock(OpenAiStructuredResponseClient.class);
        OpenAiSocialCopyClient client = new OpenAiSocialCopyClient(delegate);
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "apiKey", "configured-key");
        ReflectionTestUtils.setField(client, "maxRetries", 1);
        ReflectionTestUtils.setField(client, "initialBackoffSeconds", 0L);
        ReflectionTestUtils.setField(client, "maxBackoffSeconds", 0L);
        when(delegate.requestJson(anyString(), eq("schema"), any(), eq(List.of())))
                .thenThrow(new SocialCopyProviderException(
                        "OPENAI", SocialCopyFailureReason.RATE_LIMITED, 429, 0L, "rate limited"
                ))
                .thenReturn("{\"ok\":true}");

        String result = client.requestJson("system", "user", "schema", Map.of());

        assertThat(result).isEqualTo("{\"ok\":true}");
        verify(delegate, times(2)).requestJson(anyString(), eq("schema"), any(), eq(List.of()));
    }
}
