package com.heritage.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.heritage.platform.service.ai.DeepSeekStructuredResponseClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/n8n")
public class N8nDeepSeekProxyController {
    private final DeepSeekStructuredResponseClient client;

    public N8nDeepSeekProxyController(DeepSeekStructuredResponseClient client) {
        this.client = client;
    }

    @PostMapping(value = "/deepseek", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> proxy(
            @RequestHeader(value = "X-Website-DeepSeek-Task-Token", required = false) String taskToken,
            @RequestBody JsonNode requestBody
    ) {
        if (taskToken == null || taskToken.isBlank()) {
            return ResponseEntity.status(401).body("{\"error\":{\"message\":\"Unauthorized.\"}}");
        }
        try {
            String result = client.proxyCompletion(taskToken, requestBody);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() != null && ex.getMessage().contains("timed out")
                    ? "DeepSeek request timed out."
                    : "DeepSeek proxy unavailable.";
            return ResponseEntity.status(502).body("{\"error\":{\"message\":\"" + message + "\"}}");
        }
    }
}