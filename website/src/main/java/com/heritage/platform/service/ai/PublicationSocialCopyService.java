package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ForbiddenException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.ai.ResolvedPaperUnderstanding;
import com.heritage.platform.dto.ai.SocialCopyCandidate;
import com.heritage.platform.dto.ai.SocialCopyClaimVerification;
import com.heritage.platform.dto.ai.SocialCopyContentPlan;
import com.heritage.platform.dto.ai.SocialCopyContentSpec;
import com.heritage.platform.dto.ai.SocialCopyGenerationRequest;
import com.heritage.platform.dto.ai.SocialCopyGenerationResult;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.PostRepository;
import com.heritage.platform.service.AuthContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PublicationSocialCopyService {

    private static final Logger logger = LoggerFactory.getLogger(PublicationSocialCopyService.class);
    private static final String INSUFFICIENT_COPY_MESSAGE =
            "论文内容提取不足，暂时无法生成可靠的发布文案。请检查 PDF 提取、GROBID 和 DeepSeek 配置后重试。";

    private final AuthContextService authContextService;
    private final PostRepository postRepository;
    private final UploadPathService uploadPathService;
    private final PaperUnderstandingResolverService understandingResolverService;
    private final SocialCopyProviderRouter providerRouter;
    private final PaperAiQualityValidator qualityValidator;
    private final SocialCopyEvidenceDigestService evidenceDigestService;
    private final SocialCopyDebugService debugService;
    private final SocialCopyContentSpecService contentSpecService;
    private final EvidenceRetrievalService evidenceRetrievalService;
    private final SocialCopyClaimVerifier claimVerifier;

    @Autowired
    public PublicationSocialCopyService(
            AuthContextService authContextService,
            PostRepository postRepository,
            UploadPathService uploadPathService,
            PaperUnderstandingResolverService understandingResolverService,
            SocialCopyProviderRouter providerRouter,
            PaperAiQualityValidator qualityValidator,
            SocialCopyEvidenceDigestService evidenceDigestService,
            SocialCopyDebugService debugService,
            SocialCopyContentSpecService contentSpecService,
            EvidenceRetrievalService evidenceRetrievalService,
            SocialCopyClaimVerifier claimVerifier
    ) {
        this.authContextService = authContextService;
        this.postRepository = postRepository;
        this.uploadPathService = uploadPathService;
        this.understandingResolverService = understandingResolverService;
        this.providerRouter = providerRouter;
        this.qualityValidator = qualityValidator;
        this.evidenceDigestService = evidenceDigestService;
        this.debugService = debugService;
        this.contentSpecService = contentSpecService;
        this.evidenceRetrievalService = evidenceRetrievalService;
        this.claimVerifier = claimVerifier;
    }

    PublicationSocialCopyService(
            AuthContextService authContextService,
            PostRepository postRepository,
            UploadPathService uploadPathService,
            PaperUnderstandingResolverService understandingResolverService,
            OpenAiSocialCopyService openAiSocialCopyService,
            PaperAiQualityValidator qualityValidator
    ) {
        this(
                authContextService, postRepository, uploadPathService, understandingResolverService,
                legacyRouter(openAiSocialCopyService), qualityValidator,
                new SocialCopyEvidenceDigestService(),
                new SocialCopyDebugService(new ObjectMapper()), new SocialCopyContentSpecService(),
                new EvidenceRetrievalService(), new SocialCopyClaimVerifier()
        );
    }

    @Transactional(readOnly = true)
    public SocialCopyGenerationResult generate(Long publicationId, SocialCopyGenerationRequest request) {
        User currentUser = authContextService.requireActiveUser();
        Post publication = findPublication(publicationId);
        requireOwnerOrAdmin(publication, currentUser);
        String language = normalizeLanguage(request == null ? null : request.language());
        String tone = normalizeTone(request == null ? null : request.tone());
        SocialCopyContentSpec contentSpec = contentSpecService.normalize(request, language, tone);
        Path pdfPath = uploadPathService.resolveUploadedPdf(publication.getPdfUrl());

        try {
            ResolvedPaperUnderstanding resolved = understandingResolverService.resolve(publicationId, publication, pdfPath);
            var digest = evidenceDigestService.build(
                    resolved.paperUnderstanding().finalUnderstanding(), resolved.evidencePacket()
            );
            SocialCopyContentPlan contentPlan = contentSpecService.plan(contentSpec, digest);
            var retrievedEvidence = evidenceRetrievalService.retrieve(resolved.evidencePacket(), contentSpec, contentPlan);
            var retrievalScopedEvidence = retrievedEvidence.isEmpty()
                    ? resolved.evidencePacket()
                    : resolved.evidencePacket().withEvidenceSpans(retrievedEvidence);
            if (!qualityValidator.isUsableUnderstanding(resolved.paperUnderstanding())
                    && !evidenceDigestService.isUsable(digest)) {
                throw new AiCoverWorkflowException("No usable paper understanding or evidence was available.");
            }
            List<String> warnings = new ArrayList<>(resolved.warnings());
            SocialCopyProviderRouter.RoutedSocialCopy routed = providerRouter.generate(
                    resolved.paperUnderstanding().finalUnderstanding(), retrievalScopedEvidence, language,
                    contentSpec, contentPlan
            );
            String source = routed.source();
            OpenAiSocialCopyService.GeneratedSocialCopy generated = routed.generated();
            warnings.add("已由 DeepSeek 基于论文证据生成发布文案，请发布前人工检查。");
            String copyText = generated.variants().get(tone);
            if (copyText == null) {
                copyText = generated.variants().get("engaging");
                tone = "engaging";
            }
            logger.info(
                    "Social copy generation completed: publicationId={}, source={}, language={}, tone={}, characterCount={}",
                    publicationId, source, language, tone, copyText.length()
            );
            List<String> finalWarnings = warnings.stream()
                    .filter(value -> value != null && !value.isBlank()).distinct().toList();
            debugService.write(
                    publicationId, generated, source, language, tone, finalWarnings,
                    routed.attempts(), routed.openAiFailure(), routed.deepSeekUsed(), routed.compactMode()
            );
            List<SocialCopyCandidate> candidates = buildCandidates(generated.variants(), contentSpec, retrievedEvidence);
            SocialCopyCandidate recommended = candidates.stream().filter(SocialCopyCandidate::recommended).findFirst().orElse(null);
            List<SocialCopyClaimVerification> checks = recommended == null ? List.of() : recommended.claimChecks();
            if (checks.stream().anyMatch(check -> "UNSUPPORTED".equals(check.verdict()))) {
                warnings.add("部分强主张未找到足够论文证据；系统已标出，发布前请人工修改或确认。");
            }
            return new SocialCopyGenerationResult(
                    publicationId,
                    language,
                    tone,
                    "social-media-ready",
                    source,
                    copyText,
                    generated.variants(),
                    generated.hashtags(),
                    finalWarnings,
                    resolved.paperUnderstanding().finalUnderstanding().confidenceLevel(),
                    contentSpec,
                    contentPlan,
                    retrievedEvidence,
                    candidates,
                    checks
            );
        } catch (AiCoverWorkflowException ex) {
            logger.warn(
                    "LLM social copy generation stopped: publicationId={}, reason={}",
                    publicationId, AiCoverDiagnostics.safeExceptionSummary(ex)
            );
            throw new BadRequestException(deepSeekFailureMessage(ex, language));
        }
    }

    private String deepSeekFailureMessage(AiCoverWorkflowException exception, String language) {
        if (!(exception instanceof SocialCopyProviderException failure)) {
            return "en".equals(language)
                    ? "Paper extraction or DeepSeek generation was insufficient; reliable social copy was not generated."
                    : INSUFFICIENT_COPY_MESSAGE;
        }
        return switch (failure.failureReason()) {
            case CONFIGURATION, DISABLED -> "en".equals(language)
                    ? "DeepSeek is not configured. Set DEEPSEEK_API_KEY and enable the DeepSeek service."
                    : "DeepSeek 尚未配置。请设置 DEEPSEEK_API_KEY，并启用 DeepSeek 服务后重试。";
            case INSUFFICIENT_CREDIT -> "en".equals(language)
                    ? "DeepSeek account credit is insufficient. Recharge the DeepSeek account and try again."
                    : "DeepSeek 账户余额或可用额度不足。请充值后重试。";
            case AUTHENTICATION -> "en".equals(language)
                    ? "DeepSeek authentication failed. Check DEEPSEEK_API_KEY and try again."
                    : "DeepSeek API 密钥验证失败。请检查 DEEPSEEK_API_KEY 后重试。";
            case RATE_LIMITED -> "en".equals(language)
                    ? "DeepSeek is receiving too many requests. Please wait and try again."
                    : "DeepSeek 当前请求过于频繁，请稍后重试。";
            case TIMEOUT, TEMPORARY_ERROR -> "en".equals(language)
                    ? "DeepSeek is temporarily unavailable. Please try again later."
                    : "DeepSeek 暂时不可用或请求超时，请稍后重试。";
            default -> "en".equals(language)
                    ? "DeepSeek did not return a usable copy. Please try again."
                    : "DeepSeek 未返回可用文案，请稍后重试。";
        };
    }

    private static SocialCopyProviderRouter legacyRouter(OpenAiSocialCopyService openAiSocialCopyService) {
        SocialCopyStructuredJsonClient enabledOpenAi = new SocialCopyStructuredJsonClient() {
            @Override
            public String providerName() {
                return "OPENAI";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String requestJson(String systemPrompt, String userPrompt, String schemaName, Map<String, Object> schema) {
                throw new UnsupportedOperationException("Legacy non-compact test route does not call this method.");
            }
        };
        return new SocialCopyProviderRouter(
                openAiSocialCopyService, enabledOpenAi, null, new EvidenceFallbackSocialCopyService(),
                false, "openai,evidence_fallback"
        );
    }

    private Post findPublication(Long publicationId) {
        Post publication = postRepository.findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (!publication.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        return publication;
    }

    private void requireOwnerOrAdmin(Post publication, User currentUser) {
        boolean admin = currentUser.getRole() == UserRole.ADMIN;
        boolean owner = publication.getAuthor() != null
                && publication.getAuthor().getId() != null
                && publication.getAuthor().getId().equals(currentUser.getId());
        if (!admin && !owner) {
            throw new ForbiddenException("Only the publication uploader or an administrator can generate social copy.");
        }
    }

    private String normalizeLanguage(String value) {
        return "en".equalsIgnoreCase(value == null ? "" : value.trim()) ? "en" : "zh";
    }

    private String normalizeTone(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "concise", "academic", "professional" -> normalized;
            default -> "engaging";
        };
    }

    private List<SocialCopyCandidate> buildCandidates(
            Map<String, String> variants,
            SocialCopyContentSpec spec,
            List<com.heritage.platform.dto.ai.EvidenceSpan> evidence
    ) {
        List<SocialCopyCandidate> candidates = new ArrayList<>();
        for (String style : List.of("engaging", "concise", "professional")) {
            String copy = variants == null ? null : variants.get(style);
            if (copy == null || copy.isBlank()) continue;
            List<SocialCopyClaimVerification> checks = claimVerifier.verify(copy, evidence);
            long unsupported = checks.stream().filter(check -> "UNSUPPORTED".equals(check.verdict())).count();
            double factuality = Math.max(0d, 5d - unsupported * 1.5d);
            double audienceFit = "engaging".equals(style) && "public".equals(spec.audience()) ? 5d : 4d;
            double goalFit = 4d;
            double xiaohongshuFit = "engaging".equals(style) ? 5d : 4d;
            candidates.add(new SocialCopyCandidate(style, style, copy, factuality, audienceFit, goalFit,
                    xiaohongshuFit, false, checks));
        }
        if (candidates.isEmpty()) return List.of();
        int best = 0;
        double bestScore = -1d;
        for (int index = 0; index < candidates.size(); index++) {
            SocialCopyCandidate candidate = candidates.get(index);
            double score = candidate.factualityScore() * 0.45d + candidate.audienceFitScore() * 0.2d
                    + candidate.goalFitScore() * 0.15d + candidate.xiaohongshuFitScore() * 0.2d;
            if (score > bestScore) { bestScore = score; best = index; }
        }
        List<SocialCopyCandidate> ranked = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            SocialCopyCandidate value = candidates.get(index);
            ranked.add(new SocialCopyCandidate(value.id(), value.style(), value.copyText(), value.factualityScore(),
                    value.audienceFitScore(), value.goalFitScore(), value.xiaohongshuFitScore(), index == best,
                    value.claimChecks()));
        }
        return List.copyOf(ranked);
    }

    private com.heritage.platform.dto.ai.FinalPaperUnderstanding applyContentSpec(
            com.heritage.platform.dto.ai.FinalPaperUnderstanding value,
            SocialCopyContentSpec spec,
            SocialCopyContentPlan plan
    ) {
        String target = "Xiaohongshu target reader: " + plan.audienceGuidance()
                + "; selected audience=" + spec.audience() + "; requested length=" + spec.length();
        String valueWithGoal = plan.goalGuidance() + " Communication goal=" + spec.goal()
                + ". Required CTA: " + plan.callToAction() + ". " + safe(value.whyItMatters());
        return new com.heritage.platform.dto.ai.FinalPaperUnderstanding(
                value.title(), value.abstractSummary(), value.authors(), value.year(), value.researchProblem(), target,
                value.method(), value.keyImplementation(), value.keyContribution(), value.importantSystemComponents(),
                value.inputOutputRelationship(), valueWithGoal, value.possibleVisualMetaphor(), value.forbiddenVisualElements(),
                value.likelyApplicationScenario(), value.visualizableEntities(), value.visualizableInteractions(),
                value.visualizableEnvironment(), value.confidenceLevel(), value.venue(), value.proposedSystemOrMethod(),
                value.visibleInterfaceOrDevice(), value.visibleInput(), value.visibleOutput(), value.expectedOutcome(),
                value.mustShowElements(), value.mustAvoidElements(), value.applicationEnvironment(), value.mainTaskOrWorkflow(),
                value.visibleInteraction()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasExplicitContentSpec(SocialCopyGenerationRequest request) {
        return request != null && (notBlank(request.audience()) || notBlank(request.goal())
                || notBlank(request.length()) || notBlank(request.callToAction()));
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
