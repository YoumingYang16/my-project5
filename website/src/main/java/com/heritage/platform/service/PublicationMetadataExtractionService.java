package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.dto.response.PublicationMetadataCandidateResponse;
import com.heritage.platform.dto.response.PublicationMetadataFieldResponse;
import com.heritage.platform.service.CrossrefMetadataService.CrossrefMetadata;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PublicationMetadataExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(PublicationMetadataExtractionService.class);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2}|2100)\\b");
    private static final Pattern DOI_PATTERN = Pattern.compile(
            "(?i)(?:https?://(?:dx\\.)?doi\\.org/|doi\\s*:?\\s*)?(10\\.\\d{4,9}/[-._;()/:A-Z0-9]+)"
    );
    private static final Pattern BAD_AUTHOR_PATTERN = Pattern.compile(
            "(?i)\\b(university|school|department|institute|laboratory|faculty|college|academy|china|usa|u\\.s\\.a\\.?|uk|email|e-mail|campus)\\b|@|https?://"
    );
    private static final Pattern BAD_TITLE_PATTERN = Pattern.compile(
            "(?i)^(abstract|keywords?|key words|index terms|references|acm reference format|ccs concepts)$|\\b(copyright|permission to make digital|all rights reserved)\\b"
    );
    private static final Pattern BAD_ABSTRACT_PREFIX_PATTERN = Pattern.compile(
            "(?i)^(figure|fig\\.|table|permission|copyright|acm reference format|ccs concepts|keywords?|key words|references)\\b"
    );

    @Value("${grobid.enabled:false}")
    private boolean grobidEnabled;

    @Value("${grobid.base-url:http://localhost:8070}")
    private String grobidBaseUrl;

    @Value("${grobid.timeout-seconds:30}")
    private long timeoutSeconds;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private final AuthContextService authContextService;
    private final CrossrefMetadataService crossrefMetadataService;

    public PublicationMetadataExtractionService(
            AuthContextService authContextService,
            CrossrefMetadataService crossrefMetadataService
    ) {
        this.authContextService = authContextService;
        this.crossrefMetadataService = crossrefMetadataService;
    }

    public PublicationMetadataCandidateResponse extractFromUploadedPdf(String pdfUrl) {
        authContextService.requireActiveUser();
        Path pdfPath = resolveUploadedPdf(pdfUrl);

        if (!grobidEnabled) {
            return PublicationMetadataCandidateResponse.unavailable(
                    "Automatic metadata extraction is unavailable. Please fill the metadata manually."
            );
        }

        try {
            String teiXml = requestHeaderExtraction(pdfPath);
            return parseTei(teiXml);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warn("GROBID metadata extraction was interrupted.", ex);
            return extractionFailed("Automatic metadata extraction is unavailable. Please fill the metadata manually.");
        } catch (Exception ex) {
            logger.warn("GROBID metadata extraction failed.", ex);
            return extractionFailed("Automatic metadata extraction is unavailable. Please fill the metadata manually.");
        }
    }

    private Path resolveUploadedPdf(String pdfUrl) {
        String value = pdfUrl == null ? "" : pdfUrl.trim();
        if (value.isEmpty()) {
            throw new BadRequestException("PDF URL is required.");
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            throw new BadRequestException("Only locally uploaded PDF files can be extracted.");
        }
        if (!value.startsWith("/uploads/")) {
            throw new BadRequestException("Only files uploaded through the platform can be extracted.");
        }

        String relativeName = value.substring("/uploads/".length()).replace("\\", "/");
        Path uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        Path pdfPath = uploadRoot.resolve(relativeName).normalize();
        if (!pdfPath.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid uploaded PDF path.");
        }
        if (!Files.isRegularFile(pdfPath)) {
            throw new BadRequestException("The uploaded PDF could not be found.");
        }
        if (!pdfPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files can be extracted.");
        }
        return pdfPath;
    }

    private String requestHeaderExtraction(Path pdfPath) throws IOException, InterruptedException {
        long effectiveTimeout = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        String boundary = "----HeritageGrobidBoundary" + UUID.randomUUID();
        URI endpoint = URI.create(trimTrailingSlash(grobidBaseUrl) + "/api/processHeaderDocument");

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(effectiveTimeout))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, pdfPath)))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(effectiveTimeout))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
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
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private PublicationMetadataCandidateResponse parseTei(String teiXml) throws Exception {
        Document document = parseXml(teiXml);
        XPath xpath = XPathFactory.newInstance().newXPath();
        String documentText = documentText(document);
        String firstPageText = firstPageText(documentText);
        List<String> warnings = new ArrayList<>();

        List<Candidate> doiCandidates = new ArrayList<>();
        addDoiCandidate(doiCandidates, firstText(xpath, document,
                "(//*[local-name()='idno' and translate(@type, 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ')='DOI'][normalize-space()])[1]"),
                "GROBID", "HIGH", warnings);
        addDoiCandidate(doiCandidates, findDoi(firstPageText), "PDF_TEXT", "MEDIUM", warnings);
        addDoiCandidate(doiCandidates, findDoi(teiXml), "REGEX", "LOW", warnings);
        Candidate doiCandidate = select(doiCandidates);
        String doi = stringValue(doiCandidate);

        Optional<CrossrefMetadata> crossref = crossrefMetadataService.findByDoi(doi);
        crossref.map(CrossrefMetadata::doi)
                .ifPresent(value -> addDoiCandidate(doiCandidates, value, "CROSSREF", "HIGH", warnings));
        doiCandidate = select(doiCandidates);
        doi = stringValue(doiCandidate);

        List<Candidate> titleCandidates = new ArrayList<>();
        crossref.map(CrossrefMetadata::title)
                .ifPresent(value -> addTitleCandidate(titleCandidates, value, "CROSSREF", "HIGH", warnings));
        addTitleCandidate(titleCandidates, firstText(xpath, document,
                "(//*[local-name()='teiHeader']/*[local-name()='fileDesc']/*[local-name()='titleStmt']/*[local-name()='title'][normalize-space()])[1]"),
                "GROBID", "HIGH", warnings);
        addTitleCandidate(titleCandidates, firstText(xpath, document,
                "(//*[local-name()='sourceDesc']//*[local-name()='biblStruct']//*[local-name()='analytic']/*[local-name()='title'][normalize-space()])[1]"),
                "GROBID", "MEDIUM", warnings);
        addTitleCandidate(titleCandidates, firstLikelyTitleLine(firstPageText), "PDF_TEXT", "LOW", warnings);
        Candidate titleCandidate = select(titleCandidates);
        String title = stringValue(titleCandidate);

        List<Candidate> authorCandidates = new ArrayList<>();
        crossref.map(CrossrefMetadata::authors)
                .ifPresent(value -> addAuthorsCandidate(authorCandidates, value, "CROSSREF", "HIGH", warnings));
        addAuthorsCandidate(authorCandidates, extractAuthors(xpath, document), "GROBID", "HIGH", warnings);
        addAuthorsCandidate(authorCandidates, firstPageAuthors(firstPageText), "PDF_TEXT", "LOW", warnings);
        Candidate authorsCandidate = select(authorCandidates);
        String authors = stringValue(authorsCandidate);

        List<Candidate> yearCandidates = new ArrayList<>();
        crossref.map(CrossrefMetadata::year)
                .ifPresent(value -> addYearCandidate(yearCandidates, value, "CROSSREF", "HIGH", warnings));
        for (String dateText : extractDateTexts(xpath, document)) {
            addYearCandidate(yearCandidates, parseYear(dateText), "GROBID", "MEDIUM", warnings);
        }
        addYearCandidate(yearCandidates, nearbyYear(firstPageText, doi, title), "PDF_TEXT", "MEDIUM", warnings);
        addYearCandidate(yearCandidates, safeFirstPageYear(firstPageText), "REGEX", "LOW", warnings);
        Candidate yearCandidate = select(yearCandidates);
        Integer year = integerValue(yearCandidate);

        List<Candidate> venueCandidates = new ArrayList<>();
        crossref.map(CrossrefMetadata::venue)
                .ifPresent(value -> addVenueCandidate(venueCandidates, value, "CROSSREF", "HIGH", warnings));
        crossref.map(CrossrefMetadata::publisher)
                .ifPresent(value -> addVenueCandidate(venueCandidates, value, "CROSSREF", "MEDIUM", warnings));
        for (String venueText : extractGrobidVenues(xpath, document)) {
            addVenueCandidate(venueCandidates, venueText, "GROBID", "MEDIUM", warnings);
        }
        addVenueCandidate(venueCandidates, venueLine(firstPageText), "PDF_TEXT", "LOW", warnings);
        Candidate venueCandidate = select(venueCandidates);
        String venue = stringValue(venueCandidate);

        List<Candidate> abstractCandidates = new ArrayList<>();
        addAbstractCandidate(abstractCandidates, firstText(xpath, document,
                "(//*[local-name()='profileDesc']/*[local-name()='abstract'][normalize-space()])[1]"),
                "GROBID", "HIGH", warnings);
        addAbstractCandidate(abstractCandidates, firstText(xpath, document,
                "(//*[local-name()='abstract'][normalize-space()])[1]"),
                "GROBID", "MEDIUM", warnings);
        addAbstractCandidate(abstractCandidates, sectionAfterLabel(firstPageText, "Abstract",
                "CCS Concepts", "Keywords", "Key Words", "Index Terms", "Introduction", "1 Introduction",
                "1. Introduction", "ACM Reference Format"),
                "PDF_TEXT", "LOW", warnings);
        Candidate abstractCandidate = select(abstractCandidates);
        String abstractText = stringValue(abstractCandidate);

        List<Candidate> keywordCandidates = new ArrayList<>();
        addKeywordsCandidate(keywordCandidates, extractKeywords(xpath, document), "GROBID", "HIGH", warnings);
        addKeywordsCandidate(keywordCandidates, sectionAfterLabel(firstPageText, "Keywords",
                "ACM Reference Format", "CCS Concepts", "Introduction", "1 Introduction", "1. Introduction"),
                "PDF_TEXT", "MEDIUM", warnings);
        addKeywordsCandidate(keywordCandidates, sectionAfterLabel(firstPageText, "Index Terms",
                "ACM Reference Format", "CCS Concepts", "Introduction", "1 Introduction", "1. Introduction"),
                "PDF_TEXT", "MEDIUM", warnings);
        Candidate keywordsCandidate = select(keywordCandidates);
        String keywords = stringValue(keywordsCandidate);

        List<Candidate> researchAreaCandidates = new ArrayList<>();
        addResearchAreaCandidate(researchAreaCandidates, sectionAfterLabel(firstPageText, "CCS Concepts",
                "Keywords", "Key Words", "Index Terms", "ACM Reference Format", "Introduction",
                "1 Introduction", "1. Introduction"),
                "PDF_TEXT", "HIGH", warnings);
        crossref.map(CrossrefMetadata::subjects)
                .ifPresent(value -> addResearchAreaCandidate(researchAreaCandidates, value, "CROSSREF", "MEDIUM", warnings));
        Candidate researchAreaCandidate = select(researchAreaCandidates);
        String researchArea = stringValue(researchAreaCandidate);

        Map<String, PublicationMetadataFieldResponse> fields = new LinkedHashMap<>();
        putField(fields, "title", titleCandidate);
        putField(fields, "authors", authorsCandidate);
        putField(fields, "year", yearCandidate);
        putField(fields, "venue", venueCandidate);
        putField(fields, "abstractText", abstractCandidate);
        putField(fields, "keywords", keywordsCandidate);
        putField(fields, "researchArea", researchAreaCandidate);
        putField(fields, "doi", doiCandidate);

        String bibtex = buildFallbackBibtex(title, authors, year, venue, doi);
        boolean hasCandidate = !fields.isEmpty();
        return new PublicationMetadataCandidateResponse(
                title,
                authors,
                year,
                venue,
                abstractText,
                keywords,
                researchArea,
                doi,
                bibtex,
                fields,
                warnings,
                true,
                hasCandidate,
                hasCandidate
                        ? "Metadata candidates extracted. Please review them before saving."
                        : "No clear metadata candidates were found. Please fill the metadata manually."
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

        return factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(teiXml == null ? "" : teiXml)));
    }

    private String extractAuthors(XPath xpath, Document document) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(
                "//*[local-name()='sourceDesc']//*[local-name()='biblStruct']//*[local-name()='analytic']/*[local-name()='author']",
                document,
                XPathConstants.NODESET
        );
        if (nodes.getLength() == 0) {
            nodes = (NodeList) xpath.evaluate("//*[local-name()='author']", document, XPathConstants.NODESET);
        }

        List<String> authors = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String author = authorNameFromPersName(nodes.item(i));
            if (author != null && !authors.contains(author)) {
                authors.add(author);
            }
        }
        return authors.isEmpty() ? null : String.join(", ", authors);
    }

    private String authorNameFromPersName(Node authorNode) {
        Node persName = firstDescendant(authorNode, "persName");
        if (persName == null) {
            return null;
        }
        List<String> forenames = descendantTexts(persName, "forename");
        String surname = firstDescendantText(persName, "surname");
        String combined = normalizeWhitespace((String.join(" ", forenames) + " " + (surname == null ? "" : surname)).trim());
        return combined != null ? combined : normalizeWhitespace(persName.getTextContent());
    }

    private String extractKeywords(XPath xpath, Document document) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(
                "//*[local-name()='textClass']//*[local-name()='keywords']//*[local-name()='term']",
                document,
                XPathConstants.NODESET
        );
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = normalizeWhitespace(nodes.item(i).getTextContent());
            if (value != null && !terms.contains(value)) {
                terms.add(value);
            }
        }
        return terms.isEmpty() ? null : String.join(", ", terms);
    }

    private List<String> extractDateTexts(XPath xpath, Document document) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate("//*[local-name()='date']", document, XPathConstants.NODESET);
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            Node when = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("when");
            addUnique(dates, when == null ? null : when.getTextContent());
            addUnique(dates, node.getTextContent());
        }
        return dates;
    }

    private List<String> extractGrobidVenues(XPath xpath, Document document) throws Exception {
        List<String> venues = new ArrayList<>();
        addUnique(venues, firstText(xpath, document,
                "(//*[local-name()='sourceDesc']//*[local-name()='biblStruct']//*[local-name()='monogr']/*[local-name()='title'][normalize-space()])[1]"));
        addUnique(venues, firstText(xpath, document,
                "(//*[local-name()='publicationStmt']/*[local-name()='publisher'][normalize-space()])[1]"));
        addUnique(venues, firstText(xpath, document,
                "(//*[local-name()='meeting']//*[local-name()='title'][normalize-space()])[1]"));
        addUnique(venues, firstText(xpath, document,
                "(//*[local-name()='meeting'][normalize-space()])[1]"));
        return venues;
    }

    private void addDoiCandidate(
            List<Candidate> candidates,
            String raw,
            String source,
            String confidence,
            List<String> warnings
    ) {
        String doi = normalizeDoi(raw);
        if (doi == null) {
            warnRejected(raw, "DOI", source, "not a recognizable DOI", warnings);
            return;
        }
        candidates.add(new Candidate("doi", doi, source, confidence, cleanupWarning(raw, doi)));
    }

    private void addTitleCandidate(
            List<Candidate> candidates,
            String raw,
            String source,
            String confidence,
            List<String> warnings
    ) {
        String title = normalizeWhitespace(raw);
        if (title == null) {
            return;
        }
        String reason = invalidTitleReason(title);
        if (reason != null) {
            warnRejected(raw, "title", source, reason, warnings);
            return;
        }
        candidates.add(new Candidate("title", title, source, confidence, cleanupWarning(raw, title)));
    }

    private void addAuthorsCandidate(
            List<Candidate> candidates,
            String raw,
            String source,
            String confidence,
            List<String> warnings
    ) {
        String authors = normalizeAuthors(raw);
        if (authors == null) {
            warnRejected(raw, "authors", source, "not a clean personal-name list", warnings);
            return;
        }
        candidates.add(new Candidate("authors", authors, source, confidence, cleanupWarning(raw, authors)));
    }

    private void addYearCandidate(
            List<Candidate> candidates,
            Integer year,
            String source,
            String confidence,
            List<String> warnings
    ) {
        if (year == null) {
            return;
        }
        int maxYear = Year.now().getValue() + 1;
        if (year < 1990 || year > maxYear) {
            warn(warnings, "Rejected year from " + source + ": outside the accepted publication range.");
            return;
        }
        candidates.add(new Candidate("year", year, source, confidence, null));
    }

    private void addVenueCandidate(
            List<Candidate> candidates,
            String raw,
            String source,
            String confidence,
            List<String> warnings
    ) {
        String venue = normalizeWhitespace(raw);
        if (venue == null) {
            return;
        }
        if (venue.length() > 220 || BAD_TITLE_PATTERN.matcher(venue).find()) {
            warnRejected(raw, "venue", source, "looks like a section label or boilerplate", warnings);
            return;
        }
        candidates.add(new Candidate("venue", venue, source, confidence, cleanupWarning(raw, venue)));
    }

    private void addAbstractCandidate(
            List<Candidate> candidates,
            String raw,
            String source,
            String confidence,
            List<String> warnings
    ) {
        String abstractText = normalizeAbstract(raw);
        if (abstractText == null) {
            warnRejected(raw, "abstract", source, "too short or likely boilerplate/caption text", warnings);
            return;
        }
        candidates.add(new Candidate("abstractText", abstractText, source, confidence, cleanupWarning(raw, abstractText)));
    }

    private void addKeywordsCandidate(
            List<Candidate> candidates,
            String raw,
            String source,
            String confidence,
            List<String> warnings
    ) {
        String keywords = normalizeKeywords(raw);
        if (keywords == null) {
            warnRejected(raw, "keywords", source, "not a clean keyword list", warnings);
            return;
        }
        candidates.add(new Candidate("keywords", keywords, source, confidence, cleanupWarning(raw, keywords)));
    }

    private void addResearchAreaCandidate(
            List<Candidate> candidates,
            String raw,
            String source,
            String confidence,
            List<String> warnings
    ) {
        String researchArea = normalizeResearchArea(raw);
        if (researchArea == null) {
            return;
        }
        candidates.add(new Candidate("researchArea", researchArea, source, confidence, cleanupWarning(raw, researchArea)));
    }

    private Candidate select(List<Candidate> candidates) {
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void putField(Map<String, PublicationMetadataFieldResponse> fields, String fieldName, Candidate candidate) {
        if (candidate != null) {
            fields.put(fieldName, new PublicationMetadataFieldResponse(
                    candidate.value(),
                    candidate.source(),
                    candidate.confidence(),
                    candidate.warning()
            ));
        }
    }

    private String firstText(XPath xpath, Document document, String expression) throws Exception {
        Node node = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
        return node == null ? null : normalizeWhitespace(node.getTextContent());
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

    private Integer nearbyYear(String text, String doi, String title) {
        List<String> lines = cleanLines(text);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!isPublicationContextLine(line, doi, title)) {
                continue;
            }
            for (int offset = -1; offset <= 1; offset++) {
                int index = i + offset;
                if (index >= 0 && index < lines.size()) {
                    Integer year = parseYear(lines.get(index));
                    if (isAcceptableYear(year)) {
                        return year;
                    }
                }
            }
        }
        return null;
    }

    private Integer safeFirstPageYear(String text) {
        for (String line : cleanLines(firstChars(text, 5000))) {
            if (line.toLowerCase(Locale.ROOT).contains("references")) {
                return null;
            }
            Integer year = parseYear(line);
            if (isAcceptableYear(year)) {
                return year;
            }
        }
        return null;
    }

    private String venueLine(String text) {
        for (String line : cleanLines(text)) {
            String lower = line.toLowerCase(Locale.ROOT);
            if ((lower.contains("conference") || lower.contains("journal") || lower.contains("proceedings")
                    || lower.contains("symposium") || lower.contains("workshop") || lower.contains("transactions")
                    || lower.contains("acm") || lower.contains("ieee") || lower.contains("springer")
                    || lower.contains("elsevier")) && !lower.contains("doi")) {
                return line;
            }
        }
        return null;
    }

    private String firstLikelyTitleLine(String text) {
        for (String line : cleanLines(text)) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (line.length() < 12 || line.length() > 220 || lower.contains("doi")
                    || lower.contains("http") || lower.contains("@") || BAD_AUTHOR_PATTERN.matcher(line).find()) {
                continue;
            }
            if (invalidTitleReason(line) == null && wordCount(line) >= 3 && wordCount(line) <= 35) {
                return line;
            }
        }
        return null;
    }

    private String firstPageAuthors(String text) {
        for (String line : cleanLines(firstChars(text, 3000))) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (line.length() > 180 || lower.contains("doi") || lower.contains("abstract")
                    || lower.contains("keywords") || lower.contains("copyright")) {
                continue;
            }
            if (line.contains(",") || lower.contains(" and ")) {
                String authors = normalizeAuthors(line);
                if (authors != null && wordCount(authors) <= 18) {
                    return authors;
                }
            }
        }
        return null;
    }

    private String sectionAfterLabel(String text, String label, String... stopLabels) {
        String value = text == null ? "" : text;
        Matcher matcher = Pattern.compile("(?i)\\b" + Pattern.quote(label) + "\\b\\s*[:.\\-]?\\s*").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.end();
        int end = value.length();
        String lower = value.toLowerCase(Locale.ROOT);
        for (String stopLabel : stopLabels) {
            int index = lower.indexOf(stopLabel.toLowerCase(Locale.ROOT), start);
            if (index > start && index < end) {
                end = index;
            }
        }
        return value.substring(start, end);
    }

    private boolean isPublicationContextLine(String line, String doi, String title) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("references")) {
            return false;
        }
        String normalizedDoi = normalizeDoi(doi);
        if (normalizedDoi != null && lower.contains(normalizedDoi.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String normalizedTitle = normalizeWhitespace(title);
        if (normalizedTitle != null && lower.contains(normalizedTitle.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return lower.contains("conference") || lower.contains("journal") || lower.contains("proceedings")
                || lower.contains("copyright") || lower.contains("acm") || lower.contains("ieee")
                || lower.contains("springer") || lower.contains("elsevier");
    }

    private String invalidTitleReason(String title) {
        String normalized = normalizeWhitespace(title);
        if (normalized == null) {
            return "empty";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (BAD_TITLE_PATTERN.matcher(normalized).find()) {
            return "section label or copyright boilerplate";
        }
        boolean conferenceOnly = (lower.startsWith("proceedings") || lower.startsWith("conference")
                || lower.startsWith("symposium") || lower.startsWith("workshop")
                || lower.startsWith("international conference"))
                && wordCount(normalized) <= 14;
        if (conferenceOnly) {
            return "conference or proceedings name only";
        }
        return null;
    }

    private String normalizeAuthors(String raw) {
        String value = normalizeWhitespace(raw);
        if (value == null) {
            return null;
        }
        String prepared = value.replaceAll("(?i)\\s+and\\s+", ", ");
        String[] parts = prepared.split("\\s*[,;]\\s*");
        Set<String> authors = new LinkedHashSet<>();
        for (String part : parts) {
            String name = normalizeWhitespace(part);
            if (isPersonalName(name)) {
                authors.add(name);
            }
        }
        return authors.isEmpty() ? null : String.join(", ", authors);
    }

    private boolean isPersonalName(String name) {
        if (name == null || BAD_AUTHOR_PATTERN.matcher(name).find() || name.matches(".*\\d.*")) {
            return false;
        }
        int words = wordCount(name);
        if (words < 1 || words > 6 || name.length() > 80) {
            return false;
        }
        return name.matches("[\\p{L}][\\p{L} .'-]*");
    }

    private String normalizeAbstract(String raw) {
        String value = normalizeWhitespace(raw);
        if (value == null) {
            return null;
        }
        value = value.replaceFirst("(?i)^abstract\\s*[:.\\-]?\\s*", "");
        value = cutBeforeAny(value, "CCS Concepts", "Keywords", "Key Words", "Index Terms",
                "Introduction", "1 Introduction", "1. Introduction", "ACM Reference Format");
        value = normalizeWhitespace(value);
        if (value == null || value.length() < 80 || wordCount(value) < 12
                || BAD_ABSTRACT_PREFIX_PATTERN.matcher(value).find()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("permission to make digital") || lower.contains("acm reference format")) {
            return null;
        }
        return value;
    }

    private String normalizeKeywords(String raw) {
        String value = normalizeWhitespace(raw);
        if (value == null) {
            return null;
        }
        value = value.replaceFirst("(?i)^(keywords|key words|additional key words and phrases|index terms)\\s*[:.\\-]?\\s*", "");
        value = cutBeforeAny(value, "CCS Concepts", "ACM Reference Format", "Abstract", "Introduction",
                "1 Introduction", "1. Introduction");
        String[] parts = value.split("\\s*[,;|]\\s*");
        List<String> keywords = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            String keyword = normalizeKeywordTerm(part);
            if (keyword == null) {
                continue;
            }
            String key = keyword.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                keywords.add(keyword);
            }
        }
        return keywords.isEmpty() ? null : String.join(", ", keywords);
    }

    private String normalizeKeywordTerm(String raw) {
        String term = normalizeWhitespace(raw);
        if (term == null) {
            return null;
        }
        term = term.replaceAll("^[.:\\-\\s]+|[.:;\\-\\s]+$", "");
        if (term.length() < 2 || term.length() > 80) {
            return null;
        }
        String lower = term.toLowerCase(Locale.ROOT);
        if (lower.contains("ccs concepts") || lower.contains("acm reference format")
                || lower.contains("copyright") || lower.contains("permission")) {
            return null;
        }
        return term;
    }

    private String normalizeResearchArea(String raw) {
        String value = normalizeWhitespace(raw);
        if (value == null) {
            return null;
        }
        value = value.replaceFirst("(?i)^ccs concepts\\s*[:.\\-]?\\s*", "");
        value = cutBeforeAny(value, "Keywords", "Key Words", "Index Terms", "ACM Reference Format",
                "Abstract", "Introduction", "1 Introduction", "1. Introduction");
        String[] parts = value.split("\\s*[,;]\\s*");
        List<String> areas = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            String area = normalizeResearchAreaTerm(part);
            if (area != null && seen.add(area.toLowerCase(Locale.ROOT))) {
                areas.add(area);
            }
        }
        return areas.isEmpty() ? null : String.join(", ", areas);
    }

    private String normalizeResearchAreaTerm(String raw) {
        String term = normalizeWhitespace(raw);
        if (term == null) {
            return null;
        }
        term = term.replaceAll("^[.:\\-\\s]+|[.:;\\-\\s]+$", "");
        if (term.length() < 2 || term.length() > 150) {
            return null;
        }
        String lower = term.toLowerCase(Locale.ROOT);
        if (lower.contains("acm reference format") || lower.contains("copyright")
                || lower.contains("permission")) {
            return null;
        }
        return term;
    }

    private String cutBeforeAny(String value, String... labels) {
        String lower = value.toLowerCase(Locale.ROOT);
        int end = value.length();
        for (String label : labels) {
            int index = lower.indexOf(label.toLowerCase(Locale.ROOT));
            if (index > 0 && index < end) {
                end = index;
            }
        }
        return value.substring(0, end);
    }

    private Integer parseYear(String value) {
        String text = value == null ? "" : value;
        Matcher matcher = YEAR_PATTERN.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private boolean isAcceptableYear(Integer year) {
        return year != null && year >= 1990 && year <= Year.now().getValue() + 1;
    }

    private String findDoi(String value) {
        String text = value == null ? "" : value;
        Matcher matcher = DOI_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeDoi(String value) {
        String text = normalizeWhitespace(value);
        if (text == null) {
            return null;
        }
        Matcher matcher = DOI_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String doi = matcher.group(1)
                .replaceAll("[\\].,;:)}]+$", "")
                .toLowerCase(Locale.ROOT);
        return doi.isBlank() ? null : doi;
    }

    private String documentText(Document document) {
        Node root = document == null ? null : document.getDocumentElement();
        if (root == null) {
            return "";
        }
        String text = root.getTextContent() == null ? "" : root.getTextContent();
        return text.replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String firstPageText(String text) {
        return firstChars(text, 8000);
    }

    private String firstChars(String text, int maxChars) {
        String value = text == null ? "" : text;
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private List<String> cleanLines(String text) {
        String[] lines = (text == null ? "" : text).split("\\R+");
        List<String> values = new ArrayList<>();
        for (String line : lines) {
            String cleaned = normalizeWhitespace(line);
            if (cleaned != null) {
                values.add(cleaned);
            }
        }
        return values;
    }

    private int wordCount(String value) {
        String normalized = normalizeWhitespace(value);
        return normalized == null ? 0 : normalized.split("\\s+").length;
    }

    private String buildFallbackBibtex(String title, String authors, Integer year, String venue, String doi) {
        if (title == null && authors == null && year == null && venue == null && doi == null) {
            return null;
        }
        String safeTitle = title == null ? "Untitled paper" : title;
        String safeAuthors = authors == null ? "Unknown Author" : authors;
        String safeYear = year == null ? "n.d." : String.valueOf(year);
        String key = (safeAuthors.split(",")[0] + safeYear + safeTitle)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (key.isBlank()) {
            key = "paper";
        }

        List<String> fields = new ArrayList<>();
        fields.add("  title = {" + escapeBibtex(safeTitle) + "}");
        fields.add("  author = {" + escapeBibtex(safeAuthors) + "}");
        fields.add("  year = {" + escapeBibtex(safeYear) + "}");
        if (venue != null) {
            fields.add("  journal = {" + escapeBibtex(venue) + "}");
        }
        if (doi != null) {
            fields.add("  doi = {" + escapeBibtex(doi) + "}");
        }
        return "@article{" + key.substring(0, Math.min(key.length(), 48)) + ",\n"
                + String.join(",\n", fields)
                + "\n}";
    }

    private String escapeBibtex(String value) {
        return value.replace("{", "\\{").replace("}", "\\}");
    }

    private String stringValue(Candidate candidate) {
        return candidate == null || candidate.value() == null ? null : String.valueOf(candidate.value());
    }

    private Integer integerValue(Candidate candidate) {
        if (candidate == null || candidate.value() == null) {
            return null;
        }
        if (candidate.value() instanceof Integer value) {
            return value;
        }
        return parseYear(String.valueOf(candidate.value()));
    }

    private String cleanupWarning(String raw, String cleaned) {
        String rawNormalized = normalizeWhitespace(raw);
        if (rawNormalized == null || rawNormalized.equals(cleaned)) {
            return null;
        }
        return "Cleaned or normalized before suggesting.";
    }

    private void warnRejected(String raw, String field, String source, String reason, List<String> warnings) {
        if (normalizeWhitespace(raw) != null) {
            warn(warnings, "Rejected " + field + " from " + source + ": " + reason + ".");
        }
    }

    private void warn(List<String> warnings, String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private void addUnique(List<String> values, String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized != null && !values.contains(normalized)) {
            values.add(normalized);
        }
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String trimTrailingSlash(String value) {
        String cleaned = value == null || value.isBlank() ? "http://localhost:8070" : value.trim();
        return cleaned.endsWith("/") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }

    private PublicationMetadataCandidateResponse extractionFailed(String message) {
        return new PublicationMetadataCandidateResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                true,
                false,
                message
        );
    }

    private void setFeatureSafely(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (ParserConfigurationException ex) {
            logger.debug("XML parser feature not supported: {}", feature);
        }
    }

    private record Candidate(
            String field,
            Object value,
            String source,
            String confidence,
            String warning
    ) {
    }
}
