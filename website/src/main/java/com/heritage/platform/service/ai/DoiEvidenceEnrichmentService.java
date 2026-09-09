package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.DoiEnrichmentResult;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.GrobidPaperDocument;
import com.heritage.platform.dto.ai.PdfTextEvidence;
import com.heritage.platform.entity.Post;
import com.heritage.platform.service.CrossrefMetadataService;
import com.heritage.platform.service.CrossrefMetadataService.CrossrefMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DoiEvidenceEnrichmentService {

    public static final String DOI_EVIDENCE_WARNING =
            "DOI metadata was used to supplement extracted paper evidence.";

    private static final Logger logger = LoggerFactory.getLogger(DoiEvidenceEnrichmentService.class);
    private static final Pattern DOI_PATTERN = Pattern.compile(
            "(?i)(?:https?://(?:dx\\.)?doi\\.org/|doi\\s*[:=]\\s*)?(10\\.\\d{4,9}/[-._;()/:A-Z0-9]+)"
    );
    private static final Set<String> PLACEHOLDERS = Set.of(
            "test", "test1", "sample", "placeholder", "untitled", "unknown", "n/a", "none",
            "not specified", "paper", "article", "research paper", "content"
    );

    private final CrossrefMetadataService crossrefMetadataService;

    public DoiEvidenceEnrichmentService(CrossrefMetadataService crossrefMetadataService) {
        this.crossrefMetadataService = crossrefMetadataService;
    }

    public DoiEnrichmentResult enrich(
            GrobidPaperDocument grobidDocument,
            Post publication,
            PdfTextEvidence pdfEvidence
    ) {
        GrobidMetadata extracted = grobidDocument == null || grobidDocument.metadata() == null
                ? GrobidMetadata.unavailable("GROBID metadata was not available.")
                : grobidDocument.metadata();
        List<String> sources = new ArrayList<>();
        if (extracted.available() || useful(grobidDocument == null ? null : grobidDocument.teiXml())) {
            sources.add("GROBID");
        }
        if (pdfEvidence != null && pdfEvidence.available()) {
            sources.add("PDF_TEXT");
        }
        if (hasPublicationMetadata(publication)) {
            sources.add("PUBLICATION_METADATA");
        }

        logger.info("DOI enrichment attempt started: sources={}", sources);
        String discoveredDoi = firstDoi(
                extracted.doi(),
                grobidDocument == null ? null : grobidDocument.teiXml(),
                pdfEvidence == null ? null : pdfEvidence.firstPageText(),
                pdfEvidence == null ? null : pdfEvidence.documentText(),
                publication == null ? null : publication.getDoi(),
                publication == null ? null : publication.getBibtex(),
                publication == null ? null : publication.getContent()
        );
        boolean doiFound = discoveredDoi != null;
        logger.info("DOI discovery completed: found={}", doiFound);

        Optional<CrossrefMetadata> crossref = Optional.empty();
        if (doiFound) {
            logger.info("DOI metadata lookup started.");
            crossref = crossrefMetadataService.findByDoi(discoveredDoi);
            if (crossref.isPresent()) {
                logger.info("DOI metadata lookup succeeded.");
            } else {
                logger.warn("DOI metadata lookup failed or returned no matching record; continuing with other evidence.");
            }
        } else {
            logger.info("DOI metadata lookup skipped: no DOI was discovered.");
        }

        List<String> fieldsAdded = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (pdfEvidence != null) {
            warnings.addAll(pdfEvidence.warnings());
        }
        CrossrefMetadata doiMetadata = crossref.orElse(null);
        String title = preferExtracted(
                extracted.title(),
                doiMetadata == null ? null : doiMetadata.title(),
                publication == null ? null : publication.getTitle(),
                "title", fieldsAdded
        );
        String abstractText = preferAbstract(
                extracted.abstractText(),
                doiMetadata == null ? null : doiMetadata.abstractText(),
                publication == null ? null : publication.getAbstractText(),
                publication == null ? null : publication.getContent(),
                fieldsAdded
        );
        List<String> authors = !cleanList(extracted.authors()).isEmpty()
                ? cleanList(extracted.authors())
                : firstNonEmpty(
                        splitValues(doiMetadata == null ? null : doiMetadata.authors()),
                        splitValues(publication == null ? null : publication.getPublicationAuthors()),
                        "authors", fieldsAdded
                );
        Integer year = validYear(extracted.year())
                ? extracted.year()
                : firstYear(
                        doiMetadata == null ? null : doiMetadata.year(),
                        publication == null ? null : publication.getPublicationYear(),
                        fieldsAdded
                );
        String venue = preferExtracted(
                extracted.venue(),
                doiMetadata == null ? null : doiMetadata.venue(),
                publication == null ? null : publication.getVenue(),
                "venue", fieldsAdded
        );
        String doi = firstUseful(discoveredDoi, doiMetadata == null ? null : doiMetadata.doi());
        List<String> keywords = mergeLimited(20,
                extracted.keywords(),
                splitValues(publication == null ? null : publication.getKeywords()),
                splitValues(doiMetadata == null ? null : doiMetadata.subjects())
        );
        if (extracted.keywords().isEmpty() && !keywords.isEmpty()) {
            fieldsAdded.add("subjects/keywords");
        }

        if (doiMetadata != null) {
            sources.add("DOI");
            warnings.add(DOI_EVIDENCE_WARNING);
            if (useful(doiMetadata.publisher())) {
                fieldsAdded.add("publisher from DOI metadata");
            }
            if (useful(doiMetadata.canonicalUrl())) {
                fieldsAdded.add("canonical URL from DOI metadata");
            }
            if (useful(doiMetadata.citationMetadata())) {
                fieldsAdded.add("citation metadata from DOI metadata");
            }
        } else if (doiFound) {
            warnings.add("A DOI was discovered, but DOI metadata lookup did not return supplemental evidence.");
        }

        boolean available = useful(title) || useful(abstractText) || !authors.isEmpty() || validYear(year)
                || pdfEvidence != null && pdfEvidence.available();
        GrobidMetadata enriched = new GrobidMetadata(
                title, abstractText, authors, year, venue, doi, keywords,
                extracted.available() ? extracted.source() : sources.contains("PUBLICATION_METADATA")
                        ? "PUBLICATION_METADATA" : sources.contains("PDF_TEXT") ? "PDF_TEXT" : extracted.source(),
                available,
                distinct(extracted.warnings())
        );
        logger.info(
                "DOI enrichment completed: found={}, lookupAttempted={}, lookupSucceeded={}, fieldsAdded={}, evidenceSources={}",
                doiFound, doiFound, doiMetadata != null, distinct(fieldsAdded), distinct(sources)
        );
        return new DoiEnrichmentResult(
                enriched,
                doiMetadata == null ? null : clean(doiMetadata.publisher()),
                doiMetadata == null ? null : clean(doiMetadata.canonicalUrl()),
                splitValues(doiMetadata == null ? null : doiMetadata.subjects()),
                doiMetadata == null ? null : clean(doiMetadata.citationMetadata()),
                distinct(sources),
                distinct(warnings),
                doiFound,
                doiFound,
                doiMetadata != null,
                distinct(fieldsAdded)
        );
    }

    private String preferExtracted(String extracted, String doi, String publication, String field, List<String> added) {
        String first = firstUseful(extracted);
        if (first != null) {
            return first;
        }
        String doiValue = firstUseful(doi);
        if (doiValue != null) {
            added.add(field + " from DOI metadata");
            return doiValue;
        }
        String publicationValue = firstUseful(publication);
        if (publicationValue != null) {
            added.add(field + " from publication metadata");
        }
        return publicationValue;
    }

    private String preferAbstract(
            String extracted,
            String doi,
            String publicationAbstract,
            String publicationContent,
            List<String> added
    ) {
        if (usefulText(extracted, 40)) {
            return clean(extracted);
        }
        if (usefulText(doi, 40)) {
            added.add("abstract from DOI metadata");
            return clean(doi);
        }
        if (usefulText(publicationAbstract, 40)) {
            added.add("abstract from publication metadata");
            return clean(publicationAbstract);
        }
        if (useful(extracted)) {
            return clean(extracted);
        }
        if (useful(doi)) {
            added.add("abstract from DOI metadata");
            return clean(doi);
        }
        if (useful(publicationAbstract)) {
            added.add("abstract from publication metadata");
            return clean(publicationAbstract);
        }
        return firstUseful(publicationContent);
    }

    private List<String> firstNonEmpty(List<String> doi, List<String> publication, String field, List<String> added) {
        if (!doi.isEmpty()) {
            added.add(field + " from DOI metadata");
            return doi;
        }
        if (!publication.isEmpty()) {
            added.add(field + " from publication metadata");
        }
        return publication;
    }

    private Integer firstYear(Integer doiYear, Integer publicationYear, List<String> added) {
        if (validYear(doiYear)) {
            added.add("year from DOI metadata");
            return doiYear;
        }
        if (validYear(publicationYear)) {
            added.add("year from publication metadata");
            return publicationYear;
        }
        return null;
    }

    private String firstDoi(String... candidates) {
        for (String candidate : candidates) {
            String doi = doiFrom(candidate);
            if (doi != null) {
                return doi;
            }
        }
        return null;
    }

    private String doiFrom(String value) {
        if (!useful(value)) {
            return null;
        }
        Matcher matcher = DOI_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        String doi = matcher.group(1).replaceAll("[\\s.,;:]+$", "").toLowerCase();
        doi = trimUnmatchedClosingDelimiter(doi, '(', ')');
        doi = trimUnmatchedClosingDelimiter(doi, '[', ']');
        doi = trimUnmatchedClosingDelimiter(doi, '{', '}');
        return doi;
    }

    private String trimUnmatchedClosingDelimiter(String value, char opening, char closing) {
        String result = value;
        while (result.endsWith(String.valueOf(closing))
                && result.chars().filter(character -> character == closing).count()
                > result.chars().filter(character -> character == opening).count()) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean hasPublicationMetadata(Post publication) {
        return publication != null && (
                useful(publication.getTitle()) || useful(publication.getAbstractText())
                        || useful(publication.getPublicationAuthors()) || validYear(publication.getPublicationYear())
                        || useful(publication.getVenue()) || useful(publication.getDoi())
                        || useful(publication.getKeywords()) || useful(publication.getBibtex())
        );
    }

    private boolean validYear(Integer value) {
        return value != null && value >= 1800 && value <= Year.now().getValue() + 1;
    }

    private boolean useful(String value) {
        String cleaned = clean(value);
        return cleaned != null && !PLACEHOLDERS.contains(cleaned.toLowerCase());
    }

    private boolean usefulText(String value, int minimumLength) {
        String cleaned = clean(value);
        return useful(cleaned) && cleaned.length() >= minimumLength;
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (useful(value)) {
                return clean(value);
            }
        }
        return null;
    }

    private List<String> splitValues(String raw) {
        if (!useful(raw)) {
            return List.of();
        }
        return cleanList(Arrays.asList(raw.split("\\s*[;,]\\s*")));
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(this::useful).map(this::clean).distinct().limit(30).toList();
    }

    @SafeVarargs
    private final List<String> mergeLimited(int limit, List<String>... lists) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (List<String> list : lists) {
            result.addAll(cleanList(list));
        }
        return result.stream().limit(limit).toList();
    }

    private List<String> distinct(List<String> values) {
        return values == null ? List.of() : new LinkedHashSet<>(values).stream()
                .filter(value -> value != null && !value.isBlank()).toList();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
