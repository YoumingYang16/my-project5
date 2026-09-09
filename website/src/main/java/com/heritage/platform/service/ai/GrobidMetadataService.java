package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.GrobidPaperDocument;
import com.heritage.platform.service.CrossrefMetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GrobidMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(GrobidMetadataService.class);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2}|2100)\\b");

    @Value("${grobid.enabled:true}")
    private boolean grobidEnabled;

    @Value("${grobid.url:${grobid.base-url:http://localhost:8070}}")
    private String grobidUrl;

    @Value("${grobid.timeout-seconds:30}")
    private long timeoutSeconds;

    public GrobidMetadataService(CrossrefMetadataService ignored) {
        // DOI lookup is intentionally centralized in DoiEvidenceEnrichmentService so every
        // PaperEvidencePacket (cover and social copy) receives the same enrichment pass.
    }

    public boolean isEnabled() {
        return grobidEnabled;
    }

    public GrobidMetadata extract(Path pdfPath) {
        if (!grobidEnabled) {
            return GrobidMetadata.unavailable("GROBID is disabled.");
        }
        try {
            logger.info("GROBID header extraction started: fileName={}, fileSizeBytes={}", pdfPath.getFileName(), Files.size(pdfPath));
            String teiXml = requestExtraction(pdfPath, "/api/processHeaderDocument");
            GrobidMetadata metadata = parseTei(teiXml);
            logger.info(
                    "GROBID header extraction succeeded: titlePresent={}, authorCount={}, year={}",
                    metadata.title() != null,
                    metadata.authors().size(),
                    metadata.year()
            );
            return metadata;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warn("GROBID metadata extraction was interrupted.");
            return GrobidMetadata.unavailable("GROBID metadata extraction was interrupted.");
        } catch (Exception ex) {
            logger.warn("GROBID metadata extraction failed: {}", AiCoverDiagnostics.safeExceptionSummary(ex), ex);
            return GrobidMetadata.unavailable("GROBID is unavailable. Continuing without helper metadata.");
        }
    }

    public GrobidPaperDocument extractDocument(Path pdfPath) {
        if (!grobidEnabled) {
            GrobidMetadata unavailable = GrobidMetadata.unavailable("GROBID is disabled.");
            return new GrobidPaperDocument(unavailable, null, unavailable.warnings());
        }
        try {
            logger.info("GROBID full-text extraction started: fileName={}, fileSizeBytes={}", pdfPath.getFileName(), Files.size(pdfPath));
            String teiXml = requestExtraction(pdfPath, "/api/processFulltextDocument");
            GrobidMetadata metadata = parseTei(teiXml);
            logger.info(
                    "GROBID full-text extraction succeeded: teiCharacters={}, titlePresent={}, authorCount={}, year={}",
                    teiXml.length(),
                    metadata.title() != null,
                    metadata.authors().size(),
                    metadata.year()
            );
            return new GrobidPaperDocument(metadata, teiXml, metadata.warnings());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            GrobidMetadata unavailable = GrobidMetadata.unavailable("GROBID full-text extraction was interrupted.");
            return new GrobidPaperDocument(unavailable, null, unavailable.warnings());
        } catch (Exception fullTextFailure) {
            logger.warn(
                    "GROBID full-text extraction failed; falling back to header extraction: {}",
                    AiCoverDiagnostics.safeExceptionSummary(fullTextFailure),
                    fullTextFailure
            );
            GrobidMetadata metadata = extract(pdfPath);
            List<String> warnings = new ArrayList<>(metadata.warnings());
            warnings.add("GROBID full-text grounding was unavailable; header metadata was used instead.");
            return new GrobidPaperDocument(metadata, null, List.copyOf(warnings));
        }
    }

    private String requestExtraction(Path pdfPath, String apiPath) throws IOException, InterruptedException {
        long effectiveTimeout = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        String boundary = "----HeritageGrobidBoundary" + UUID.randomUUID();
        URI endpoint = URI.create(trimTrailingSlash(grobidUrl) + apiPath);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(effectiveTimeout))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, pdfPath)))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(effectiveTimeout))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GROBID returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private byte[] buildMultipartBody(String boundary, Path pdfPath) throws IOException {
        String fileName = pdfPath.getFileName().toString();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"input\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write("Content-Type: application/pdf\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(Files.readAllBytes(pdfPath));
        // These coordinates allow downstream code to link figures, tables, references and formulas
        // back to the original PDF when the installed GROBID service supports them.
        for (String value : List.of("figure", "biblStruct", "formula", "ref")) {
            output.write(("\r\n--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"teiCoordinates\"\r\n\r\n" + value)
                    .getBytes(StandardCharsets.UTF_8));
        }
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    GrobidMetadata parseTei(String teiXml) throws Exception {
        List<String> warnings = new ArrayList<>();
        Document document = parseXml(teiXml);
        XPath xpath = XPathFactory.newInstance().newXPath();

        String title = firstCleanText(xpath, document,
                "(//*[local-name()='teiHeader']/*[local-name()='fileDesc']/*[local-name()='titleStmt']/*[local-name()='title'][normalize-space()])[1]");
        if (title == null) {
            title = firstCleanText(xpath, document,
                    "(//*[local-name()='sourceDesc']//*[local-name()='biblStruct']//*[local-name()='analytic']/*[local-name()='title'][normalize-space()])[1]");
        }

        String abstractText = firstCleanText(xpath, document,
                "(//*[local-name()='profileDesc']/*[local-name()='abstract'][normalize-space()])[1]");
        if (abstractText == null) {
            abstractText = firstCleanText(xpath, document, "(//*[local-name()='abstract'][normalize-space()])[1]");
        }
        if (looksLikeFigureCaption(abstractText)) {
            warnings.add("GROBID returned a figure caption as the abstract; a body introduction snippet was used instead.");
            abstractText = extractBodySummary(xpath, document);
        }

        List<String> authors = extractAuthors(xpath, document);
        Integer year = extractYear(xpath, document);
        String doi = firstCleanText(xpath, document,
                "(//*[local-name()='teiHeader']//*[local-name()='idno']"
                        + "[translate(@type, 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ')='DOI'][normalize-space()])[1]");
        String venue = firstCleanText(xpath, document,
                "(//*[local-name()='teiHeader']//*[local-name()='sourceDesc']//*[local-name()='monogr']"
                        + "/*[local-name()='title'][normalize-space()])[1]");
        List<String> keywords = extractKeywords(xpath, document);
        boolean available = title != null || abstractText != null || !authors.isEmpty() || year != null;
        if (!available) {
            warnings.add("GROBID returned no usable title, abstract, authors, or year.");
        }
        return new GrobidMetadata(
                title,
                abstractText,
                authors,
                year,
                venue,
                doi,
                keywords,
                "GROBID",
                available,
                warnings
        );
    }

    private Document parseXml(String teiXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        setFeatureSafely(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureSafely(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureSafely(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureSafely(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(teiXml == null ? "" : teiXml)));
    }

    private List<String> extractAuthors(XPath xpath, Document document) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(
                "//*[local-name()='sourceDesc']/*[local-name()='biblStruct']/*[local-name()='analytic']/*[local-name()='author']",
                document,
                XPathConstants.NODESET
        );
        if (nodes.getLength() == 0) {
            nodes = (NodeList) xpath.evaluate(
                    "//*[local-name()='teiHeader']//*[local-name()='author'][*[local-name()='persName']]",
                    document,
                    XPathConstants.NODESET
            );
        }

        Set<String> authors = new LinkedHashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String author = authorNameFromNode(nodes.item(i));
            if (author != null) {
                authors.add(author);
            }
        }
        return List.copyOf(authors);
    }

    private List<String> extractKeywords(XPath xpath, Document document) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(
                "//*[local-name()='teiHeader']//*[local-name()='textClass']//*[local-name()='keywords']"
                        + "//*[local-name()='term'][normalize-space()]",
                document,
                XPathConstants.NODESET
        );
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (int index = 0; index < nodes.getLength() && keywords.size() < 20; index++) {
            String value = normalizeWhitespace(nodes.item(index).getTextContent());
            if (value != null && value.length() <= 120) {
                keywords.add(value);
            }
        }
        return List.copyOf(keywords);
    }

    private String authorNameFromNode(Node authorNode) {
        Node persName = firstDescendant(authorNode, "persName");
        if (persName == null) {
            return null;
        }
        List<String> forenames = descendantTexts(persName, "forename");
        String surname = firstDescendantText(persName, "surname");
        String combined = normalizeWhitespace((String.join(" ", forenames) + " " + (surname == null ? "" : surname)).trim());
        String name = combined == null ? normalizeWhitespace(persName.getTextContent()) : combined;
        return looksLikeAffiliation(name) ? null : name;
    }

    private Integer extractYear(XPath xpath, Document document) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(
                "//*[local-name()='teiHeader']//*[local-name()='sourceDesc']/*[local-name()='biblStruct']"
                        + "//*[local-name()='imprint']/*[local-name()='date']"
                        + " | //*[local-name()='teiHeader']//*[local-name()='publicationStmt']/*[local-name()='date']",
                document,
                XPathConstants.NODESET
        );
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            Node when = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("when");
            Integer year = parseYear(when == null ? null : when.getTextContent());
            if (isAcceptableYear(year)) {
                return year;
            }
            year = parseYear(node.getTextContent());
            if (isAcceptableYear(year)) {
                return year;
            }
        }
        return null;
    }

    private String extractBodySummary(XPath xpath, Document document) throws Exception {
        String summary = firstCleanText(xpath, document,
                "(//*[local-name()='text']/*[local-name()='body']/*[local-name()='div']"
                        + "[contains(translate(normalize-space(*[local-name()='head'][1]), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'abstract')]"
                        + "/*[local-name()='p'][normalize-space()])[1]");
        if (summary == null) {
            summary = firstCleanText(xpath, document,
                    "(//*[local-name()='text']/*[local-name()='body']/*[local-name()='div']"
                            + "[contains(translate(normalize-space(*[local-name()='head'][1]), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'introduction')]"
                            + "/*[local-name()='p'][normalize-space()])[1]");
        }
        if (summary == null) {
            summary = firstCleanText(xpath, document,
                    "(//*[local-name()='text']/*[local-name()='body']//*[local-name()='p'][normalize-space()])[1]");
        }
        return truncate(summary, 1200);
    }

    private boolean looksLikeFigureCaption(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.matches("^(figure|fig\\.)\\s*\\d+[.:].*")
                || (lower.startsWith("figure ") && (lower.contains("screenshot") || lower.contains("demonstration")));
    }

    private boolean looksLikeAffiliation(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("university")
                || lower.contains("department of")
                || lower.contains("school of")
                || lower.contains("institute of")
                || lower.contains("laboratory")
                || lower.contains("research center");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    private String firstCleanText(XPath xpath, Document document, String expression) throws Exception {
        Node node = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
        return normalizeWhitespace(node == null ? null : node.getTextContent());
    }

    private Node firstDescendant(Node node, String localName) {
        if (node == null) {
            return null;
        }
        if (localName.equals(node.getLocalName())) {
            return node;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node match = firstDescendant(children.item(i), localName);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private String firstDescendantText(Node node, String localName) {
        List<String> values = descendantTexts(node, localName);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<String> descendantTexts(Node node, String localName) {
        List<String> values = new ArrayList<>();
        collectDescendantTexts(node, localName, values);
        return values;
    }

    private void collectDescendantTexts(Node node, String localName, List<String> values) {
        if (node == null) {
            return;
        }
        if (localName.equals(node.getLocalName())) {
            String value = normalizeWhitespace(node.getTextContent());
            if (value != null) {
                values.add(value);
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectDescendantTexts(children.item(i), localName, values);
        }
    }

    private Integer parseYear(String value) {
        String text = value == null ? "" : value;
        Matcher matcher = YEAR_PATTERN.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private boolean isAcceptableYear(Integer year) {
        return year != null && year >= 1900 && year <= Year.now().getValue() + 1;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?<=\\p{L})-\\s+(?=\\p{Ll})", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String trimTrailingSlash(String value) {
        String cleaned = value == null || value.isBlank() ? "http://localhost:8070" : value.trim();
        return cleaned.endsWith("/") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }

    private void setFeatureSafely(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (ParserConfigurationException ex) {
            logger.debug("XML parser feature not supported: {}", feature);
        }
    }
}
