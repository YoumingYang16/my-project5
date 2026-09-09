package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeepSeekStructuredResponseClientTests {

    private HttpClient httpClient;
    private DeepSeekStructuredResponseClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        client = new DeepSeekStructuredResponseClient(new ObjectMapper(), httpClient);
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "apiKey", "secret-test-key");
        ReflectionTestUtils.setField(client, "maxRetries", 0);
        ReflectionTestUtils.setField(client, "initialBackoffSeconds", 0L);
        ReflectionTestUtils.setField(client, "maxBackoffSeconds", 0L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void callsOpenAiCompatibleChatCompletionsAndExtractsJsonContent() throws Exception {
        HttpResponse<String> response = response(
                200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}",
                Map.of()
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        String result = client.requestJson(
                "Return JSON.", "Create the copy as JSON.", "social_copy_compact_pipeline", Map.of()
        );

        assertThat(result).isEqualTo("{\"ok\":true}");
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.deepseek.com/chat/completions");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer secret-test-key");
    }

    @Test
    void runtimeApiKeyOverridesConfiguredKeyWithoutExposingIt() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", List.of())
        );
        client.configureRuntimeApiKey("runtime-secret-key-1234567890");
        HttpResponse<String> response = response(
                200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}",
                Map.of()
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        client.requestJson("Return JSON.", "JSON task", "schema", Map.of());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().headers().firstValue("Authorization"))
                .contains("Bearer runtime-secret-key-1234567890");
        assertThat(client.keySource()).isEqualTo("user-session");

        client.clearRuntimeApiKey();
        assertThat(client.keySource()).isEqualTo("none");
    }

    @Test
    void rejectsInvalidRuntimeApiKey() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", List.of())
        );
        assertThatThrownBy(() -> client.configureRuntimeApiKey("short key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid DeepSeek API key");
    }

    @Test
    void keepsRuntimeKeysIsolatedByAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", List.of())
        );
        client.configureRuntimeApiKey("alice-secret-key-1234567890");
        assertThat(client.isEnabled()).isTrue();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("bob", "n/a", List.of())
        );
        assertThat(client.isEnabled()).isFalse();
        assertThat(client.keySource()).isEqualTo("none");
    }

    @Test
    void marksHttp429AsRateLimitedAndParsesRetryAfter() throws Exception {
        HttpResponse<String> rateLimited = response(
                429, "{\"error\":{\"message\":\"slow down\"}}", Map.of("Retry-After", List.of("7"))
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(rateLimited);

        assertThatThrownBy(() -> client.requestJson("Return JSON.", "JSON task", "schema", Map.of()))
                .isInstanceOfSatisfying(SocialCopyProviderException.class, failure -> {
                    assertThat(failure.failureReason()).isEqualTo(SocialCopyFailureReason.RATE_LIMITED);
                    assertThat(failure.statusCode()).isEqualTo(429);
                    assertThat(failure.retryAfterSeconds()).isEqualTo(7L);
                    assertThat(failure.rateLimited()).isTrue();
                });
    }

    @Test
    void identifiesAnInsufficientCreditResponseWithoutRetryingAnotherProvider() throws Exception {
        HttpResponse<String> insufficientCredit = response(
                402, "{\"error\":{\"message\":\"insufficient balance\"}}", Map.of()
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(insufficientCredit);

        assertThatThrownBy(() -> client.requestJson("Return JSON.", "JSON task", "schema", Map.of()))
                .isInstanceOfSatisfying(SocialCopyProviderException.class, failure -> {
                    assertThat(failure.failureReason()).isEqualTo(SocialCopyFailureReason.INSUFFICIENT_CREDIT);
                    assertThat(failure.statusCode()).isEqualTo(402);
                });
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void retriesRateLimitOnceWithoutSpamming() throws Exception {
        ReflectionTestUtils.setField(client, "maxRetries", 1);
        HttpResponse<String> rateLimited = response(429, "{}", Map.of("Retry-After", List.of("0")));
        HttpResponse<String> succeeded = response(
                200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}",
                Map.of()
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rateLimited)
                .thenReturn(succeeded);

        assertThat(client.requestJson("Return JSON.", "JSON task", "schema", Map.of()))
                .isEqualTo("{\"ok\":true}");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }
}
