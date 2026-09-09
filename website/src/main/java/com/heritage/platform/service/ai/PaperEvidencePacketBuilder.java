package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.DoiEnrichmentResult;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.GrobidPaperDocument;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PdfTextEvidence;
import com.heritage.platform.dto.ai.EvidenceSpan;
import com.heritage.platform.dto.ai.PdfPageEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PaperEvidencePacketBuilder {

    private static final Logger logger = LoggerFactory.getLogger(PaperEvidencePacketBuilder.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}-]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "about", "after", "also", "among", "based", "because", "been", "before", "between",
            "both", "could", "data", "during", "each", "from", "have", "into", "more", "most",
            "other", "paper", "proposed", "research", "results", "show", "shows", "study", "such",
            "system", "than", "that", "their", "there", "these", "they", "this", "through", "using",
            "were", "which", "while", "with", "within", "would", "the", "and", "for", "are", "was",
            "has", "had", "not", "can", "our", "its", "figure", "figures", "section", "sections"
    );

    @Value("${ai-cover.evidence-max-chars:16000}")
    private int maxEvidenceChars;

    @Value("${ai-cover.section-snippet-max-chars:2000}")
    private int maxSectionSnippetChars;

    @Value("${ai-cover.max-figure-captions:8}")
    private int maxFigureCaptions;

    @Value("${ai-cover.max-table-captions:4}")
    private int maxTableCaptions;

    public PaperEvidencePacket build(GrobidPaperDocument grobidDocument) {
        return build(grobidDocument, null, null);
    }

    public PaperEvidencePacket build(
            GrobidPaperDocument grobidDocument,
            DoiEnrichmentResult enrichment,
            PdfTextEvidence pdfEvidence
    ) {
        GrobidMetadata metadata = enrichment != null && enrichment.metadata() != null
                ? enrichment.metadata()
                : grobidDocument == null || grobidDocument.metadata() == null
                ? GrobidMetadata.unavailable("GROBID metadata was not available.")
                : grobidDocument.metadata();
        List<String> warnings = new ArrayList<>();
        if (enrichment != null) {
            warnings.addAll(enrichment.warnings());
        }
        if (pdfEvidence != null) {
            warnings.addAll(pdfEvidence.warnings());
        }
        List<String> problem = new ArrayList<>();
        List<String> method = new ArrayList<>();
        List<String> implementation = new ArrayList<>();
        List<String> evaluation = new ArrayList<>();
        List<String> conclusion = new ArrayList<>();
        List<String> figures = new ArrayList<>();
        List<String> tables = new ArrayList<>();
        List<String> teiKeywords = new ArrayList<>();

        String teiXml = grobidDocument == null ? null : grobidDocument.teiXml();
        if (teiXml != null && !teiXml.isBlank()) {
            try {
                Document document = parseXml(teiXml);
                extractSections(document, problem, method, implementation, evaluation, conclusion);
                extractCaptions(document, figures, tables);
                extractKeywords(document, teiKeywords);
            } catch (Exception ex) {
                logger.warn("Paper evidence packet TEI parsing failed: {}", AiCoverDiagnostics.safeExceptionSummary(ex));
                warnings.add("Some structured paper evidence could not be extracted; available metadata was used.");
            }
        } else {
            warnings.add("GROBID full-text evidence was unavailable; PDF text and bibliographic evidence were used when available.");
        }

        List<String> pdfSnippets = pdfEvidenceSnippets(pdfEvidence);
        addPdfSnippets(pdfSnippets, problem, method, implementation, evaluation, conclusion);

        List<String> keywords = distinctLimited(merge(
                metadata.keywords(), teiKeywords,
                enrichment == null ? List.of() : enrichment.subjectTerms()
        ), 20);
        List<String> allSnippets = merge(problem, method, implementation, evaluation, conclusion, figures, tables);
        List<String> domainTerms = extractSalientTerms(metadata.title(), metadata.abstractText(), keywords, allSnippets);
        List<String> applicationClues = rankedApplicationClues(
                merge(method, implementation, evaluation, conclusion, figures),
                metadata.title(),
                10
        );
        List<String> usefulFigures = figures.stream().filter(this::isUsefulApplicationFigure).toList();
        List<String> visualClues = distinctLimited(
                merge(usefulFigures, applicationClues, keywords, meaningfulTerms(domainTerms)),
                16
        );

        int evidenceLimit = maxEvidenceChars <= 0 ? 16000 : maxEvidenceChars;
        String compactText = buildCompactText(
                metadata,
                keywords,
                problem,
                method,
                implementation,
                evaluation,
                conclusion,
                figures,
                tables,
                domainTerms,
                applicationClues,
                visualClues,
                enrichment,
                pdfSnippets,
                evidenceLimit
        );
        logger.info(
                "Paper evidence packet built: characters={}, sources={}, problemSnippets={}, methodSnippets={}, implementationSnippets={}, evaluationSnippets={}, figureCaptions={}",
                compactText.length(),
                enrichment == null ? List.of() : enrichment.evidenceSources(),
                problem.size(),
                method.size(),
                implementation.size(),
                evaluation.size(),
                figures.size()
        );
        return new PaperEvidencePacket(
                metadata.title(),
                metadata.abstractText(),
                metadata.authors(),
                metadata.year(),
                metadata.venue(),
                metadata.doi(),
                keywords,
                distinctLimited(problem, 8),
                distinctLimited(method, 8),
                distinctLimited(implementation, 8),
                distinctLimited(evaluation, 8),
                distinctLimited(conclusion, 5),
                distinctLimited(figures, effectiveMaxFigures()),
                distinctLimited(tables, effectiveMaxTables()),
                domainTerms,
                applicationClues,
                visualClues,
                compactText,
                compactText.length(),
                distinctLimited(warnings, 12),
                enrichment == null ? null : enrichment.publisher(),
                enrichment == null ? null : enrichment.canonicalUrl(),
                enrichment == null ? List.of() : enrichment.subjectTerms(),
                enrichment == null ? null : enrichment.citationMetadata(),
                enrichment == null ? List.of() : enrichment.evidenceSources(),
                buildEvidenceSpans(pdfEvidence, problem, method, implementation, evaluation, conclusion, figures, tables)
        );
    }

    private List<EvidenceSpan> buildEvidenceSpans(
            PdfTextEvidence pdfEvidence,
            List<String> problem,
            List<String> method,
            List<String> implementation,
            List<String> evaluation,
            List<String> conclusion,
            List<String> figures,
            List<String> tables
    ) {
        List<EvidenceSpan> spans = new ArrayList<>();
        if (pdfEvidence != null) {
            for (PdfPageEvidence page : pdfEvidence.pages()) {
                String text = truncate(clean(page.text()), effectiveSectionLimit());
                if (text != null) {
                    spans.add(new EvidenceSpan("pdf-page-" + page.pageNumber(), "PDF_TEXT", page.pageNumber(),
                            "page", text, page.extractionConfidence()));
                }
            }
        }
        addSectionSpans(spans, "problem", problem);
        addSectionSpans(spans, "method", method);
        addSectionSpans(spans, "implementation", implementation);
        addSectionSpans(spans, "evaluation", evaluation);
        addSectionSpans(spans, "conclusion", conclusion);
        addSectionSpans(spans, "figure-caption", figures);
        addSectionSpans(spans, "table-caption", tables);
        return List.copyOf(spans);
    }

    private void addSectionSpans(List<EvidenceSpan> spans, String section, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String text = truncate(clean(value), effectiveSectionLimit());
            if (text != null) {
                spans.add(new EvidenceSpan("grobid-" + section + "-" + (spans.size() + 1), "GROBID", null,
                        section, text, 0.85d));
            }
        }
    }

    private List<String> pdfEvidenceSnippets(PdfTextEvidence pdfEvidence) {
        if (pdfEvidence == null || !pdfEvidence.available() || pdfEvidence.documentText().isBlank()) {
            return List.of();
        }
        String normalized = pdfEvidence.documentText().replace('\n', ' ');
        return distinctLimited(List.of(normalized.split("(?<=[.!?])\\s+(?=[\\p{Lu}\\p{N}])")), 40).stream()
                .filter(value -> value.length() >= 45)
                .map(value -> truncate(value, Math.min(700, effectiveSectionLimit())))
                .limit(24)
                .toList();
    }

    private void addPdfSnippets(
            List<String> snippets,
            List<String> problem,
            List<String> method,
            List<String> implementation,
            List<String> evaluation,
            List<String> conclusion
    ) {
        for (String snippet : snippets) {
            String lower = snippet.toLowerCase(Locale.ROOT);
            if (containsAny(lower, " problem", " challenge", " limitation", " need ", " motivation", " background")) {
                problem.add(snippet);
            }
            if (containsAny(lower, "we propose", "we present", "we introduce", " method", " approach", " design")) {
                method.add(snippet);
            }
            if (containsAny(lower, " implementation", " architecture", " prototype", " interface", " algorithm")) {
                implementation.add(snippet);
            }
            if (containsAny(lower, " evaluation", " experiment", " result", " finding", " performance")) {
                evaluation.add(snippet);
            }
            if (containsAny(lower, " conclusion", "we conclude", " future work", " implication")) {
                conclusion.add(snippet);
            }
        }
        if (problem.isEmpty() && !snippets.isEmpty()) {
            problem.add(snippets.getFirst());
        }
        if (method.isEmpty() && snippets.size() > 1) {
            method.add(snippets.get(1));
        }
    }

    private void extractSections(
            Document document,
            List<String> problem,
            List<String> method,
            List<String> implementation,
            List<String> evaluation,
            List<String> conclusion
    ) {
        NodeList divisions = document.getElementsByTagNameNS("*", "div");
        for (int index = 0; index < divisions.getLength(); index++) {
            Node division = divisions.item(index);
            String heading = firstDescendantText(division, "head");
            if (heading == null || isNoiseHeading(heading)) {
                continue;
            }
            String body = directParagraphText(division);
            if (body == null || isNoiseText(body)) {
                continue;
            }
            String snippet = truncate(clean(heading + ": " + body), effectiveSectionLimit());
            String normalizedHeading = heading.toLowerCase(Locale.ROOT);
            if (matchesHeading(normalizedHeading, "abstract", "introduction", "background", "motivation", "problem", "overview")) {
                problem.add(snippet);
            }
            if (matchesHeading(normalizedHeading, "method", "methodology", "approach", "design", "materials", "procedure",
                    "interface", "interaction", "workflow", "usage")) {
                method.add(snippet);
            }
            if (matchesHeading(normalizedHeading, "system", "implementation", "architecture", "prototype", "algorithm", "model",
                    "interface", "interaction")) {
                implementation.add(snippet);
            }
            if (matchesHeading(normalizedHeading, "evaluation", "result", "experiment", "finding", "discussion", "performance", "study")) {
                evaluation.add(snippet);
            }
            if (matchesHeading(normalizedHeading, "conclusion", "future work", "implication", "limitation")) {
                conclusion.add(snippet);
            }
        }
    }

    private void extractCaptions(Document document, List<String> figures, List<String> tables) {
        NodeList nodes = document.getElementsByTagNameNS("*", "figure");
        for (int index = 0; index < nodes.getLength(); index++) {
            Node figure = nodes.item(index);
            boolean table = figure instanceof Element element
                    && "table".equalsIgnoreCase(element.getAttribute("type"));
            String heading = firstDescendantText(figure, "head");
            String description = firstDescendantText(figure, "figDesc");
            String caption = clean(joinNonBlank(heading, description));
            if (caption == null || isNoiseText(caption)) {
                continue;
            }
            if (table && tables.size() < effectiveMaxTables()) {
                tables.add(truncate(caption, effectiveSectionLimit()));
            } else if (!table && figures.size() < effectiveMaxFigures()) {
                figures.add(truncate(caption, effectiveSectionLimit()));
            }
        }
    }

    private void extractKeywords(Document document, List<String> keywords) {
        NodeList nodes = document.getElementsByTagNameNS("*", "term");
        for (int index = 0; index < nodes.getLength() && keywords.size() < 20; index++) {
            String value = clean(nodes.item(index).getTextContent());
            if (value != null && value.length() <= 120 && !isNoiseText(value)) {
                keywords.add(value);
            }
        }
    }

    private List<String> extractSalientTerms(
            String title,
            String abstractText,
            List<String> keywords,
            List<String> snippets
    ) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(keywords == null ? List.of() : keywords);
        if (title != null) {
            for (String segment : title.split("[:;|]") ) {
                String cleaned = clean(segment);
                if (cleaned != null && cleaned.split("\\s+").length >= 2 && cleaned.length() <= 120) {
                    terms.add(cleaned);
                }
            }
        }

        String corpus = clean(joinNonBlank(title, abstractText, String.join(" ", snippets == null ? List.of() : snippets)));
        Map<String, Integer> counts = new HashMap<>();
        if (corpus != null) {
            Matcher matcher = TOKEN_PATTERN.matcher(corpus.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                String token = matcher.group();
                if (!STOP_WORDS.contains(token) && !token.chars().allMatch(Character::isDigit)) {
                    counts.merge(token, 1, Integer::sum);
                }
            }
        }
        counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(12)
                .map(Map.Entry::getKey)
                .forEach(terms::add);
        return terms.stream().limit(20).toList();
    }

    private String buildCompactText(
            GrobidMetadata metadata,
            List<String> keywords,
            List<String> problem,
            List<String> method,
            List<String> implementation,
            List<String> evaluation,
            List<String> conclusion,
            List<String> figures,
            List<String> tables,
            List<String> domainTerms,
            List<String> applicationClues,
            List<String> visualClues,
            DoiEnrichmentResult enrichment,
            List<String> pdfSnippets,
            int maxChars
    ) {
        StringBuilder output = new StringBuilder();
        append(output, "Title", metadata.title());
        append(output, "Abstract", truncate(metadata.abstractText(), effectiveSectionLimit()));
        append(output, "Authors", String.join(", ", metadata.authors()));
        append(output, "Year", metadata.year() == null ? null : String.valueOf(metadata.year()));
        append(output, "Venue", metadata.venue());
        append(output, "DOI", metadata.doi());
        if (enrichment != null) {
            append(output, "Publisher", enrichment.publisher());
            append(output, "Canonical URL", enrichment.canonicalUrl());
            append(output, "Citation metadata", enrichment.citationMetadata());
            appendList(output, "Evidence sources", enrichment.evidenceSources());
            appendList(output, "DOI subjects", enrichment.subjectTerms());
        }
        appendList(output, "Keywords", keywords);
        appendList(output, "Problem and context evidence", problem);
        appendList(output, "Method and design evidence", method);
        appendList(output, "Implementation evidence", implementation);
        appendList(output, "Evaluation and result evidence", evaluation);
        appendList(output, "Conclusion evidence", conclusion);
        appendList(output, "Figure captions", figures);
        appendList(output, "Table captions", tables);
        appendList(output, "Repeated or salient terms", domainTerms);
        appendList(output, "Application clues", applicationClues);
        appendList(output, "Visual clues", visualClues);
        appendList(output, "Uploaded PDF text excerpts", pdfSnippets);
        String value = output.toString().trim();
        return value.length() <= maxChars ? value : value.substring(0, maxChars).trim();
    }

    private void append(StringBuilder output, String label, String value) {
        String cleaned = clean(value);
        if (cleaned != null) {
            output.append(label).append(": ").append(cleaned).append('\n');
        }
    }

    private void appendList(StringBuilder output, String label, List<String> values) {
        List<String> cleaned = distinctLimited(values, 12);
        if (!cleaned.isEmpty()) {
            output.append(label).append(":\n");
            cleaned.forEach(value -> output.append("- ").append(value).append('\n'));
        }
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        setFeatureSafely(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureSafely(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureSafely(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureSafely(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String directParagraphText(Node division) {
        List<String> paragraphs = new ArrayList<>();
        NodeList children = division.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if ("p".equals(child.getLocalName())) {
                String value = clean(child.getTextContent());
                if (value != null) {
                    paragraphs.add(value);
                }
            }
        }
        return clean(String.join(" ", paragraphs));
    }

    private String firstDescendantText(Node node, String localName) {
        if (node == null) {
            return null;
        }
        if (localName.equals(node.getLocalName())) {
            return clean(node.getTextContent());
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            String value = firstDescendantText(children.item(index), localName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean matchesHeading(String heading, String... fragments) {
        for (String fragment : fragments) {
            if (heading.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNoiseHeading(String heading) {
        String lower = heading.toLowerCase(Locale.ROOT);
        return matchesHeading(lower, "reference", "bibliography", "acknowledg", "author contribution", "supplementary");
    }

    private boolean isNoiseText(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("copyright ")
                || lower.startsWith("permission to make digital")
                || lower.startsWith("published under")
                || lower.contains("all rights reserved");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?<=\\p{L})-\\s+(?=\\p{Ll})", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        String prefix = value.substring(0, maxChars).trim();
        int sentenceBoundary = Math.max(prefix.lastIndexOf('.'), Math.max(prefix.lastIndexOf('!'), prefix.lastIndexOf('?')));
        if (sentenceBoundary >= maxChars / 3) {
            return prefix.substring(0, sentenceBoundary + 1).trim();
        }
        int wordBoundary = prefix.lastIndexOf(' ');
        return wordBoundary > 0 ? prefix.substring(0, wordBoundary).trim() : prefix;
    }

    private List<String> rankedApplicationClues(List<String> values, String title, int limit) {
        String product = productName(title);
        return distinctLimited(values, values == null ? 0 : values.size()).stream()
                .sorted(Comparator.comparingInt((String value) -> applicationScore(value, product)).reversed())
                .filter(value -> applicationScore(value, product) > 0)
                .limit(limit)
                .toList();
    }

    private int applicationScore(String value, String product) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        int score = 0;
        if (product != null && lower.contains(product.toLowerCase(Locale.ROOT))) {
            score += 20;
        }
        for (String cue : List.of(
                "interfaces and interactions", "design of", "we designed", "we developed", "we present",
                "users access", "users engage", "participants explored", "using the system", "using the method",
                "real-world", "field study", "workflow", "deployment", "technical implementation"
        )) {
            if (lower.contains(cue)) {
                score += 7;
            }
        }
        for (String noise : List.of(
                "related work", "theoretical framework", "previous studies", "requirement analysis",
                "workshop", "flow chart", "analysis and results"
        )) {
            if (lower.contains(noise)) {
                score -= 10;
            }
        }
        if (lower.startsWith("figure ") && !isUsefulApplicationFigure(value)) {
            score -= 20;
        }
        return score;
    }

    private boolean isUsefulApplicationFigure(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        boolean useEvidence = containsAny(lower,
                "demonstration", "using the", "in use", "user interface", "interaction", "prototype deployed");
        boolean diagramOrResult = containsAny(lower,
                "summary of", "framework", "conceptual diagram", "flow chart", "results of", "architecture");
        return useEvidence && !diagramOrResult;
    }

    private List<String> meaningfulTerms(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> {
                    String lower = value.toLowerCase(Locale.ROOT).trim();
                    return !STOP_WORDS.contains(lower)
                            && lower.length() >= 4;
                })
                .toList();
    }

    private String productName(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        int colon = title.indexOf(':');
        return colon > 1 && colon <= 80 ? title.substring(0, colon).trim() : null;
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String joinNonBlank(String... values) {
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String normalized = clean(value);
            if (normalized != null) {
                cleaned.add(normalized);
            }
        }
        return String.join(" ", cleaned);
    }

    @SafeVarargs
    private final List<String> merge(List<String>... lists) {
        List<String> values = new ArrayList<>();
        for (List<String> list : lists) {
            if (list != null) {
                values.addAll(list);
            }
        }
        return values;
    }

    private List<String> distinctLimited(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::clean)
                .filter(value -> value != null && !isNoiseText(value))
                .distinct()
                .limit(Math.max(0, limit))
                .toList();
    }

    private int effectiveSectionLimit() {
        return maxSectionSnippetChars <= 0 ? 2000 : maxSectionSnippetChars;
    }

    private int effectiveMaxFigures() {
        return maxFigureCaptions <= 0 ? 8 : maxFigureCaptions;
    }

    private int effectiveMaxTables() {
        return maxTableCaptions <= 0 ? 4 : maxTableCaptions;
    }

    private void setFeatureSafely(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (ParserConfigurationException ex) {
            logger.debug("XML parser feature not supported: {}", feature);
        }
    }
}
