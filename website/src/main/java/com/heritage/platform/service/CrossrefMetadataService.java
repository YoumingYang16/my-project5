package com.heritage.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CrossrefMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(CrossrefMetadataService.class);

    @Value("${crossref.enabled:true}")
    private boolean crossrefEnabled;

    @Value("${crossref.base-url:https://api.crossref.org}")
    private String crossrefBaseUrl;

    @Value("${crossref.timeout-seconds:10}")
    private long timeoutSeconds;

    @Value("${crossref.mailto:}")
    private String crossrefMailto;

    private final ObjectMapper objectMapper;

    public CrossrefMetadataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<CrossrefMetadata> findByDoi(String doi) {
        String normalizedDoi = normalize(doi);
        if (!crossrefEnabled || normalizedDoi == null) {
            return Optional.empty();
        }

        try {
            URI endpoint = buildWorksUri(normalizedDoi);
            long effectiveTimeout = timeoutSeconds <= 0 ? 10 : timeoutSeconds;
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(effectiveTimeout))
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(effectiveTimeout))
                    .build();
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Crossref returned HTTP " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warn("Crossref metadata lookup was interrupted.");
            return Optional.empty();
        } catch (Exception ex) {
            logger.warn("Crossref metadata lookup failed for DOI {}.", normalizedDoi, ex);
            return Optional.empty();
        }
    }

    private URI buildWorksUri(String doi) {
        String encodedDoi = URLEncoder.encode(doi, StandardCharsets.UTF_8).replace("+", "%20");
        String endpoint = trimTrailingSlash(crossrefBaseUrl) + "/works/" + encodedDoi;
        String mailto = normalize(crossrefMailto);
        if (mailto != null) {
            endpoint += "?mailto=" + URLEncoder.encode(mailto, StandardCharsets.UTF_8).replace("+", "%20");
        }
        return URI.create(endpoint);
    }

    private Optional<CrossrefMetadata> parseResponse(String body) throws IOException {
        JsonNode message = objectMapper.readTree(body).path("message");
        if (message.isMissingNode() || message.isNull()) {
            return Optional.empty();
        }

        String title = joinTitleParts(
                firstArrayText(message, "title"),
                firstArrayText(message, "subtitle")
        );
        String authors = authors(message.path("author"));
        Integer year = firstYear(message, "issued", "published-print", "published-online", "published", "created");
        String venue = firstNonBlank(
                firstArrayText(message, "container-title"),
                textAt(message.path("event").path("name"))
        );
        String publisher = textAt(message.path("publisher"));
        String doi = textAt(message.path("DOI"));
        String subjects = arrayValues(message.path("subject"));
        String abstractText = stripMarkup(textAt(message.path("abstract")));
        String canonicalUrl = firstNonBlank(textAt(message.path("URL")), textAt(message.path("resource").path("primary").path("URL")));
        String citationMetadata = citationMetadata(message);

        if (title == null && authors == null && year == null && venue == null && publisher == null
                && doi == null && subjects == null && abstractText == null && canonicalUrl == null) {
            return Optional.empty();
        }
        return Optional.of(new CrossrefMetadata(
                title, authors, year, venue, publisher, doi, subjects,
                abstractText, canonicalUrl, citationMetadata
        ));
    }

    private String citationMetadata(JsonNode message) {
        List<String> values = new ArrayList<>();
        addCitationValue(values, "type", textAt(message.path("type")));
        addCitationValue(values, "referenceCount", integerText(message.path("reference-count")));
        addCitationValue(values, "citationCount", integerText(message.path("is-referenced-by-count")));
        return values.isEmpty() ? null : String.join("; ", values);
    }

    private void addCitationValue(List<String> values, String label, String value) {
        if (value != null) {
            values.add(label + "=" + value);
        }
    }

    private String integerText(JsonNode node) {
        return node != null && node.canConvertToInt() ? String.valueOf(node.asInt()) : null;
    }

    private String stripMarkup(String value) {
        if (value == null) {
            return null;
        }
        return normalize(value.replaceAll("<[^>]+>", " "));
    }

    private String authors(JsonNode authorsNode) {
        if (!authorsNode.isArray()) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode author : authorsNode) {
            String given = textAt(author.path("given"));
            String family = textAt(author.path("family"));
            String literal = textAt(author.path("name"));
            String name = firstNonBlank(normalize((given == null ? "" : given) + " " + (family == null ? "" : family)), literal);
            if (name != null) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private Integer firstYear(JsonNode message, String... fields) {
        for (String field : fields) {
            Integer year = yearFromDateParts(message.path(field).path("date-parts"));
            if (year != null) {
                return year;
            }
        }
        return null;
    }

    private Integer yearFromDateParts(JsonNode dateParts) {
        if (!dateParts.isArray() || dateParts.isEmpty()) {
            return null;
        }
        JsonNode firstPart = dateParts.get(0);
        if (!firstPart.isArray() || firstPart.isEmpty() || !firstPart.get(0).canConvertToInt()) {
            return null;
        }
        return firstPart.get(0).asInt();
    }

    private String firstArrayText(JsonNode node, String field) {
        JsonNode values = node.path(field);
        if (!values.isArray()) {
            return null;
        }
        for (JsonNode value : values) {
            String text = textAt(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String arrayValues(JsonNode values) {
        if (!values.isArray()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (JsonNode value : values) {
            String text = textAt(value);
            if (text != null && !items.contains(text)) {
                items.add(text);
            }
        }
        return items.isEmpty() ? null : String.join(", ", items);
    }

    private String joinTitleParts(String title, String subtitle) {
        if (title == null) {
            return subtitle;
        }
        if (subtitle == null || title.equalsIgnoreCase(subtitle)) {
            return title;
        }
        return title + ": " + subtitle;
    }

    private String userAgent() {
        String mailto = normalize(crossrefMailto);
        return mailto == null ? "HeritagePlatform/1.0" : "HeritagePlatform/1.0 (mailto:" + mailto + ")";
    }

    private String textAt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return normalize(node.asText());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String trimTrailingSlash(String value) {
        String cleaned = value == null || value.isBlank() ? "https://api.crossref.org" : value.trim();
        return cleaned.endsWith("/") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }

    public record CrossrefMetadata(
            String title,
            String authors,
            Integer year,
            String venue,
            String publisher,
            String doi,
            String subjects,
            String abstractText,
            String canonicalUrl,
            String citationMetadata
    ) {
        public CrossrefMetadata(
                String title,
                String authors,
                Integer year,
                String venue,
                String publisher,
                String doi,
                String subjects
        ) {
            this(title, authors, year, venue, publisher, doi, subjects, null, null, null);
        }
    }
}
