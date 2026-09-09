package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.DoiEnrichmentResult;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.GrobidPaperDocument;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingCacheEntry;
import com.heritage.platform.dto.ai.PaperUnderstandingProviderResult;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import com.heritage.platform.dto.ai.PdfTextEvidence;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import com.heritage.platform.dto.ai.ResolvedPaperUnderstanding;
import com.heritage.platform.entity.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class PaperUnderstandingResolverService {

    private static final Logger logger = LoggerFactory.getLogger(PaperUnderstandingResolverService.class);

    private final GrobidMetadataService grobidMetadataService;
    private final PaperUnderstandingProviderChain providerChain;
    private final PaperUnderstandingMergeService mergeService;
    private final PaperReferenceGroundingService referenceGroundingService;
    private final PaperEvidencePacketBuilder evidencePacketBuilder;
    private final PaperUnderstandingCacheService understandingCacheService;
    private final PaperAiQualityValidator qualityValidator;
    private final PdfTextEvidenceService pdfTextEvidenceService;
    private final DoiEvidenceEnrichmentService doiEvidenceEnrichmentService;
    private final DoclingFallbackService doclingFallbackService;

    @Autowired
    public PaperUnderstandingResolverService(
            GrobidMetadataService grobidMetadataService,
            PaperUnderstandingProviderChain providerChain,
            PaperUnderstandingMergeService mergeService,
            PaperReferenceGroundingService referenceGroundingService,
            PaperEvidencePacketBuilder evidencePacketBuilder,
            PaperUnderstandingCacheService understandingCacheService,
            PaperAiQualityValidator qualityValidator,
            PdfTextEvidenceService pdfTextEvidenceService,
            DoiEvidenceEnrichmentService doiEvidenceEnrichmentService,
            DoclingFallbackService doclingFallbackService
    ) {
        this.grobidMetadataService = grobidMetadataService;
        this.providerChain = providerChain;
        this.mergeService = mergeService;
        this.referenceGroundingService = referenceGroundingService;
        this.evidencePacketBuilder = evidencePacketBuilder;
        this.understandingCacheService = understandingCacheService;
        this.qualityValidator = qualityValidator;
        this.pdfTextEvidenceService = pdfTextEvidenceService;
        this.doiEvidenceEnrichmentService = doiEvidenceEnrichmentService;
        this.doclingFallbackService = doclingFallbackService;
    }

    PaperUnderstandingResolverService(
            GrobidMetadataService grobidMetadataService,
            PaperUnderstandingProviderChain providerChain,
            PaperUnderstandingMergeService mergeService,
            PaperReferenceGroundingService referenceGroundingService,
            PaperEvidencePacketBuilder evidencePacketBuilder,
            PaperUnderstandingCacheService understandingCacheService,
            PaperAiQualityValidator qualityValidator
    ) {
        this(
                grobidMetadataService, providerChain, mergeService, referenceGroundingService,
                evidencePacketBuilder, understandingCacheService, qualityValidator, null, null, null
        );
    }

    PaperUnderstandingResolverService(
            GrobidMetadataService grobidMetadataService,
            PaperUnderstandingProviderChain providerChain,
            PaperUnderstandingMergeService mergeService,
            PaperReferenceGroundingService referenceGroundingService,
            PaperEvidencePacketBuilder evidencePacketBuilder,
            PaperUnderstandingCacheService understandingCacheService,
            PaperAiQualityValidator qualityValidator,
            PdfTextEvidenceService pdfTextEvidenceService,
            DoiEvidenceEnrichmentService doiEvidenceEnrichmentService
    ) {
        this(grobidMetadataService, providerChain, mergeService, referenceGroundingService, evidencePacketBuilder,
                understandingCacheService, qualityValidator, pdfTextEvidenceService, doiEvidenceEnrichmentService, null);
    }

    public ResolvedPaperUnderstanding resolve(Long publicationId, Post publication, Path pdfPath) {
        String pdfHash = understandingCacheService.pdfHash(pdfPath);
        logger.info(
                "Paper understanding resolution started: publicationId={}, pdfHash={}, providerPriority={}",
                publicationId,
                pdfHash,
                providerChain.priority()
        );

        Optional<PaperUnderstandingCacheEntry> cached = understandingCacheService.find(publicationId, pdfHash);
        if (cached.isPresent()) {
            PaperUnderstandingCacheEntry entry = cached.orElseThrow();
            PaperUnderstandingResult cachedUnderstanding = withoutHistoricalProviderFailure(
                    entry.paperUnderstanding()
            );
            List<String> warnings = distinctWarnings(
                    withoutHistoricalProviderFailures(entry.warnings()),
                    cachedUnderstanding.warnings(),
                    List.of("Authoritative cached OpenAI paper understanding was reused.")
            );
            logger.info(
                    "Paper understanding resolution completed: publicationId={}, source=CACHED_{}, evidenceCharacters={}",
                    publicationId,
                    entry.understandingSource(),
                    entry.evidencePacket().characterCount()
            );
            return new ResolvedPaperUnderstanding(
                    pdfHash,
                    entry.evidencePacket(),
                    entry.grounding(),
                    cachedUnderstanding,
                    true,
                    "CACHED_" + entry.understandingSource(),
                    null,
                    warnings
            );
        }

        List<String> warnings = new ArrayList<>();
        PdfTextEvidence pdfEvidence = pdfTextEvidenceService == null
                ? PdfTextEvidence.unavailable("PDF text extraction was not configured in this test fixture.")
                : pdfTextEvidenceService.extract(pdfPath);
        warnings.addAll(pdfEvidence.warnings());
        logger.info("Paper understanding GROBID extraction started: publicationId={}", publicationId);
        GrobidPaperDocument grobidDocument = grobidMetadataService.extractDocument(pdfPath);
        if (doclingFallbackService != null && doclingFallbackService.shouldFallback(pdfEvidence, grobidDocument.teiXml())) {
            PdfTextEvidence doclingEvidence = doclingFallbackService.extract(pdfPath);
            if (doclingEvidence.available()) {
                pdfEvidence = doclingEvidence;
                warnings.addAll(doclingEvidence.warnings());
            }
        }
        DoiEnrichmentResult enrichment = doiEvidenceEnrichmentService == null
                ? DoiEnrichmentResult.empty(grobidDocument.metadata())
                : doiEvidenceEnrichmentService.enrich(grobidDocument, publication, pdfEvidence);
        GrobidMetadata grobidMetadata = enrichment.metadata();
        grobidDocument = new GrobidPaperDocument(
                grobidMetadata,
                grobidDocument.teiXml(),
                grobidDocument.warnings()
        );
        logger.info(
                "Paper understanding GROBID extraction completed: publicationId={}, available={}, fullTextAvailable={}, authorCount={}, year={}",
                publicationId,
                grobidMetadata.available(),
                grobidDocument.teiXml() != null,
                grobidMetadata.authors().size(),
                grobidMetadata.year()
        );
        warnings.addAll(grobidDocument.warnings());
        warnings.addAll(enrichment.warnings());

        ReferenceGroundingContext grounding = referenceGroundingService.extract(grobidDocument.teiXml());
        warnings.addAll(grounding.groundingWarnings());
        PaperEvidencePacket evidencePacket = evidencePacketBuilder.build(grobidDocument, enrichment, pdfEvidence);
        warnings.addAll(evidencePacket.warnings());
        qualityValidator.requireUsableEvidence(evidencePacket);
        logger.info(
                "Paper understanding compact evidence ready: publicationId={}, evidenceCharacters={}",
                publicationId,
                evidencePacket.characterCount()
        );

        PaperUnderstandingProviderResult providerResult = providerChain.resolve(evidencePacket);
        warnings.addAll(providerResult.warnings());

        PaperUnderstandingResult paperUnderstanding = mergeService.merge(
                grobidMetadata,
                providerResult.understanding(),
                providerResult.openAiFailureMessage(),
                grounding,
                publication.getPublicationYear(),
                evidencePacket,
                providerResult.sourceProvider()
        );
        warnings = new ArrayList<>(distinctWarnings(warnings, paperUnderstanding.warnings()));
        String source = providerResult.sourceProvider();
        qualityValidator.requireUsableUnderstanding(paperUnderstanding);
        if ("OPENAI".equalsIgnoreCase(source)) {
            understandingCacheService.save(
                    publicationId,
                    pdfHash,
                    evidencePacket,
                    paperUnderstanding,
                    grounding,
                    source,
                    providerResult.modelName(),
                    warnings
            );
        }
        logger.info(
                "Paper understanding resolution completed: publicationId={}, source={}, status={}, warningCount={}",
                publicationId,
                source,
                paperUnderstanding.status(),
                warnings.size()
        );
        return new ResolvedPaperUnderstanding(
                pdfHash,
                evidencePacket,
                grounding,
                paperUnderstanding,
                false,
                source,
                providerResult.openAiFailureMessage(),
                warnings
        );
    }

    private PaperUnderstandingResult withoutHistoricalProviderFailure(PaperUnderstandingResult understanding) {
        if (understanding == null) {
            return null;
        }
        return new PaperUnderstandingResult(
                understanding.status(),
                understanding.grobidMetadata(),
                understanding.providerUnderstanding(),
                understanding.finalUnderstanding(),
                withoutHistoricalProviderFailures(understanding.warnings()),
                understanding.understandingSource()
        );
    }

    private List<String> withoutHistoricalProviderFailures(List<String> warnings) {
        return warnings == null ? List.of() : warnings.stream()
                .filter(value -> value != null && !isHistoricalProviderFailure(value))
                .toList();
    }

    private boolean isHistoricalProviderFailure(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return (lower.contains("openai") || lower.contains("ollama")) && (
                lower.contains("timed out")
                        || lower.contains("timeout")
                        || lower.contains("unavailable")
                        || lower.contains("request failed")
                        || lower.contains("connection failed")
        );
    }

    @SafeVarargs
    private final List<String> distinctWarnings(List<String>... warningLists) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (List<String> warningList : warningLists) {
            if (warningList != null) {
                warningList.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .forEach(values::add);
            }
        }
        return List.copyOf(values);
    }
}
