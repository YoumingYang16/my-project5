package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.DoiEnrichmentResult;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.GrobidPaperDocument;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingCacheEntry;
import com.heritage.platform.dto.ai.PaperUnderstandingProviderResult;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import com.heritage.platform.dto.ai.PdfTextEvidence;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import com.heritage.platform.dto.ai.ResolvedPaperUnderstanding;
import com.heritage.platform.entity.Post;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaperUnderstandingResolverServiceTests {

    @Test
    void authoritativeOpenAiCacheHitSkipsGrobidAndProviderCall() {
        Fixture fixture = fixture();
        PaperUnderstandingCacheEntry entry = new PaperUnderstandingCacheEntry(
                PaperUnderstandingCacheService.CACHE_VERSION, "hash", evidence(), understanding(),
                ReferenceGroundingContext.empty(null), "OPENAI", "2026-06-29T00:00:00Z", "gpt-test", List.of()
        );
        when(fixture.cache.pdfHash(fixture.pdf)).thenReturn("hash");
        when(fixture.cache.find(12L, "hash")).thenReturn(Optional.of(entry));

        ResolvedPaperUnderstanding result = fixture.resolver.resolve(12L, fixture.publication, fixture.pdf);

        assertThat(result.cacheHit()).isTrue();
        assertThat(result.source()).isEqualTo("CACHED_OPENAI");
        assertThat(result.warnings()).contains("Authoritative cached OpenAI paper understanding was reused.");
        verify(fixture.providerChain, never()).resolve(any());
        verifyNoInteractions(
                fixture.grobid, fixture.groundingService, fixture.evidenceBuilder,
                fixture.pdfTextService, fixture.doiService
        );
    }

    @Test
    void grobidUnavailableStillUsesPdfAndPublicationEvidenceFallback() {
        Fixture fixture = fixture();
        GrobidMetadata metadata = GrobidMetadata.unavailable("GROBID is disabled.");
        GrobidPaperDocument document = new GrobidPaperDocument(metadata, null, metadata.warnings());
        PdfTextEvidence pdfEvidence = new PdfTextEvidence(
                "Inspection assistant 10.1145/example",
                evidence().compactEvidenceText(), true, List.of()
        );
        DoiEnrichmentResult enrichment = new DoiEnrichmentResult(
                new GrobidMetadata(
                        evidence().title(), evidence().abstractText(), evidence().authors(), evidence().year(),
                        evidence().venue(), "10.1145/example", evidence().keywords(),
                        "PUBLICATION_METADATA", true, List.of()
                ),
                "ACM", "https://doi.org/10.1145/example", List.of("inspection"),
                "type=proceedings-article", List.of("PDF_TEXT", "PUBLICATION_METADATA", "DOI"),
                List.of(DoiEvidenceEnrichmentService.DOI_EVIDENCE_WARNING), true, true, true,
                List.of("venue from DOI metadata")
        );
        when(fixture.cache.pdfHash(fixture.pdf)).thenReturn("hash");
        when(fixture.cache.find(12L, "hash")).thenReturn(Optional.empty());
        when(fixture.pdfTextService.extract(fixture.pdf)).thenReturn(pdfEvidence);
        when(fixture.grobid.extractDocument(fixture.pdf)).thenReturn(document);
        when(fixture.doiService.enrich(document, fixture.publication, pdfEvidence)).thenReturn(enrichment);
        when(fixture.groundingService.extract(null)).thenReturn(ReferenceGroundingContext.empty(null));
        when(fixture.evidenceBuilder.build(any(), eq(enrichment), eq(pdfEvidence))).thenReturn(evidence());
        when(fixture.providerChain.resolve(evidence())).thenReturn(new PaperUnderstandingProviderResult(
                new DeterministicPaperUnderstandingProvider().understand(evidence()),
                "EVIDENCE_FALLBACK", "evidence-fallback-v2", "OpenAI unavailable", List.of()
        ));

        ResolvedPaperUnderstanding result = fixture.resolver.resolve(12L, fixture.publication, fixture.pdf);

        assertThat(result.source()).isEqualTo("EVIDENCE_FALLBACK");
        assertThat(result.paperUnderstanding().finalUnderstanding().confidenceLevel()).isEqualTo("LOW");
        assertThat(result.warnings()).contains(DeterministicPaperUnderstandingProvider.FALLBACK_WARNING);
        verify(fixture.cache, never()).save(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void grobidEvidenceIsAnalyzedByOpenAiAndOnlyOpenAiResultIsCached() {
        Fixture fixture = fixture();
        GrobidMetadata metadata = new GrobidMetadata(
                "Inspection assistant", evidence().abstractText(), List.of("A. Researcher"), 2026,
                "Inspection Journal", null, List.of("inspection"), "GROBID", true, List.of()
        );
        when(fixture.cache.pdfHash(fixture.pdf)).thenReturn("hash");
        when(fixture.cache.find(12L, "hash")).thenReturn(Optional.empty());
        when(fixture.grobid.isEnabled()).thenReturn(true);
        GrobidPaperDocument document = new GrobidPaperDocument(metadata, "<TEI/>", List.of());
        PdfTextEvidence pdfEvidence = new PdfTextEvidence("Inspection assistant", evidence().compactEvidenceText(), true, List.of());
        DoiEnrichmentResult enrichment = new DoiEnrichmentResult(
                metadata, null, null, List.of(), null, List.of("GROBID", "PDF_TEXT"),
                List.of(), false, false, false, List.of()
        );
        when(fixture.pdfTextService.extract(fixture.pdf)).thenReturn(pdfEvidence);
        when(fixture.grobid.extractDocument(fixture.pdf)).thenReturn(document);
        when(fixture.doiService.enrich(document, fixture.publication, pdfEvidence)).thenReturn(enrichment);
        when(fixture.groundingService.extract("<TEI/>")).thenReturn(ReferenceGroundingContext.empty(null));
        when(fixture.evidenceBuilder.build(any(), eq(enrichment), eq(pdfEvidence))).thenReturn(evidence());
        when(fixture.providerChain.resolve(evidence())).thenReturn(new PaperUnderstandingProviderResult(
                openAiUnderstanding(), "OPENAI", "gpt-test", null, List.of()
        ));

        ResolvedPaperUnderstanding result = fixture.resolver.resolve(12L, fixture.publication, fixture.pdf);

        assertThat(result.source()).isEqualTo("OPENAI");
        assertThat(result.paperUnderstanding().understandingSource()).isEqualTo("OPENAI");
        assertThat(result.paperUnderstanding().finalUnderstanding().proposedSystemOrMethod())
                .isEqualTo("sensor-guided inspection assistant");
        verify(fixture.cache).save(
                eq(12L), eq("hash"), eq(evidence()), any(), any(), eq("OPENAI"), eq("gpt-test"), any()
        );
    }

    private Fixture fixture() {
        GrobidMetadataService grobid = mock(GrobidMetadataService.class);
        PaperUnderstandingProviderChain providerChain = mock(PaperUnderstandingProviderChain.class);
        PaperReferenceGroundingService grounding = mock(PaperReferenceGroundingService.class);
        PaperEvidencePacketBuilder evidenceBuilder = mock(PaperEvidencePacketBuilder.class);
        PaperUnderstandingCacheService cache = mock(PaperUnderstandingCacheService.class);
        PdfTextEvidenceService pdfTextService = mock(PdfTextEvidenceService.class);
        DoiEvidenceEnrichmentService doiService = mock(DoiEvidenceEnrichmentService.class);
        PaperUnderstandingResolverService resolver = new PaperUnderstandingResolverService(
                grobid, providerChain, new PaperUnderstandingMergeService(), grounding,
                evidenceBuilder, cache, new PaperAiQualityValidator(), pdfTextService, doiService
        );
        Post publication = mock(Post.class);
        Path pdf = Path.of("paper.pdf");
        when(providerChain.priority()).thenReturn(List.of("openai", "evidence_fallback"));
        return new Fixture(
                grobid, providerChain, grounding, evidenceBuilder, cache,
                pdfTextService, doiService, resolver, publication, pdf
        );
    }

    private PaperEvidencePacket evidence() {
        String body = "The paper proposes a sensor-guided inspection assistant for industrial quality review. "
                .repeat(5);
        String text = "Title: Inspection assistant\nAbstract: " + body + "\nMethod: " + body;
        return new PaperEvidencePacket(
                "Inspection assistant", body, List.of("A. Researcher"), 2026, "Inspection Journal", null,
                List.of("inspection"), List.of(body), List.of(body), List.of(body), List.of(body), List.of(body),
                List.of(), List.of(), List.of("inspection"), List.of(body), List.of("inspection interface"),
                text, text.length(), List.of()
        );
    }

    private OpenAiPaperUnderstanding openAiUnderstanding() {
        return new OpenAiPaperUnderstanding(
                "Inspection assistant", "The paper presents an assistant for industrial inspection.",
                List.of("A. Researcher"), 2026,
                "manual inspection can produce inconsistent defect review", "industrial quality inspectors",
                "sensor observations guide the inspector's review workflow",
                "an inspection assistant combines a sensor module with a review interface",
                "the assistant supports more consistent defect review",
                List.of("sensor module", "inspection interface", "finding overlay"),
                "sensor observations become highlighted findings", "inspectors can focus on likely defects", null,
                List.of("generic AI wallpaper"), "an inspector reviews a part at an inspection station",
                List.of("inspector", "part", "interface"),
                List.of("inspector reviews highlighted findings"), "industrial quality inspection station",
                List.of(), "HIGH", "Inspection Journal", "sensor-guided inspection assistant",
                "inspection workstation interface", "sensor observations", "highlighted findings",
                "more consistent defect review",
                List.of("inspector", "inspection assistant", "inspection station", "highlighted findings"),
                List.of("generic technology poster"), "industrial quality inspection station",
                "inspect a manufactured part and review likely defects",
                "the inspector positions the part and reviews highlighted findings"
        );
    }

    private PaperUnderstandingResult understanding() {
        FinalPaperUnderstanding value = new PaperUnderstandingMergeService().merge(
                GrobidMetadata.unavailable(null), openAiUnderstanding(), null,
                ReferenceGroundingContext.empty(null), 2026, evidence(), "OPENAI"
        ).finalUnderstanding();
        return new PaperUnderstandingResult(
                "COMPLETED", GrobidMetadata.unavailable(null), openAiUnderstanding(), value, List.of(), "OPENAI"
        );
    }

    private record Fixture(
            GrobidMetadataService grobid,
            PaperUnderstandingProviderChain providerChain,
            PaperReferenceGroundingService groundingService,
            PaperEvidencePacketBuilder evidenceBuilder,
            PaperUnderstandingCacheService cache,
            PdfTextEvidenceService pdfTextService,
            DoiEvidenceEnrichmentService doiService,
            PaperUnderstandingResolverService resolver,
            Post publication,
            Path pdf
    ) {
    }
}
