package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.DoiEnrichmentResult;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.GrobidPaperDocument;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PdfTextEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaperEvidencePacketBuilderTests {

    @Test
    void recordsDoiPdfAndPublicationSourcesInTheCompactPacket() {
        PaperEvidencePacketBuilder builder = new PaperEvidencePacketBuilder();
        ReflectionTestUtils.setField(builder, "maxEvidenceChars", 4000);
        ReflectionTestUtils.setField(builder, "maxSectionSnippetChars", 500);
        ReflectionTestUtils.setField(builder, "maxFigureCaptions", 2);
        ReflectionTestUtils.setField(builder, "maxTableCaptions", 1);
        GrobidMetadata metadata = new GrobidMetadata(
                "Inspection assistant", "A system supports inspectors during defect review.",
                List.of("A. Researcher"), 2026, "CHI", "10.1145/example",
                List.of("inspection"), "GROBID", true, List.of()
        );
        DoiEnrichmentResult enrichment = new DoiEnrichmentResult(
                metadata, "ACM", "https://doi.org/10.1145/example", List.of("HCI"),
                "type=proceedings-article; citationCount=4",
                List.of("GROBID", "DOI", "PDF_TEXT", "PUBLICATION_METADATA"),
                List.of(DoiEvidenceEnrichmentService.DOI_EVIDENCE_WARNING),
                true, true, true, List.of("publisher from DOI metadata")
        );
        PdfTextEvidence pdf = new PdfTextEvidence(
                "Inspection assistant 10.1145/example",
                "Inspectors face inconsistent defect review. The authors propose an inspection assistant. "
                        + "The interface highlights findings for review at an inspection station.",
                true, List.of()
        );

        PaperEvidencePacket packet = builder.build(
                new GrobidPaperDocument(metadata, null, List.of()), enrichment, pdf
        );

        assertThat(packet.evidenceSources()).containsExactly(
                "GROBID", "DOI", "PDF_TEXT", "PUBLICATION_METADATA"
        );
        assertThat(packet.publisher()).isEqualTo("ACM");
        assertThat(packet.canonicalUrl()).isEqualTo("https://doi.org/10.1145/example");
        assertThat(packet.citationMetadata()).contains("citationCount=4");
        assertThat(packet.compactEvidenceText()).contains(
                "Evidence sources", "DOI subjects", "Uploaded PDF text excerpts"
        );
    }

    @Test
    void buildsBoundedGenericEvidenceFromStructuredTeiAndRemovesNoise() {
        PaperEvidencePacketBuilder builder = new PaperEvidencePacketBuilder();
        ReflectionTestUtils.setField(builder, "maxEvidenceChars", 1400);
        ReflectionTestUtils.setField(builder, "maxSectionSnippetChars", 260);
        ReflectionTestUtils.setField(builder, "maxFigureCaptions", 2);
        ReflectionTestUtils.setField(builder, "maxTableCaptions", 1);

        GrobidMetadata metadata = new GrobidMetadata(
                "Adaptive manipulation with tactile feedback",
                "We present a tactile control method for robust object manipulation under uncertain contact.",
                List.of("A. Researcher", "B. Scientist"),
                2026,
                "International Research Conference",
                "10.1000/example",
                List.of("tactile control", "adaptive manipulation"),
                "GROBID",
                true,
                List.of()
        );
        String tei = """
                <TEI xmlns="http://www.tei-c.org/ns/1.0">
                  <teiHeader><profileDesc><textClass><keywords><term>contact sensing</term></keywords></textClass></profileDesc></teiHeader>
                  <text><body>
                    <div><head>Introduction</head><p>Reliable manipulation remains difficult when contact conditions vary.</p></div>
                    <div><head>Method</head><p>The controller combines tactile observations with an adaptive policy.</p></div>
                    <div><head>System Implementation</head><p>A sensing module updates the controller during each manipulation step.</p></div>
                    <div><head>Evaluation</head><p>Experiments show improved completion under unseen contact conditions.</p></div>
                    <div><head>References</head><p>Copyright 2026. All rights reserved. Prior work list.</p></div>
                    <figure><head>Experimental setup</head><figDesc>The platform manipulates an object while sensing contact.</figDesc></figure>
                    <figure type="table"><head>Task completion results</head></figure>
                  </body></text>
                </TEI>
                """;

        PaperEvidencePacket packet = builder.build(new GrobidPaperDocument(metadata, tei, List.of()));

        assertThat(packet.characterCount()).isLessThanOrEqualTo(1400);
        assertThat(packet.methodDesignSnippets()).anyMatch(value -> value.startsWith("Method:"));
        assertThat(packet.implementationSnippets()).anyMatch(value -> value.startsWith("System Implementation:"));
        assertThat(packet.figureCaptions()).contains("Experimental setup The platform manipulates an object while sensing contact.");
        assertThat(packet.keywords()).contains("contact sensing", "tactile control");
        assertThat(packet.compactEvidenceText()).doesNotContain("Prior work list", "All rights reserved");
    }
}
