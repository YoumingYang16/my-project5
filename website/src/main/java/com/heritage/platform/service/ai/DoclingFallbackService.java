package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.PdfPageEvidence;
import com.heritage.platform.dto.ai.PdfTextEvidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional client for the repository's Docling parser service. */
@Service
public class DoclingFallbackService {
    private final ObjectMapper objectMapper;

    @Value("${docling.enabled:false}")
    private boolean enabled;
    @Value("${docling.url:http://localhost:8091}")
    private String baseUrl;
    @Value("${docling.timeout-seconds:120}")
    private long timeoutSeconds;

    public DoclingFallbackService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean shouldFallback(PdfTextEvidence pdf, String teiXml) {
        return enabled && ((!pdf.available()) || pdf.pages().isEmpty()) && (teiXml == null || teiXml.isBlank());
    }

    public PdfTextEvidence extract(Path pdfPath) {
        if (!enabled || pdfPath == null || !Files.isRegularFile(pdfPath)) {
            return PdfTextEvidence.unavailable("Docling fallback was not available.");
        }
        try {
            String boundary = "----DoclingBoundary" + UUID.randomUUID();
            byte[] file = Files.readAllBytes(pdfPath);
            byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + pdfPath.getFileName() + "\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes();
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes();
            byte[] body = new byte[prefix.length + file.length + suffix.length];
            System.arraycopy(prefix, 0, body, 0, prefix.length);
            System.arraycopy(file, 0, body, prefix.length, file.length);
            System.arraycopy(suffix, 0, body, prefix.length + file.length, suffix.length);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/parse"))
                    .timeout(Duration.ofSeconds(Math.max(30, timeoutSeconds)))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PdfTextEvidence.unavailable("Docling fallback returned HTTP " + response.statusCode() + ".");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("text").asText("").trim();
            List<PdfPageEvidence> pages = new ArrayList<>();
            for (JsonNode page : root.path("pages")) {
                String pageText = page.path("text").asText("").trim();
                if (!pageText.isBlank()) {
                    pages.add(new PdfPageEvidence(page.path("pageNumber").asInt(pages.size() + 1), pageText,
                            page.path("confidence").asDouble(0.7d)));
                }
            }
            boolean available = text.length() >= 40;
            return new PdfTextEvidence(pages.isEmpty() ? "" : pages.getFirst().text(), text, available,
                    available ? List.of("Docling/OCR fallback supplied structured text.")
                            : List.of("Docling fallback returned insufficient text."), pages);
        } catch (Exception ex) {
            return PdfTextEvidence.unavailable("Docling/OCR fallback failed; GROBID/PDF text evidence was retained.");
        }
    }
}
