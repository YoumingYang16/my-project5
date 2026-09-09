package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses the canonical n8n paper-understanding workflow when available. */
@Service
public class N8nPaperUnderstandingService implements PaperUnderstandingProvider {
    private static final Logger logger = LoggerFactory.getLogger(N8nPaperUnderstandingService.class);
    private static final ProxySelector DIRECT_PROXY_SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // The caller reports the HTTP failure with paper-understanding context.
        }
    };
    private final ObjectMapper objectMapper;
    private final DeepSeekStructuredResponseClient deepSeekClient;

    @Value("${n8n.paper-understanding.enabled:false}")
    private boolean enabled;
    @Value("${n8n.paper-understanding.url:http://127.0.0.1:5678/webhook/website-paper-understanding}")
    private String endpoint;
    @Value("${n8n.paper-understanding.timeout-seconds:300}")
    private long timeoutSeconds;
    @Value("${n8n.paper-understanding.connect-timeout-seconds:10}")
    private long connectTimeoutSeconds;
    @Value("${n8n.paper-understanding.token:}")
    private String token;
    @Value("${n8n.paper-understanding.max-content-chars:24000}")
    private int maxContentChars;

    public N8nPaperUnderstandingService(ObjectMapper objectMapper, DeepSeekStructuredResponseClient deepSeekClient) {
        this.objectMapper = objectMapper;
        this.deepSeekClient = deepSeekClient;
    }

    @Override public String providerName() { return "N8N"; }
    @Override public String modelName() { return "My workflow 3 / DeepSeek"; }
    @Override public boolean isEnabled() { return enabled && endpoint != null && !endpoint.isBlank(); }
    @Override public boolean isAvailable() { return isEnabled(); }

    @Override
    public OpenAiPaperUnderstanding understand(PaperEvidencePacket evidencePacket) {
        if (!isEnabled()) throw new AiCoverWorkflowException("n8n paper understanding is disabled.");
        try {
            String userTaskToken = deepSeekClient.issueProxyTokenForCurrentUser();
            if (userTaskToken.isBlank()) throw new AiCoverWorkflowException("DeepSeek is not configured for this user. Add your DeepSeek API key in Settings.");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userTaskToken", userTaskToken);
            payload.put("briefOnly", true);
            payload.put("inputSource", "website-brief");
            payload.put("paperId", null);
            payload.put("title", evidencePacket.title());
            payload.put("abstract", evidencePacket.abstractText());
            payload.put("content", paperContent(evidencePacket));
            payload.put("evidenceHighlights", evidencePacket.compactEvidenceText());
            payload.put("contentSource", "pdf-full-text-with-structured-evidence");
            payload.put("figures", evidencePacket.figureCaptions().stream().map(caption -> Map.of("caption", caption)).toList());
            payload.put("audience", "public");
            payload.put("communicationGoal", "show-application");
            payload.put("imageStyle", "editorial");

            long startedAt = System.nanoTime();
            logger.info(
                    "n8n paper understanding request started: endpoint={}, contentCharacters={}, evidenceCharacters={}",
                    endpoint.trim(),
                    String.valueOf(payload.get("content")).length(),
                    evidencePacket.compactEvidenceText().length()
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.trim()))
                    .timeout(Duration.ofSeconds(Math.max(30, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .header("X-Website-Paper-Understanding-Token", token == null ? "" : token.trim())
                   .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpClient directClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(3, connectTimeoutSeconds)))
                    .proxy(DIRECT_PROXY_SELECTOR)
                    .build();
            HttpResponse<String> response = directClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            logger.info(
                    "n8n paper understanding request completed: status={}, durationMs={}, responseCharacters={}",
                    response.statusCode(), durationMs, response.body() == null ? 0 : response.body().length()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiCoverWorkflowException("n8n paper understanding returned HTTP " + response.statusCode() + ".");
            }
            JsonNode data = objectMapper.readTree(response.body());
            if (data.has("data")) data = data.get("data");
            if (data.has("body")) data = data.get("body");
            if (data.has("understanding")) data = data.get("understanding");
            if (data.has("result")) data = data.get("result");
            if (data.has("error")) {
                String message = data.path("error").path("message").asText("DeepSeek paper understanding failed.");
                throw new AiCoverWorkflowException("n8n/DeepSeek failed: " + AiCoverDiagnostics.sanitize(message));
            }
            return objectMapper.convertValue(data, OpenAiPaperUnderstanding.class);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiCoverWorkflowException("n8n paper understanding was interrupted.", ex);
        } catch (AiCoverWorkflowException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiCoverWorkflowException("n8n paper understanding request failed: " + ex.getMessage(), ex);
        }
    }

    String paperContent(PaperEvidencePacket evidencePacket) {
        List<com.heritage.platform.dto.ai.EvidenceSpan> pdfPages = evidencePacket.evidenceSpans().stream()
                .filter(span -> "PDF_TEXT".equalsIgnoreCase(span.source()))
                .filter(span -> span.text() != null && !span.text().isBlank())
                .sorted(Comparator.comparing(
                        com.heritage.platform.dto.ai.EvidenceSpan::pageNumber,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
        StringBuilder content = new StringBuilder();
        for (com.heritage.platform.dto.ai.EvidenceSpan page : pdfPages) {
            if (content.length() > 0) content.append("\n\n");
            if (page.pageNumber() != null) {
                content.append("[PDF page ").append(page.pageNumber()).append("]\n");
            }
            content.append(page.text().trim());
        }
        if (content.length() == 0) {
            content.append(evidencePacket.compactEvidenceText());
        }
        int limit = maxContentChars <= 0 ? 50000 : maxContentChars;
        if (content.length() <= limit) return content.toString();
        int headLength = Math.max(1, limit * 4 / 5);
        int tailLength = Math.max(1, limit - headLength - 32);
        return content.substring(0, headLength)
                + "\n\n[PDF text truncated]\n\n"
                + content.substring(content.length() - tailLength);
    }
}
