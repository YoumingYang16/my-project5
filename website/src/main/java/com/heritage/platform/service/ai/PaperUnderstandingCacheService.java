package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingCacheEntry;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class PaperUnderstandingCacheService {

    // Version 6 requires the full-text evidence prompt and validated dual-composition brief.
    static final int CACHE_VERSION = 6;
    private static final String CACHE_FILE = "paper-understanding-cache.json";
    private static final Logger logger = LoggerFactory.getLogger(PaperUnderstandingCacheService.class);

    private final ObjectMapper objectMapper;
    private final UploadPathService uploadPathService;
    private final PaperAiQualityValidator qualityValidator;

    public PaperUnderstandingCacheService(
            ObjectMapper objectMapper,
            UploadPathService uploadPathService,
            PaperAiQualityValidator qualityValidator
    ) {
        this.objectMapper = objectMapper;
        this.uploadPathService = uploadPathService;
        this.qualityValidator = qualityValidator;
    }

    public String pdfHash(Path pdfPath) {
        try (InputStream input = Files.newInputStream(pdfPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new AiCoverWorkflowException("The uploaded PDF could not be hashed for paper understanding cache.", ex);
        }
    }

    public Optional<PaperUnderstandingCacheEntry> find(Long publicationId, String pdfHash) {
        Path cachePath = cachePath(publicationId, false);
        if (cachePath == null || !Files.isRegularFile(cachePath)) {
            logger.info("Paper understanding cache miss: publicationId={}, reason=not-found", publicationId);
            return Optional.empty();
        }
        try {
            PaperUnderstandingCacheEntry entry = objectMapper.readValue(
                    cachePath.toFile(),
                    PaperUnderstandingCacheEntry.class
            );
            if (!isValid(entry, pdfHash)) {
                logger.info("Paper understanding cache miss: publicationId={}, reason=stale-or-invalid", publicationId);
                return Optional.empty();
            }
            logger.info(
                    "Paper understanding cache hit: publicationId={}, source={}, updatedAt={}",
                    publicationId,
                    entry.understandingSource(),
                    entry.understandingUpdatedAt()
            );
            return Optional.of(entry);
        } catch (Exception ex) {
            logger.warn(
                    "Paper understanding cache read failed: publicationId={}, failure={}",
                    publicationId,
                    AiCoverDiagnostics.safeExceptionSummary(ex)
            );
            return Optional.empty();
        }
    }

    public Optional<PaperUnderstandingCacheEntry> find(Long publicationId, String pdfHash, String modelName) {
        return find(publicationId, pdfHash);
    }

    public void save(
            Long publicationId,
            String pdfHash,
            PaperEvidencePacket evidencePacket,
            PaperUnderstandingResult paperUnderstanding,
            ReferenceGroundingContext grounding,
            String understandingSource,
            String modelName,
            List<String> warnings
    ) {
        if (!"OPENAI".equalsIgnoreCase(understandingSource)
                || !qualityValidator.hasSufficientEvidence(evidencePacket)
                || !qualityValidator.isAuthoritativeOpenAi(paperUnderstanding)) {
            logger.info("Paper understanding cache save skipped: publicationId={}, reason=non-authoritative", publicationId);
            return;
        }
        try {
            Path cachePath = cachePath(publicationId, true);
            Path temporary = cachePath.resolveSibling(CACHE_FILE + ".tmp");
            PaperUnderstandingCacheEntry entry = new PaperUnderstandingCacheEntry(
                    CACHE_VERSION,
                    pdfHash,
                    evidencePacket,
                    paperUnderstanding,
                    grounding,
                    understandingSource,
                    Instant.now().toString(),
                    modelName,
                    warnings
            );
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), entry);
            try {
                Files.move(
                        temporary,
                        cachePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info(
                    "Paper understanding cache saved: publicationId={}, source={}, evidenceCharacters={}",
                    publicationId,
                    understandingSource,
                    evidencePacket.characterCount()
            );
        } catch (Exception ex) {
            logger.warn(
                    "Paper understanding cache save failed: publicationId={}, failure={}",
                    publicationId,
                    AiCoverDiagnostics.safeExceptionSummary(ex)
            );
        }
    }

    private boolean isValid(PaperUnderstandingCacheEntry entry, String pdfHash) {
        return entry != null
                && entry.cacheVersion() == CACHE_VERSION
                && pdfHash != null
                && pdfHash.equals(entry.pdfHash())
                && entry.evidencePacket() != null
                && qualityValidator.hasSufficientEvidence(entry.evidencePacket())
                && entry.paperUnderstanding() != null
                && entry.paperUnderstanding().finalUnderstanding() != null
                && "OPENAI".equalsIgnoreCase(entry.understandingSource())
                && qualityValidator.isAuthoritativeOpenAi(entry.paperUnderstanding())
                && entry.grounding() != null;
    }

    public String understandingHash(PaperUnderstandingResult understanding) {
        if (!qualityValidator.isUsableUnderstanding(understanding)) {
            throw new AiCoverWorkflowException("The paper understanding was not usable enough to hash.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    objectMapper.writeValueAsBytes(understanding.finalUnderstanding())
            ));
        } catch (Exception ex) {
            throw new AiCoverWorkflowException("The paper understanding could not be hashed.", ex);
        }
    }

    private Path cachePath(Long publicationId, boolean createDirectory) {
        try {
            Path directory = createDirectory
                    ? uploadPathService.generatedCoverDirectory(publicationId)
                    : uploadPathService.existingGeneratedCoverDirectory(publicationId);
            Path cachePath = directory.resolve(CACHE_FILE).normalize();
            if (!cachePath.startsWith(directory)) {
                throw new AiCoverWorkflowException("Paper understanding cache path is invalid.");
            }
            return cachePath;
        } catch (Exception ex) {
            if (createDirectory) {
                throw new AiCoverWorkflowException("Paper understanding cache directory could not be prepared.", ex);
            }
            return null;
        }
    }
}
