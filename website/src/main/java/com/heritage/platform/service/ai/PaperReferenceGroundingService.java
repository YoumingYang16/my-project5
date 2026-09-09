package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.ReferenceGroundingContext;
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
public class PaperReferenceGroundingService {

    private static final Logger logger = LoggerFactory.getLogger(PaperReferenceGroundingService.class);
    private static final int MAX_ITEMS_PER_GROUP = 12;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}-]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "about", "after", "also", "among", "based", "because", "been", "before", "between",
            "both", "could", "data", "during", "each", "from", "have", "into", "more", "most",
            "other", "paper", "proposed", "research", "results", "show", "study", "such", "system",
            "than", "that", "their", "there", "these", "they", "this", "through", "using", "were",
            "which", "while", "with", "within", "would"
    );

    @Value("${ai-cover.reference-grounding-enabled:true}")
    private boolean enabled;

    public ReferenceGroundingContext extract(String teiXml) {
        if (!enabled) {
            return ReferenceGroundingContext.empty("Paper reference grounding is disabled.");
        }
        if (teiXml == null || teiXml.isBlank()) {
            return ReferenceGroundingContext.empty(
                    "Limited paper-specific visual grounding was found, so scene details were inferred conservatively."
            );
        }

        try {
            Document document = parseXml(teiXml);
            List<String> figureCaptions = new ArrayList<>();
            List<String> tableCaptions = new ArrayList<>();
            extractCaptions(document, figureCaptions, tableCaptions);
            List<String> sectionHints = extractSectionHints(document);
            List<String> keywords = extractKeywords(document);
            String evidenceText = normalize(String.join(" ", merge(sectionHints, figureCaptions, tableCaptions, keywords)));
            List<String> salientTerms = salientTerms(evidenceText, keywords);
            List<String> taskClues = sectionHints.stream()
                    .filter(this::isMethodOrOutcomeSection)
                    .limit(6)
                    .toList();

            LinkedHashSet<String> visualClues = new LinkedHashSet<>();
            figureCaptions.stream().limit(6).forEach(visualClues::add);
            tableCaptions.stream().limit(3).forEach(visualClues::add);
            keywords.stream().limit(6).forEach(visualClues::add);
            salientTerms.stream().limit(6).forEach(visualClues::add);

            List<String> warnings = visualClues.isEmpty()
                    ? List.of("Limited paper-specific visual grounding was found, so scene details were inferred conservatively.")
                    : List.of();
            logger.info(
                    "Paper reference grounding succeeded: figures={}, tables={}, sections={}, keywords={}, salientTerms={}",
                    figureCaptions.size(),
                    tableCaptions.size(),
                    sectionHints.size(),
                    keywords.size(),
                    salientTerms.size()
            );
            return new ReferenceGroundingContext(
                    distinctLimited(figureCaptions),
                    distinctLimited(tableCaptions),
                    distinctLimited(sectionHints),
                    distinctLimited(merge(keywords, salientTerms)),
                    List.of(),
                    distinctLimited(taskClues),
                    List.of(),
                    List.of(),
                    visualClues.stream().limit(MAX_ITEMS_PER_GROUP).toList(),
                    warnings
            );
        } catch (Exception ex) {
            logger.warn("Paper reference grounding extraction failed: {}", AiCoverDiagnostics.safeExceptionSummary(ex));
            return ReferenceGroundingContext.empty(
                    "Paper-specific visual grounding could not be extracted; generation continued conservatively."
            );
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
            String caption = normalize(joinNonBlank(heading, description));
            if (caption == null) {
                continue;
            }
            (table ? tables : figures).add(truncate(caption, 900));
        }
    }

    private List<String> extractSectionHints(Document document) {
        List<String> hints = new ArrayList<>();
        NodeList divisions = document.getElementsByTagNameNS("*", "div");
        for (int index = 0; index < divisions.getLength(); index++) {
            Node division = divisions.item(index);
            String heading = firstDescendantText(division, "head");
            if (heading == null || isNoiseHeading(heading)) {
                continue;
            }
            String paragraph = directParagraphText(division);
            String hint = paragraph == null ? heading : heading + ": " + truncate(paragraph, 900);
            hints.add(normalize(hint));
        }
        return distinctLimited(hints);
    }

    private List<String> extractKeywords(Document document) {
        List<String> keywords = new ArrayList<>();
        NodeList terms = document.getElementsByTagNameNS("*", "term");
        for (int index = 0; index < terms.getLength() && keywords.size() < MAX_ITEMS_PER_GROUP; index++) {
            String keyword = normalize(terms.item(index).getTextContent());
            if (keyword != null && keyword.length() <= 120) {
                keywords.add(keyword);
            }
        }
        return distinctLimited(keywords);
    }

    private List<String> salientTerms(String text, List<String> keywords) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(keywords == null ? List.of() : keywords);
        Map<String, Integer> counts = new HashMap<>();
        if (text != null) {
            Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
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
                .limit(MAX_ITEMS_PER_GROUP)
                .map(Map.Entry::getKey)
                .forEach(terms::add);
        return terms.stream().limit(MAX_ITEMS_PER_GROUP).toList();
    }

    private boolean isMethodOrOutcomeSection(String value) {
        String heading = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return containsAny(
                heading,
                "method", "approach", "design", "implementation", "system", "model", "algorithm",
                "evaluation", "experiment", "result", "finding", "discussion", "conclusion"
        );
    }

    private boolean isNoiseHeading(String value) {
        String heading = value.toLowerCase(Locale.ROOT);
        return containsAny(heading, "reference", "bibliography", "acknowledg", "author contribution", "supplementary");
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
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
                String value = normalize(child.getTextContent());
                if (value != null) {
                    paragraphs.add(value);
                }
            }
        }
        return normalize(String.join(" ", paragraphs));
    }

    private String firstDescendantText(Node node, String localName) {
        if (node == null) {
            return null;
        }
        if (localName.equals(node.getLocalName())) {
            return normalize(node.getTextContent());
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

    private List<String> distinctLimited(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .limit(MAX_ITEMS_PER_GROUP)
                .toList();
    }

    private String joinNonBlank(String... values) {
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                cleaned.add(normalized);
            }
        }
        return String.join(" ", cleaned);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?<=\\p{L})-\\s+(?=\\p{Ll})", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    private void setFeatureSafely(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (ParserConfigurationException ex) {
            logger.debug("XML parser feature not supported: {}", feature);
        }
    }
}
