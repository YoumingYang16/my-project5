package com.heritage.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.service.ai.DeepSeekStructuredResponseClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class N8nDeepSeekProxyControllerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeepSeekStructuredResponseClient client = mock(DeepSeekStructuredResponseClient.class);
    private final N8nDeepSeekProxyController controller = new N8nDeepSeekProxyController(client);

    @Test
    void rejectsMissingUserTaskToken() {
        var response = controller.proxy(null, objectMapper.createObjectNode());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(client);
    }

    @Test
    void acceptsSingleUseUserTaskTokenWithoutSharedEnvironmentGate() {
        var body = objectMapper.createObjectNode().put("model", "deepseek-chat");
        when(client.proxyCompletion("single-use-token", body)).thenReturn("{\"ok\":true}");

        var response = controller.proxy("single-use-token", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"ok\":true}");
    }
}
