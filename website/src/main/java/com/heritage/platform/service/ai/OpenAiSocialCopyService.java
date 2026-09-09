package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.CopyQualityReview;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.HookCandidate;
import com.heritage.platform.dto.ai.HookScore;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.SocialCopyAngle;
import com.heritage.platform.dto.ai.SocialCopyEvidenceDigest;
import com.heritage.platform.dto.ai.SocialCopyFinalValidation;
import com.heritage.platform.dto.ai.SocialCopyPipelineArtifacts;
import com.heritage.platform.dto.ai.SocialCopySelectedHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OpenAiSocialCopyService {

    private static final List<String> TONES = List.of("engaging", "concise", "professional");
    private static final String EDITOR_SYSTEM_PROMPT = """
            You are a research communication editor and Xiaohongshu / WeChat-style paper recommendation copywriter.

            Your job is not to summarize the abstract.
            Your job is to turn the paper into an attractive, grounded, reader-friendly recommendation post.

            Use this structure:
            problem scenario -> proposed system/method -> how it is used -> why valuable -> who should read.

            Start from a relatable pain point. Use light emojis naturally. Be positive and engaging.
            Stay faithful to evidence. Do not invent unsupported claims. Do not invent metrics, user-study
            results, deployments, awards, or capabilities. Do not use placeholders. Do not use ellipses.
            Do not output abstract-like wording. If evidence is incomplete, write cautiously but still complete
            the copy. Return only JSON matching the requested schema.
            """;

    private final ObjectMapper objectMapper;
    private final OpenAiStructuredResponseClient openAiClient;
    private final PaperAiQualityValidator qualityValidator;
    private final SocialCopyEvidenceDigestService digestService;
    private final SocialCopyFinalValidator finalValidator;

    @Autowired
    public OpenAiSocialCopyService(
            ObjectMapper objectMapper,
            OpenAiStructuredResponseClient openAiClient,
            PaperAiQualityValidator qualityValidator,
            SocialCopyEvidenceDigestService digestService,
            SocialCopyFinalValidator finalValidator
    ) {
        this.objectMapper = objectMapper;
        this.openAiClient = openAiClient;
        this.qualityValidator = qualityValidator;
        this.digestService = digestService;
        this.finalValidator = finalValidator;
    }

    OpenAiSocialCopyService(
            ObjectMapper objectMapper,
            OpenAiStructuredResponseClient openAiClient,
            PaperAiQualityValidator qualityValidator
    ) {
        this(objectMapper, openAiClient, qualityValidator,
                new SocialCopyEvidenceDigestService(), new SocialCopyFinalValidator());
    }

    public GeneratedSocialCopy generate(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language
    ) {
        SocialCopyEvidenceDigest digest = digestService.build(understanding, evidence);
        if (!digestService.isUsable(digest)) {
            throw new AiCoverWorkflowException("Paper evidence was insufficient for grounded social copy.");
        }

        return generatePipeline(digest, language);
    }

    public GeneratedSocialCopy generateCompact(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language,
            SocialCopyStructuredJsonClient client
    ) {
        return generateCompact(understanding, evidence, language, client, EDITOR_SYSTEM_PROMPT);
    }

    public GeneratedSocialCopy generateCompact(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence,
            String language,
            SocialCopyStructuredJsonClient client,
            String systemPrompt
    ) {
        SocialCopyEvidenceDigest digest = digestService.build(understanding, evidence);
        if (!digestService.isUsable(digest)) {
            throw providerFailure(client, SocialCopyFailureReason.INVALID_RESPONSE,
                    "Paper evidence was insufficient for grounded social copy.", null);
        }
        String response = client.requestJson(
                systemPrompt,
                compactPrompt(digest, language),
                "social_copy_compact_pipeline",
                compactSchema()
        );
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root == null || !root.isObject()) {
                throw providerFailure(client, SocialCopyFailureReason.JSON_PARSE_FAILURE,
                        "The compact social-copy response was not a JSON object.", null);
            }
            SocialCopyAngle angle = angleFrom(root.path("angle"), digest);
            List<String> compactWarnings = strings(root.path("warnings"), 8);
            if (!compactWarnings.isEmpty()) {
                LinkedHashSet<String> combinedWarnings = new LinkedHashSet<>(angle.warnings());
                combinedWarnings.addAll(compactWarnings);
                angle = new SocialCopyAngle(
                        angle.painPoint(), angle.openingHookIntent(), angle.proposedSystem(),
                        angle.usageScenario(), angle.howItWorks(), angle.mostInterestingPoint(),
                        angle.whyValuable(), angle.targetReaders(), angle.topicAreas(), angle.hashtags(),
                        angle.confidenceLevel(), List.copyOf(combinedWarnings)
                );
            }
            List<String> tags = hashtags(root.path("hashtags"), digest.hashtagCandidates());
            tags = minimumHashtags(tags, language);
            String finalCopy = ensureHashtags(clean(root.path("finalCopy").asText(null)), tags);
            if (!basicValidCopy(finalCopy, language)) {
                throw providerFailure(client, SocialCopyFailureReason.INVALID_RESPONSE,
                        "The compact response did not contain a usable final copy.", null);
            }
            SocialCopyFinalValidation validation = finalValidator.validate(
                    finalCopy, language, digest, false
            );
            if (!validation.valid()) {
                throw providerFailure(
                        client,
                        SocialCopyFailureReason.INVALID_RESPONSE,
                        "The compact final copy failed provider-independent validation: "
                                + String.join(" ", validation.issues()),
                        null
                );
            }
            // The only required provider result is a grounded final post.  The remaining
            // fields are optional diagnostics and are recovered locally from PDF evidence.
            List<HookCandidate> hooks = hooksFrom(root.path("hookCandidates"), digest);
            SocialCopySelectedHook selected = selectedHookFrom(root.path("selectedHook"), hooks);
            VariantSet drafts = compactVariants(root.path("variants"), tags, language, finalCopy, client);
            CopyQualityReview review = reviewFrom(root.path("qualityReview"));
            LinkedHashMap<String, String> finalVariants = new LinkedHashMap<>(drafts.variants());
            finalVariants.put("engaging", finalCopy);
            Map<String, String> apiVariants = apiVariants(finalVariants);
            SocialCopyPipelineArtifacts artifacts = new SocialCopyPipelineArtifacts(
                    digest, angle, hooks, selected, drafts.variants(), review, validation,
                    apiVariants, tags, review.requiresRewrite(), false
            );
            return new GeneratedSocialCopy(apiVariants, tags, artifacts);
        } catch (SocialCopyProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw providerFailure(
                    client, SocialCopyFailureReason.JSON_PARSE_FAILURE,
                    "The compact social-copy JSON could not be parsed.", ex
            );
        }
    }

    private GeneratedSocialCopy generatePipeline(SocialCopyEvidenceDigest digest, String language) {
        SocialCopyAngle angle = generateAngle(digest, language);
        List<HookCandidate> hooks = generateHooks(digest, angle, language, null);
        SocialCopySelectedHook selected = selectHook(digest, angle, hooks, language, false);
        if (selectedHookIsWeak(selected)) {
            hooks = generateHooks(
                    digest, angle, language,
                    "The first hook set scored below 4/5. Make every hook more relatable, specific, attractive, "
                            + "evidence-grounded, and natural for Xiaohongshu/WeChat."
            );
            selected = selectHook(digest, angle, hooks, language, true);
        }

        VariantSet draft = generateDrafts(digest, angle, selected, language);
        CopyQualityReview review = review(digest, angle, selected, draft, language);
        VariantSet finalSet = draft;
        boolean rewritten = false;
        if (review.requiresRewrite()) {
            finalSet = rewrite(digest, angle, selected, draft, review, language);
            rewritten = true;
        }

        SocialCopyFinalValidation validation = finalValidator.validate(
                finalSet.variants().get("engaging"), language, digest, false
        );
        boolean repaired = false;
        if (!validation.valid()) {
            finalSet = repair(digest, angle, selected, finalSet, validation, language);
            repaired = true;
            validation = finalValidator.validate(
                    finalSet.variants().get("engaging"), language, digest, true
            );
        }
        if (!validation.valid()) {
            throw new AiCoverWorkflowException(
                    "OpenAI social copy failed final validation after one targeted repair: "
                            + String.join(" ", validation.issues())
            );
        }

        Map<String, String> apiVariants = apiVariants(finalSet.variants());
        SocialCopyPipelineArtifacts artifacts = new SocialCopyPipelineArtifacts(
                digest, angle, hooks, selected, draft.variants(), review, validation,
                apiVariants, finalSet.hashtags(), rewritten, repaired
        );
        return new GeneratedSocialCopy(apiVariants, finalSet.hashtags(), artifacts);
    }

    public SocialCopyEvidenceDigest evidenceDigest(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence
    ) {
        return digestService.build(understanding, evidence);
    }

    private SocialCopyAngle generateAngle(SocialCopyEvidenceDigest digest, String language) {
        JsonNode root = request(
                "social_copy_angle",
                angleSchema(),
                """
                        Stage 2 - SocialCopyAngle.
                        Build the strongest truthful communication angle in %s from this compact evidence digest.
                        Answer what readers can relate to, what the paper proposes, how it is used, its most
                        interesting evidence-supported point, why it matters, who should read it, and which topic
                        hashtags fit. Do not leave a field blank. If evidence is incomplete, use cautious complete
                        wording and add a warning instead of inventing details.

                        SocialCopyEvidenceDigest:
                        %s
                        """.formatted(requestedLanguage(language), json(digest))
        );
        return angleFrom(root, digest);
    }

    private SocialCopyAngle angleFrom(JsonNode root, SocialCopyEvidenceDigest digest) {
        List<String> warnings = strings(root.path("warnings"), 8);
        return new SocialCopyAngle(
                fallback(root, "painPoint", digest.problemEvidence()),
                fallback(root, "openingHookIntent", "Open with a relatable situation grounded in the problem evidence."),
                fallback(root, "proposedSystem", digest.proposedSystemEvidence()),
                fallback(root, "usageScenario", digest.usageEvidence()),
                fallback(root, "howItWorks", digest.usageEvidence()),
                fallback(root, "mostInterestingPoint", digest.valueEvidence()),
                fallback(root, "whyValuable", digest.valueEvidence()),
                fallbackList(root.path("targetReaders"), digest.targetAudienceEvidence()),
                fallbackList(root.path("topicAreas"), digest.domain()),
                hashtags(root.path("hashtags"), digest.hashtagCandidates()),
                normalizeConfidence(fallback(root, "confidenceLevel", digest.confidenceLevel())),
                warnings
        );
    }

    private String compactPrompt(SocialCopyEvidenceDigest digest, String language) {
        return """
                Run the complete social-copy pipeline in one call and return JSON only.

                Return only one small JSON object with these fields:
                - finalCopy: one finished, publication-ready post
                - hashtags: 3 to 6 relevant hashtags without #
                - warnings: an optional short array

                Write in %s. Use this structure in every copy:
                problem scenario -> actual proposed system/method -> how it is used -> why valuable -> who should read.
                For Chinese, begin with 📎【论文分享】 and use 🤔, 🧩, 🛠️, ✅, and 👀 naturally.
                Do not return candidate hooks, scores, variants, analyses, or a quality review. Do not use placeholders,
                test1, ellipses, abstract-style
                openings, truncated fragments, unsupported metrics, studies, deployments, or awards. A faithful
                Chinese paraphrase of an English system name is valid.

                Compact SocialCopyEvidenceDigest:
                %s
                """.formatted(requestedLanguage(language), json(digest));
    }

    private List<HookCandidate> generateHooks(
            SocialCopyEvidenceDigest digest,
            SocialCopyAngle angle,
            String language,
            String revisionInstruction
    ) {
        String revision = revisionInstruction == null ? "" : "\nRevision instruction: " + revisionInstruction;
        JsonNode root = request(
                "social_copy_hook_candidates",
                hooksSchema(),
                """
                        Stage 3 - HookCandidateGeneration.
                        Generate 3 to 5 distinct opening hooks in %s from SocialCopyAngle. Each hook must begin
                        from a concrete pain point, scenario, question, contrast, or curiosity gap that makes a
                        reader think "I have encountered that". Avoid generic academic language, fake claims,
                        placeholders, ellipses, and truncated fragments. groundingEvidence must briefly identify
                        the supporting digest evidence; do not paste long evidence.%s

                        Evidence digest:
                        %s

                        SocialCopyAngle:
                        %s
                        """.formatted(requestedLanguage(language), revision, json(digest), json(angle))
        );
        List<HookCandidate> hooks = new ArrayList<>();
        for (JsonNode node : root.path("candidates")) {
            HookCandidate candidate = new HookCandidate(
                    clean(node.path("hookText").asText(null)),
                    normalizeHookType(node.path("hookType").asText(null)),
                    clean(node.path("whyItWorks").asText(null)),
                    clean(node.path("groundingEvidence").asText(null))
            );
            if (validHook(candidate)) {
                hooks.add(candidate);
            }
        }
        if (hooks.size() < 3 || hooks.size() > 5) {
            throw new AiCoverWorkflowException("OpenAI did not return 3 to 5 usable hook candidates.");
        }
        return List.copyOf(hooks);
    }

    private SocialCopySelectedHook selectHook(
            SocialCopyEvidenceDigest digest,
            SocialCopyAngle angle,
            List<HookCandidate> hooks,
            String language,
            boolean rewritten
    ) {
        JsonNode root = request(
                "social_copy_hook_selection",
                hookSelectionSchema(),
                """
                        Stage 4 - HookSelection.
                        Score every candidate from 1 to 5 for relatability, specificity, attractiveness,
                        groundingInEvidence, fitForXiaohongshu, and notAbstractLike. Select the strongest truthful
                        hook for a %s post. candidateIndex is zero-based and must match the supplied array.

                        Evidence digest:
                        %s

                        SocialCopyAngle:
                        %s

                        Hook candidates:
                        %s
                        """.formatted(requestedLanguage(language), json(digest), json(angle), json(hooks))
        );
        List<HookScore> scores = new ArrayList<>();
        for (JsonNode node : root.path("evaluations")) {
            int index = node.path("candidateIndex").asInt(-1);
            if (index < 0 || index >= hooks.size()) {
                continue;
            }
            int relatability = score(node, "relatability");
            int specificity = score(node, "specificity");
            int attractiveness = score(node, "attractiveness");
            int grounding = score(node, "groundingInEvidence");
            int fit = score(node, "fitForXiaohongshu");
            int notAbstract = score(node, "notAbstractLike");
            double average = (relatability + specificity + attractiveness + grounding + fit + notAbstract) / 6.0;
            scores.add(new HookScore(
                    index, relatability, specificity, attractiveness, grounding, fit, notAbstract,
                    Math.round(average * 100.0) / 100.0, clean(node.path("reason").asText(null))
            ));
        }
        if (scores.size() != hooks.size()) {
            throw new AiCoverWorkflowException("OpenAI did not score every hook candidate.");
        }
        int selectedIndex = root.path("selectedCandidateIndex").asInt(-1);
        if (selectedIndex < 0 || selectedIndex >= hooks.size()) {
            selectedIndex = scores.stream().max((left, right) -> Double.compare(left.averageScore(), right.averageScore()))
                    .map(HookScore::candidateIndex).orElse(0);
        }
        return new SocialCopySelectedHook(
                hooks.get(selectedIndex), selectedIndex, scores, rewritten,
                fallback(root, "rationale", "Selected from the scored evidence-grounded candidates.")
        );
    }

    private VariantSet generateDrafts(
            SocialCopyEvidenceDigest digest,
            SocialCopyAngle angle,
            SocialCopySelectedHook selected,
            String language
    ) {
        return variantsFrom(request(
                "social_copy_draft_variants",
                variantsSchema(),
                """
                        Stage 5 - DraftCopyVariants.
                        Write exactly three grounded variants in %s: engaging, concise, and professional.
                        The engaging version is the default Xiaohongshu/WeChat recommendation and should normally
                        be about 180-350 Chinese characters excluding hashtags when writing Chinese. The concise
                        version is shorter and punchier. The professional version is slightly more formal for a
                        publication archive or LinkedIn-style context.

                        Use the selected hook verbatim or make only a tiny fluency edit. Every variant must contain:
                        problem/scenario -> actual proposed system or method -> how a person uses it -> why it is
                        interesting or valuable -> who should read -> 3 to 6 grounded hashtags.

                        For Chinese, begin engaging with "📎【论文分享】" and use 🤔, 🧩, 🛠️, ✅, and 👀 as light
                        structural markers. Do not output bracket placeholders. Do not invent facts. Do not use
                        ellipses or abstract-style wording. A good Chinese translation or paraphrase of an English
                        system name is allowed; exact English-token matching is not required.

                        Evidence digest:
                        %s

                        SocialCopyAngle:
                        %s

                        Selected hook:
                        %s
                        """.formatted(requestedLanguage(language), json(digest), json(angle), json(selected))
        ), language);
    }

    private CopyQualityReview review(
            SocialCopyEvidenceDigest digest,
            SocialCopyAngle angle,
            SocialCopySelectedHook selected,
            VariantSet draft,
            String language
    ) {
        JsonNode root = request(
                "social_copy_quality_review",
                reviewSchema(),
                """
                        Stage 6 - CopyQualityReview.
                        Audit the default engaging %s copy against the evidence. Score every category from 1 to 5.
                        Set shouldRewrite=true if any important score is below 4, the hook is vague or abstract,
                        how-it-is-used or target readers are missing, any claim is unsupported, or the copy contains
                        a placeholder, test1, an ellipsis, or a truncated fragment. Be strict but allow faithful
                        Chinese translation and paraphrase of the proposed system or method.

                        Evidence digest:
                        %s

                        SocialCopyAngle:
                        %s

                        Selected hook:
                        %s

                        Engaging draft:
                        %s
                        """.formatted(requestedLanguage(language), json(digest), json(angle), json(selected),
                        json(draft.variants().get("engaging")))
        );
        CopyQualityReview modelReview = reviewFrom(root);
        SocialCopyFinalValidation localDraftCheck = finalValidator.validate(
                draft.variants().get("engaging"), language, digest, false
        );
        if (localDraftCheck.valid()) {
            return modelReview;
        }
        List<String> problems = new ArrayList<>(modelReview.problems());
        localDraftCheck.issues().forEach(issue -> problems.add("Local validation: " + issue));
        String engaging = draft.variants().get("engaging");
        return new CopyQualityReview(
                modelReview.hookAttractiveness(), modelReview.groundingInPaperEvidence(), modelReview.specificity(),
                modelReview.positiveTone(), modelReview.xiaohongshuReadability(), modelReview.notAbstractLike(),
                modelReview.structureCompleteness(), modelReview.noHallucination(),
                qualityValidator.containsTestPlaceholder(engaging) ? 1 : modelReview.noPlaceholderText(),
                containsEllipsis(engaging) ? 1 : modelReview.noEllipses(),
                Math.min(modelReview.overallScore(), 3), problems,
                modelReview.suggestedImprovements(), true
        );
    }

    private VariantSet rewrite(
            SocialCopyEvidenceDigest digest,
            SocialCopyAngle angle,
            SocialCopySelectedHook selected,
            VariantSet draft,
            CopyQualityReview review,
            String language
    ) {
        return variantsFrom(request(
                "social_copy_polished_rewrite",
                variantsSchema(),
                """
                        Stage 7 - FinalPolishedRewrite.
                        Rewrite the three %s variants once, following the quality review. Strengthen the grounded
                        hook, make the actual system/method and usage scenario more concrete, improve the positive
                        Xiaohongshu/WeChat readability, and keep the target-reader section and topic hashtags clear.
                        Preserve truthfulness. Do not add unsupported detail merely to sound attractive. Do not use
                        placeholders, ellipses, fake metrics, abstract-style openings, or incomplete fragments.
                        Chinese paraphrases are valid and need not repeat exact English system tokens.

                        Evidence digest: %s
                        SocialCopyAngle: %s
                        Selected hook: %s
                        Draft variants: %s
                        Quality review: %s
                        """.formatted(requestedLanguage(language), json(digest), json(angle), json(selected),
                        json(draft), json(review))
        ), language);
    }

    private VariantSet repair(
            SocialCopyEvidenceDigest digest,
            SocialCopyAngle angle,
            SocialCopySelectedHook selected,
            VariantSet current,
            SocialCopyFinalValidation validation,
            String language
    ) {
        return variantsFrom(request(
                "social_copy_targeted_repair",
                variantsSchema(),
                """
                        Stage 8 - Targeted final-validation repair.
                        Repair the three %s variants once. Fix every listed validation issue while preserving all
                        grounded facts, the selected problem-first angle, and the required structure. Do not add
                        unsupported claims. Do not use placeholders or ellipses. Keep light emojis and grounded
                        hashtags. A natural Chinese paraphrase is acceptable.

                        Evidence digest: %s
                        SocialCopyAngle: %s
                        Selected hook: %s
                        Current variants: %s
                        Validation issues: %s
                        """.formatted(requestedLanguage(language), json(digest), json(angle), json(selected),
                        json(current), json(validation.issues()))
        ), language);
    }

    private VariantSet variantsFrom(JsonNode root, String language) {
        LinkedHashMap<String, String> variants = new LinkedHashMap<>();
        List<String> tags = hashtags(root.path("hashtags"), List.of());
        if (tags.size() < 3) {
            throw new AiCoverWorkflowException("OpenAI did not return enough paper-grounded hashtags.");
        }
        for (String tone : TONES) {
            String value = ensureHashtags(clean(root.path(tone).asText(null)), tags);
            if (!basicValidCopy(value, language)) {
                throw new AiCoverWorkflowException("OpenAI returned weak or placeholder-like social copy.");
            }
            variants.put(tone, value);
        }
        return new VariantSet(Map.copyOf(variants), tags);
    }

    private VariantSet compactVariants(
            JsonNode root,
            List<String> tags,
            String language,
            String finalCopy,
            SocialCopyStructuredJsonClient client
    ) {
        LinkedHashMap<String, String> variants = new LinkedHashMap<>();
        for (String tone : TONES) {
            String value = ensureHashtags(clean(root.path(tone).asText(null)), tags);
            if (!basicValidCopy(value, language)) {
                value = finalCopy;
            }
            if (!basicValidCopy(value, language)) {
                throw providerFailure(client, SocialCopyFailureReason.INVALID_RESPONSE,
                        "The compact response contained an unusable " + tone + " variant.", null);
            }
            variants.put(tone, value);
        }
        return new VariantSet(Map.copyOf(variants), tags);
    }

    private List<HookCandidate> hooksFrom(JsonNode root, SocialCopyEvidenceDigest digest) {
        List<HookCandidate> hooks = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode node : root) {
                HookCandidate candidate = new HookCandidate(
                        clean(node.path("hookText").asText(null)),
                        normalizeHookType(node.path("hookType").asText(null)),
                        clean(node.path("whyItWorks").asText(null)),
                        clean(node.path("groundingEvidence").asText(null))
                );
                if (validHook(candidate)) {
                    hooks.add(candidate);
                }
            }
        }
        if (hooks.size() < 3) {
            hooks = fallbackHooks(digest);
        }
        return List.copyOf(hooks.subList(0, Math.min(hooks.size(), 5)));
    }

    private SocialCopySelectedHook selectedHookFrom(
            JsonNode root,
            List<HookCandidate> hooks
    ) {
        List<HookScore> scores = new ArrayList<>();
        for (JsonNode node : root.path("evaluations")) {
            int index = node.path("candidateIndex").asInt(-1);
            if (index < 0 || index >= hooks.size()) {
                continue;
            }
            int relatability = score(node, "relatability");
            int specificity = score(node, "specificity");
            int attractiveness = score(node, "attractiveness");
            int grounding = score(node, "groundingInEvidence");
            int fit = score(node, "fitForXiaohongshu");
            int notAbstract = score(node, "notAbstractLike");
            double average = (relatability + specificity + attractiveness + grounding + fit + notAbstract) / 6.0;
            scores.add(new HookScore(
                    index, relatability, specificity, attractiveness, grounding, fit, notAbstract,
                    Math.round(average * 100.0) / 100.0, clean(node.path("reason").asText(null))
            ));
        }
        if (scores.size() != hooks.size()) {
            scores = defaultScores(hooks);
        }
        int selectedIndex = root.path("selectedCandidateIndex").asInt(-1);
        if (selectedIndex < 0 || selectedIndex >= hooks.size()) {
            selectedIndex = 0;
        }
        return new SocialCopySelectedHook(
                hooks.get(selectedIndex), selectedIndex, scores, false,
                fallback(root, "rationale", "Selected from the scored evidence-grounded candidates.")
        );
    }

    private List<HookCandidate> fallbackHooks(SocialCopyEvidenceDigest digest) {
        return List.of(
                new HookCandidate("论文回应的真实问题是：" + digest.problemEvidence(), "question",
                        "来自已提取的论文问题证据。", digest.problemEvidence()),
                new HookCandidate("回到使用场景，这项研究如何落地：" + digest.usageEvidence(), "scenario",
                        "来自已提取的论文使用证据。", digest.usageEvidence()),
                new HookCandidate("值得关注的不只是概念，而是方法如何回应问题：" + digest.proposedSystemEvidence(),
                        "contrast", "来自已提取的论文方法证据。", digest.proposedSystemEvidence())
        );
    }

    private List<HookScore> defaultScores(List<HookCandidate> hooks) {
        List<HookScore> scores = new ArrayList<>();
        for (int index = 0; index < hooks.size(); index++) {
            scores.add(new HookScore(index, 4, 4, 4, 4, 4, 4, 4.0,
                    "Recovered from extracted paper evidence."));
        }
        return List.copyOf(scores);
    }

    private CopyQualityReview reviewFrom(JsonNode root) {
        return new CopyQualityReview(
                score(root, "hookAttractiveness"), score(root, "groundingInPaperEvidence"),
                score(root, "specificity"), score(root, "positiveTone"),
                score(root, "xiaohongshuReadability"), score(root, "notAbstractLike"),
                score(root, "structureCompleteness"), score(root, "noHallucination"),
                score(root, "noPlaceholderText"), score(root, "noEllipses"),
                score(root, "overallScore"), strings(root.path("problems"), 12),
                strings(root.path("suggestedImprovements"), 12), root.path("shouldRewrite").asBoolean(true)
        );
    }

    private JsonNode request(String schemaName, Map<String, Object> schema, String taskPrompt) {
        String response = openAiClient.requestJson(
                EDITOR_SYSTEM_PROMPT + "\n\n" + taskPrompt,
                schemaName,
                schema,
                List.of()
        );
        try {
            return objectMapper.readTree(response);
        } catch (Exception ex) {
            throw new AiCoverWorkflowException("OpenAI " + schemaName + " JSON could not be parsed.", ex);
        }
    }

    private Map<String, Object> angleSchema() {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        for (String name : List.of("painPoint", "openingHookIntent", "proposedSystem", "usageScenario",
                "howItWorks", "mostInterestingPoint", "whyValuable")) {
            fields.put(name, stringSchema());
        }
        fields.put("targetReaders", stringArraySchema(1, 6));
        fields.put("topicAreas", stringArraySchema(1, 8));
        fields.put("hashtags", stringArraySchema(3, 6));
        fields.put("confidenceLevel", enumSchema(List.of("HIGH", "MEDIUM", "LOW")));
        fields.put("warnings", stringArraySchema(0, 8));
        return objectSchema(fields);
    }

    private Map<String, Object> hooksSchema() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("candidates", arraySchema(hookCandidateSchema(), 3, 5));
        return objectSchema(root);
    }

    private Map<String, Object> hookCandidateSchema() {
        LinkedHashMap<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("hookText", stringSchema());
        candidate.put("hookType", enumSchema(List.of("pain_point", "scenario", "question", "contrast", "curiosity")));
        candidate.put("whyItWorks", stringSchema());
        candidate.put("groundingEvidence", stringSchema());
        return objectSchema(candidate);
    }

    private Map<String, Object> hookSelectionSchema() {
        LinkedHashMap<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("candidateIndex", integerSchema(0, 4));
        for (String name : List.of("relatability", "specificity", "attractiveness", "groundingInEvidence",
                "fitForXiaohongshu", "notAbstractLike")) {
            evaluation.put(name, integerSchema(1, 5));
        }
        evaluation.put("reason", stringSchema());
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("evaluations", arraySchema(objectSchema(evaluation), 3, 5));
        root.put("selectedCandidateIndex", integerSchema(0, 4));
        root.put("rationale", stringSchema());
        return objectSchema(root);
    }

    private Map<String, Object> variantsSchema() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        TONES.forEach(tone -> root.put(tone, stringSchema()));
        root.put("hashtags", stringArraySchema(3, 6));
        return objectSchema(root);
    }

    private Map<String, Object> reviewSchema() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        for (String name : List.of("hookAttractiveness", "groundingInPaperEvidence", "specificity", "positiveTone",
                "xiaohongshuReadability", "notAbstractLike", "structureCompleteness", "noHallucination",
                "noPlaceholderText", "noEllipses", "overallScore")) {
            root.put(name, integerSchema(1, 5));
        }
        root.put("problems", stringArraySchema(0, 12));
        root.put("suggestedImprovements", stringArraySchema(0, 12));
        root.put("shouldRewrite", Map.of("type", "boolean"));
        return objectSchema(root);
    }

    private Map<String, Object> compactSchema() {
        LinkedHashMap<String, Object> variants = new LinkedHashMap<>();
        TONES.forEach(tone -> variants.put(tone, stringSchema()));

        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("angle", angleSchema());
        root.put("hookCandidates", arraySchema(hookCandidateSchema(), 3, 5));
        root.put("selectedHook", hookSelectionSchema());
        root.put("variants", objectSchema(variants));
        root.put("qualityReview", reviewSchema());
        root.put("finalCopy", stringSchema());
        root.put("hashtags", stringArraySchema(3, 6));
        root.put("warnings", stringArraySchema(0, 8));
        return objectSchema(root);
    }

    private Map<String, Object> objectSchema(LinkedHashMap<String, Object> properties) {
        LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> enumSchema(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private Map<String, Object> integerSchema(int minimum, int maximum) {
        return Map.of("type", "integer", "minimum", minimum, "maximum", maximum);
    }

    private Map<String, Object> stringArraySchema(int minimum, int maximum) {
        return arraySchema(stringSchema(), minimum, maximum);
    }

    private Map<String, Object> arraySchema(Map<String, Object> items, int minimum, int maximum) {
        return Map.of("type", "array", "items", items, "minItems", minimum, "maxItems", maximum);
    }

    private boolean weakHookScore(HookScore score) {
        return score.averageScore() < 4.0
                || List.of(score.relatability(), score.specificity(), score.attractiveness(),
                score.groundingInEvidence(), score.fitForXiaohongshu(), score.notAbstractLike())
                .stream().anyMatch(value -> value < 4);
    }

    private boolean selectedHookIsWeak(SocialCopySelectedHook selected) {
        int selectedIndex = selected.selectedCandidateIndex();
        return selected.scores().stream().filter(score -> score.candidateIndex() == selectedIndex)
                .findFirst().map(this::weakHookScore).orElse(true);
    }

    private boolean validHook(HookCandidate value) {
        return value.hookText() != null && value.hookText().length() >= 16
                && value.whyItWorks() != null && value.groundingEvidence() != null
                && !containsEllipsis(value.hookText())
                && !qualityValidator.containsTestPlaceholder(value.hookText());
    }

    private boolean basicValidCopy(String value, String language) {
        if (value == null || value.length() < 80) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return !normalized.contains("lorem ipsum")
                && ("zh".equalsIgnoreCase(language) ? containsChinese(value) : !mostlyChinese(value));
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints()
                .anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN);
    }

    private boolean mostlyChinese(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        long han = value.codePoints()
                .filter(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN)
                .count();
        long letters = value.codePoints().filter(Character::isLetter).count();
        return han > 12 && han * 5 > Math.max(1, letters);
    }

    private List<String> minimumHashtags(List<String> tags, String language) {
        LinkedHashSet<String> values = new LinkedHashSet<>(tags == null ? List.of() : tags);
        if ("en".equalsIgnoreCase(language)) {
            values.add("PaperReview");
            values.add("ResearchCommunication");
            values.add("AcademicResearch");
        } else {
            values.add("论文分享");
            values.add("科研传播");
            values.add("学术交流");
        }
        return values.stream().limit(6).toList();
    }

    private String ensureHashtags(String value, List<String> tags) {
        if (value == null) {
            return null;
        }
        if (value.matches("(?s).*(?:^|\\s)#[\\p{L}\\p{N}_-]{2,}.*")) {
            return value;
        }
        return value + "\n\n" + tags.stream().map(tag -> "#" + tag)
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    private Map<String, String> apiVariants(Map<String, String> variants) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("engaging", variants.get("engaging"));
        result.put("concise", variants.get("concise"));
        result.put("professional", variants.get("professional"));
        result.put("academic", variants.get("professional"));
        return Map.copyOf(result);
    }

    private List<String> hashtags(JsonNode node, List<String> fallback) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = clean(item.asText(null));
                if (value == null) {
                    continue;
                }
                value = value.replaceFirst("^#+", "").replaceAll("[^\\p{L}\\p{N}_-]", "").trim();
                if (value.length() >= 2 && value.length() <= 36 && !qualityValidator.isPlaceholder(value)) {
                    values.add(value);
                }
            }
        }
        if (fallback != null) {
            fallback.stream().map(this::clean).filter(value -> value != null)
                    .map(value -> value.replaceAll("[^\\p{L}\\p{N}_-]", ""))
                    .filter(value -> value.length() >= 2 && value.length() <= 36)
                    .forEach(values::add);
        }
        return values.stream().limit(6).toList();
    }

    private List<String> strings(JsonNode node, int limit) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = clean(item.asText(null));
            if (value != null) {
                values.add(value);
            }
            if (values.size() >= limit) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private List<String> fallbackList(JsonNode node, String fallback) {
        List<String> values = strings(node, 8);
        return values.isEmpty() ? List.of(fallback) : values;
    }

    private int score(JsonNode node, String name) {
        return Math.max(1, Math.min(5, node.path(name).asInt(1)));
    }

    private String fallback(JsonNode node, String name, String fallback) {
        String value = clean(node.path(name).asText(null));
        return value == null || qualityValidator.isPlaceholder(value) ? fallback : value;
    }

    private String normalizeHookType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return List.of("pain_point", "scenario", "question", "contrast", "curiosity").contains(normalized)
                ? normalized : "scenario";
    }

    private String normalizeConfidence(String value) {
        String normalized = value == null ? "LOW" : value.trim().toUpperCase(Locale.ROOT);
        return List.of("HIGH", "MEDIUM", "LOW").contains(normalized) ? normalized : "LOW";
    }

    private String requestedLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "English" : "natural UTF-8 Simplified Chinese";
    }

    private boolean containsEllipsis(String value) {
        return value != null && (value.contains("…") || value.matches("(?s).*\\.{3,}.*"));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AiCoverWorkflowException("Social-copy stage input could not be serialized.", ex);
        }
    }

    private SocialCopyProviderException providerFailure(
            SocialCopyStructuredJsonClient client,
            SocialCopyFailureReason reason,
            String message,
            Throwable cause
    ) {
        String provider = client == null ? "UNKNOWN" : client.providerName();
        String safe = AiCoverDiagnostics.sanitize(message);
        return cause == null
                ? new SocialCopyProviderException(provider, reason, null, null, safe)
                : new SocialCopyProviderException(provider, reason, null, null, safe, cause);
    }

    public record GeneratedSocialCopy(
            Map<String, String> variants,
            List<String> hashtags,
            SocialCopyPipelineArtifacts artifacts
    ) {
        public GeneratedSocialCopy {
            variants = variants == null ? Map.of() : Map.copyOf(variants);
            hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
        }

        public GeneratedSocialCopy(Map<String, String> variants, List<String> hashtags) {
            this(variants, hashtags, null);
        }
    }

    private record VariantSet(Map<String, String> variants, List<String> hashtags) {
    }

}
