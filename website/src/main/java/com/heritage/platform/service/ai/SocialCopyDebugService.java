package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.SocialCopyPipelineArtifacts;
import com.heritage.platform.dto.ai.SocialCopyProviderAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SocialCopyDebugService {

    private static final Logger logger = LoggerFactory.getLogger(SocialCopyDebugService.class);

    private final ObjectMapper objectMapper;

    @Value("${app.upload-dir:uploads}")
    private String uploadDirectory;

    public SocialCopyDebugService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            Long publicationId,
            OpenAiSocialCopyService.GeneratedSocialCopy generated,
            String source,
            String language,
            String tone,
            List<String> warnings
    ) {
        write(publicationId, generated, source, language, tone, warnings,
                List.of(), null, false, false);
    }

    public void write(
            Long publicationId,
            OpenAiSocialCopyService.GeneratedSocialCopy generated,
            String source,
            String language,
            String tone,
            List<String> warnings,
            List<SocialCopyProviderAttempt> providerAttempts,
            SocialCopyProviderAttempt openAiFailure,
            boolean deepSeekUsed,
            boolean compactMode
    ) {
        if (generated == null) {
            return;
        }
        SocialCopyPipelineArtifacts artifacts = generated.artifacts();
        try {
            Path directory = debugDirectory(publicationId);
            Files.createDirectories(directory);
            Files.deleteIfExists(directory.resolve("social-copy-openai-error.json"));
            Files.deleteIfExists(directory.resolve("social-copy-deepseek-result.json"));
            writeJson(directory, "social-copy-provider-attempts.json", Map.of(
                    "attempts", providerAttempts == null ? List.of() : providerAttempts,
                    "sourceSelected", source,
                    "compactMode", compactMode
            ));
            if (openAiFailure != null && openAiFailure.attempted() && !openAiFailure.success()) {
                writeJson(directory, "social-copy-openai-error.json", openAiFailure);
            }
            if (artifacts == null) {
                Map<String, Object> unavailable = Map.of(
                        "status", "NOT_CAPTURED",
                        "source", source,
                        "reason", "The generator did not return stage artifacts."
                );
                writeJson(directory, "social-copy-evidence-digest.json", unavailable);
                writeJson(directory, "social-copy-angle.json", unavailable);
                writeJson(directory, "social-copy-hook-candidates.json", unavailable);
                writeJson(directory, "social-copy-selected-hook.json", unavailable);
                writeJson(directory, "social-copy-draft-variants.json", unavailable);
                writeJson(directory, "social-copy-quality-review.json", unavailable);
            } else {
                writeJson(directory, "social-copy-evidence-digest.json", artifacts.evidenceDigest());
                writeJson(directory, "social-copy-angle.json", artifacts.angle());
                writeJson(directory, "social-copy-hook-candidates.json", Map.of(
                        "candidates", artifacts.hookCandidates(),
                        "rewritten", artifacts.selectedHook() != null && artifacts.selectedHook().hooksRewritten()
                ));
                writeJson(directory, "social-copy-selected-hook.json", artifacts.selectedHook());
                writeJson(directory, "social-copy-draft-variants.json", Map.of(
                        "variants", artifacts.draftVariants(),
                        "hashtags", artifacts.finalHashtags()
                ));
                writeJson(directory, "social-copy-quality-review.json", artifacts.qualityReview());
            }
            LinkedHashMap<String, Object> finalResult = new LinkedHashMap<>();
            finalResult.put("publicationId", publicationId);
            finalResult.put("source", source);
            finalResult.put("language", language);
            finalResult.put("selectedTone", tone);
            finalResult.put("variants", generated.variants());
            finalResult.put("hashtags", generated.hashtags());
            finalResult.put("rewritten", artifacts != null && artifacts.rewritten());
            finalResult.put("repaired", artifacts != null && artifacts.repaired());
            finalResult.put("validation", artifacts == null ? null : artifacts.finalValidation());
            finalResult.put("warnings", warnings == null ? List.of() : warnings);
            writeJson(directory, "social-copy-final-result.json", finalResult);
            if (deepSeekUsed) {
                writeJson(directory, "social-copy-deepseek-result.json", Map.of(
                        "source", source,
                        "variants", generated.variants(),
                        "hashtags", generated.hashtags(),
                        "validation", artifacts == null ? Map.of() : artifacts.finalValidation(),
                        "qualityReview", artifacts == null ? Map.of() : artifacts.qualityReview()
                ));
            }
        } catch (Exception ex) {
            logger.warn(
                    "Social-copy debug JSON could not be written: publicationId={}, reason={}",
                    publicationId, AiCoverDiagnostics.safeExceptionSummary(ex)
            );
        }
    }

    public void writeCached(
            Long publicationId,
            OpenAiSocialCopyService.GeneratedSocialCopy generated,
            String language,
            String tone
    ) {
        try {
            Path directory = debugDirectory(publicationId);
            Files.createDirectories(directory);
            Files.deleteIfExists(directory.resolve("social-copy-openai-error.json"));
            Files.deleteIfExists(directory.resolve("social-copy-deepseek-result.json"));
            Map<String, Object> preserved = Map.of(
                    "status", "FIXED_COPY_PRESERVED",
                    "source", "PAPER_EVIDENCE",
                    "note", "This publication matches one of the three intentionally preserved fixed copies."
            );
            writeJson(directory, "social-copy-evidence-digest.json", preserved);
            writeJson(directory, "social-copy-angle.json", preserved);
            writeJson(directory, "social-copy-hook-candidates.json", preserved);
            writeJson(directory, "social-copy-selected-hook.json", preserved);
            writeJson(directory, "social-copy-draft-variants.json", Map.of(
                    "status", "FIXED_COPY_PRESERVED", "variants", generated.variants()
            ));
            writeJson(directory, "social-copy-quality-review.json", preserved);
            writeJson(directory, "social-copy-provider-attempts.json", Map.of(
                    "attempts", List.of(),
                    "sourceSelected", "PAPER_EVIDENCE",
                    "compactMode", false
            ));
            writeJson(directory, "social-copy-final-result.json", Map.of(
                    "publicationId", publicationId,
                    "source", "PAPER_EVIDENCE",
                    "language", language,
                    "selectedTone", tone,
                    "variants", generated.variants(),
                    "hashtags", generated.hashtags(),
                    "fixedCopyPreserved", true
            ));
        } catch (Exception ex) {
            logger.warn(
                    "Fixed social-copy debug JSON could not be written: publicationId={}, reason={}",
                    publicationId, AiCoverDiagnostics.safeExceptionSummary(ex)
            );
        }
    }

    private Path debugDirectory(Long publicationId) {
        return Path.of(uploadDirectory == null || uploadDirectory.isBlank() ? "uploads" : uploadDirectory)
                .toAbsolutePath().normalize()
                .resolve("generated-covers")
                .resolve(String.valueOf(publicationId))
                .resolve("debug");
    }

    private void writeJson(Path directory, String filename, Object value) throws Exception {
        Path target = directory.resolve(filename);
        Path temporary = directory.resolve(filename + ".tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicMoveUnavailable) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
