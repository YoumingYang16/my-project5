package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.DoiEnrichmentResult;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.GrobidPaperDocument;
import com.heritage.platform.dto.ai.PdfTextEvidence;
import com.heritage.platform.entity.Post;
import com.heritage.platform.service.CrossrefMetadataService;
import com.heritage.platform.service.CrossrefMetadataService.CrossrefMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoiEvidenceEnrichmentServiceTests {

    @Test
    void discoversDoiFromFirstPageAndFillsOnlyMissingFields() {
        CrossrefMetadataService crossref = mock(CrossrefMetadataService.class);
        DoiEvidenceEnrichmentService service = new DoiEvidenceEnrichmentService(crossref);
        GrobidMetadata grobid = new GrobidMetadata(
                "Cleaner PDF title", null, List.of("PDF Author"), null, null, null,
                List.of(), "GROBID", true, List.of()
        );
        GrobidPaperDocument document = new GrobidPaperDocument(grobid, "<TEI/>", List.of());
        PdfTextEvidence pdf = new PdfTextEvidence(
                "Cleaner PDF title\nhttps://doi.org/10.1145/ABC.123.",
                "The paper presents an inspection assistant.", true, List.of()
        );
        Post publication = mock(Post.class);
        when(publication.getTitle()).thenReturn("Database title");
        when(crossref.findByDoi("10.1145/abc.123")).thenReturn(Optional.of(new CrossrefMetadata(
                "Crossref canonical title", "DOI Author", 2025, "CHI", "ACM",
                "10.1145/ABC.123", "Human-computer interaction, Inspection",
                "Crossref abstract", "https://doi.org/10.1145/ABC.123",
                "type=proceedings-article; citationCount=4"
        )));

        DoiEnrichmentResult result = service.enrich(document, publication, pdf);

        assertThat(result.metadata().title()).isEqualTo("Cleaner PDF title");
        assertThat(result.metadata().authors()).containsExactly("PDF Author");
        assertThat(result.metadata().abstractText()).isEqualTo("Crossref abstract");
        assertThat(result.metadata().year()).isEqualTo(2025);
        assertThat(result.metadata().venue()).isEqualTo("CHI");
        assertThat(result.publisher()).isEqualTo("ACM");
        assertThat(result.canonicalUrl()).isEqualTo("https://doi.org/10.1145/ABC.123");
        assertThat(result.evidenceSources()).containsExactly(
                "GROBID", "PDF_TEXT", "PUBLICATION_METADATA", "DOI"
        );
        assertThat(result.warnings()).contains(DoiEvidenceEnrichmentService.DOI_EVIDENCE_WARNING);
        assertThat(result.lookupSucceeded()).isTrue();
    }

    @Test
    void noDoiIsSafeAndDoesNotAttemptLookup() {
        CrossrefMetadataService crossref = mock(CrossrefMetadataService.class);
        DoiEvidenceEnrichmentService service = new DoiEvidenceEnrichmentService(crossref);
        GrobidMetadata grobid = new GrobidMetadata(
                "A real paper", "A usable abstract about a system.", List.of(), 2024,
                null, null, List.of(), "GROBID", true, List.of()
        );

        DoiEnrichmentResult result = service.enrich(
                new GrobidPaperDocument(grobid, "<TEI/>", List.of()),
                mock(Post.class),
                new PdfTextEvidence("A real paper", "A usable abstract about a system.", true, List.of())
        );

        assertThat(result.doiFound()).isFalse();
        assertThat(result.lookupAttempted()).isFalse();
        assertThat(result.evidenceSources()).contains("GROBID", "PDF_TEXT").doesNotContain("DOI");
        verify(crossref, never()).findByDoi(org.mockito.ArgumentMatchers.any());
    }
}
