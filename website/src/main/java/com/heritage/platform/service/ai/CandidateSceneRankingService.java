package com.heritage.platform.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.ApplicationSceneBrief;
import com.heritage.platform.dto.ai.CandidateRankingResult;
import com.heritage.platform.dto.ai.CandidateSceneScore;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.ScenarioImagePrompt;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class CandidateSceneRankingService {

    private static final Logger logger = LoggerFactory.getLogger(CandidateSceneRankingService.class);
    static final String RANKING_FALLBACK_WARNING =
            "Candidate ranking was unavailable, so images are shown in generation order.";

    @Value("${ai-cover.candidate-ranking-enabled:false}")
    private boolean enabled;

    @Value("${ai-cover.ranking-mode:disabled}")
    private String rankingMode;

    @Value("${ai-cover.show-ranking-details:true}")
    private boolean showRankingDetails;

    @Value("${comfyui.return-candidates:3}")
    private int returnCandidates;

    private final ObjectMapper objectMapper;
    private final OpenAiStructuredResponseClient openAiClient;
    private final UploadPathService uploadPathService;

    public CandidateSceneRankingService(
            ObjectMapper objectMapper,
            OpenAiStructuredResponseClient openAiClient,
            UploadPathService uploadPathService
    ) {
        this.objectMapper = objectMapper;
        this.openAiClient = openAiClient;
        this.uploadPathService = uploadPathService;
    }

    public CandidateRankingResult rank(
            Long publicationId,
            ApplicationSceneBrief sceneBrief,
            FinalPaperUnderstanding understanding,
            ScenarioImagePrompt scenarioPrompt,
            List<AiCoverCandidate> generatedCandidates
    ) {
        if (!rankingEnabled()) {
            logger.info("Candidate ranking mode: disabled; publicationId={}, selectionMode=manual", publicationId);
            return manualSelection(generatedCandidates);
        }
        if (sceneBrief == null || generatedCandidates == null || generatedCandidates.isEmpty()) {
            String warning = "Candidate ranking could not run because no application scene or candidates were available. Images are shown in generation order.";
            logger.warn("Candidate scene ranking skipped: publicationId={}, reason={}", publicationId, warning);
            return fallback(generatedCandidates, warning);
        }

        try {
            List<OpenAiStructuredResponseClient.VisionInput> images = new ArrayList<>();
            for (AiCoverCandidate candidate : generatedCandidates) {
                Path path = uploadPathService.resolveSelectedGeneratedCover(publicationId, candidate.imageUrl());
                images.add(new OpenAiStructuredResponseClient.VisionInput(
                        "Candidate ID: " + candidate.candidateId(),
                        path
                ));
            }
            String response = openAiClient.requestJson(
                    buildPrompt(sceneBrief, understanding, scenarioPrompt),
                    "candidate_scene_ranking",
                    responseSchema(),
                    images
            );
            Map<String, CandidateSceneScore> scores = parseScores(response);
            String defaultReason = scenarioPrompt == null
                    ? "Highest paper-scene relevance score."
                    : scenarioPrompt.shortCandidateExplanation();
            CandidateRankingResult result = rankWithScores(generatedCandidates, scores, defaultReason);
            logger.info("Candidate scene ranking succeeded: publicationId={}, scoredCandidateCount={}", publicationId, scores.size());
            return result;
        } catch (Exception ex) {
            String diagnostic = AiCoverDiagnostics.safeExceptionSummary(ex);
            String warning = "Candidate ranking was unavailable: " + diagnostic
                    + ". Images are shown in generation order.";
            logger.warn(
                    "Candidate scene ranking failed: publicationId={}, failure={}",
                    publicationId,
                    diagnostic,
                    ex
            );
            return fallback(generatedCandidates, warning);
        }
    }

    CandidateRankingResult rankWithScores(
            List<AiCoverCandidate> candidates,
            Map<String, CandidateSceneScore> scores,
            String defaultReason
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new CandidateRankingResult(List.of(), true, List.of());
        }
        for (AiCoverCandidate candidate : candidates) {
            if (candidate == null || !scores.containsKey(candidate.candidateId())) {
                throw new AiCoverWorkflowException("Candidate ranking response was incomplete.");
            }
        }

        List<AiCoverCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparingInt(
                        candidate -> -safeTotal(scores.get(candidate.candidateId()))
                ))
                .limit(effectiveReturnCandidateCount())
                .toList();
        List<AiCoverCandidate> ranked = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            AiCoverCandidate candidate = sorted.get(index);
            CandidateSceneScore score = scores.get(candidate.candidateId());
            String reason = score.reason() == null || score.reason().isBlank()
                    ? defaultReason
                    : score.reason();
            ranked.add(candidate.withRanking(
                    index == 0,
                    showRankingDetails ? score : null,
                    reason,
                    candidate.warnings()
            ));
        }
        return new CandidateRankingResult(ranked, true, List.of());
    }

    CandidateRankingResult fallback(List<AiCoverCandidate> candidates, String warning) {
        List<AiCoverCandidate> source = candidates == null ? List.of() : candidates;
        List<AiCoverCandidate> returned = new ArrayList<>();
        for (int index = 0; index < Math.min(effectiveReturnCandidateCount(), source.size()); index++) {
            AiCoverCandidate candidate = source.get(index);
            List<String> candidateWarnings = new ArrayList<>(candidate.warnings());
            if (warning != null && !warning.isBlank()) {
                candidateWarnings.add(warning);
            }
            returned.add(candidate.withRanking(
                    false,
                    null,
                    "Automatic scene ranking was unavailable; this candidate is shown in generation order.",
                    distinct(candidateWarnings)
            ));
        }
        return new CandidateRankingResult(
                returned,
                false,
                warning == null || warning.isBlank() ? List.of() : List.of(warning)
        );
    }

    CandidateRankingResult manualSelection(List<AiCoverCandidate> candidates) {
        List<AiCoverCandidate> source = candidates == null ? List.of() : candidates;
        boolean locallyScored = !source.isEmpty() && source.stream().allMatch(candidate -> candidate.sceneScore() != null);
        if (locallyScored) {
            List<AiCoverCandidate> sorted = source.stream()
                    .sorted(Comparator.comparingInt(candidate -> -candidate.sceneScore().totalScore()))
                    .limit(effectiveReturnCandidateCount())
                    .toList();
            List<AiCoverCandidate> ranked = new ArrayList<>();
            for (int index = 0; index < sorted.size(); index++) {
                AiCoverCandidate candidate = sorted.get(index);
                ranked.add(candidate.withRanking(
                        index == 0,
                        candidate.sceneScore(),
                        candidate.recommendationReason(),
                        candidate.warnings()
                ));
            }
            return new CandidateRankingResult(ranked, true, List.of());
        }
        List<AiCoverCandidate> returned = new ArrayList<>();
        for (int index = 0; index < Math.min(effectiveReturnCandidateCount(), source.size()); index++) {
            AiCoverCandidate candidate = source.get(index);
            String reason = candidate.recommendationReason();
            if (reason == null || reason.isBlank()) {
                reason = "Paper-grounded candidate " + (index + 1) + " shown for manual cover selection.";
            }
            returned.add(candidate.withRanking(false, null, reason, candidate.warnings()));
        }
        return new CandidateRankingResult(returned, false, List.of());
    }

    Map<String, CandidateSceneScore> parseScores(String json) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode rankings = root.path("rankings");
        if (!rankings.isArray()) {
            throw new AiCoverWorkflowException("Candidate ranking response was invalid.");
        }
        Map<String, CandidateSceneScore> scores = new LinkedHashMap<>();
        for (JsonNode item : rankings) {
            String candidateId = text(item, "candidateId");
            if (candidateId == null) {
                continue;
            }
            int sceneRealism = score(item, "sceneRealismScore");
            int taskAccuracy = score(item, "taskAccuracyScore");
            int technologyRelevance = score(item, "technologyRelevanceScore");
            int environmentAccuracy = score(item, "environmentAccuracyScore");
            int visualClarity = score(item, "visualClarityScore");
            int coverSuitability = score(item, "coverSuitabilityScore");
            int nonGeneric = score(item, "nonGenericScore");
            int total = sceneRealism + taskAccuracy + technologyRelevance + environmentAccuracy
                    + visualClarity + coverSuitability + nonGeneric;
            scores.put(candidateId, new CandidateSceneScore(
                    sceneRealism,
                    taskAccuracy,
                    technologyRelevance,
                    environmentAccuracy,
                    visualClarity,
                    coverSuitability,
                    nonGeneric,
                    total,
                    strings(item, "problems"),
                    text(item, "reason")
            ));
        }
        return scores;
    }

    int effectiveReturnCandidateCount() {
        return returnCandidates <= 0 ? 1 : returnCandidates;
    }

    private boolean rankingEnabled() {
        return enabled && rankingMode != null && !"disabled".equalsIgnoreCase(rankingMode.trim());
    }

    private String buildPrompt(
            ApplicationSceneBrief sceneBrief,
            FinalPaperUnderstanding understanding,
            ScenarioImagePrompt scenarioPrompt
    ) throws JsonProcessingException {
        return """
                Evaluate every candidate image against the application scene brief below. Each image follows a text label containing its Candidate ID.

                Decide whether the candidate accurately represents the proposed technology being used in its intended real-world task and environment. The strongest image should let a non-expert understand at a glance what the contribution is, who uses it, what they do with it, and what response or benefit it produces. Use the scene brief as ground truth.

                Score each field from 0 to 10:
                - sceneRealismScore: believable, realistic scene and posture.
                - taskAccuracyScore: correct or plausibly correct task.
                - technologyRelevanceScore: proposed technology, product, or system is shown or clearly implied.
                - environmentAccuracyScore: environment matches the paper domain.
                - visualClarityScore: the user, contribution, action, real setting, and visible response form one immediately understandable visual story.
                - coverSuitabilityScore: attractive and usable as a publication cover.
                - nonGenericScore: avoids generic AI or technology wallpaper.

                Beautiful but paper-irrelevant images must receive low task and technology scores. Wrong users, tasks, devices, or environments must receive low accuracy scores. Generic decorative images must receive low nonGenericScore. Return one ranking object for every supplied candidate and only valid JSON.

                Application scene brief:
                """ + objectMapper.writeValueAsString(sceneBrief)
                + "\n\nPaper understanding:\n" + objectMapper.writeValueAsString(understanding)
                + "\n\nGeneration strategy:\n" + objectMapper.writeValueAsString(scenarioPrompt);
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> scoreProperties = new LinkedHashMap<>();
        scoreProperties.put("candidateId", Map.of("type", "string"));
        for (String field : List.of(
                "sceneRealismScore", "taskAccuracyScore", "technologyRelevanceScore",
                "environmentAccuracyScore", "visualClarityScore", "coverSuitabilityScore", "nonGenericScore"
        )) {
            scoreProperties.put(field, Map.of("type", "integer", "minimum", 0, "maximum", 10));
        }
        scoreProperties.put("problems", Map.of("type", "array", "items", Map.of("type", "string")));
        scoreProperties.put("reason", Map.of("type", "string"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("required", List.copyOf(scoreProperties.keySet()));
        itemSchema.put("properties", scoreProperties);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("rankings"));
        schema.put("properties", Map.of("rankings", Map.of("type", "array", "items", itemSchema)));
        return schema;
    }

    private int score(JsonNode root, String field) {
        return Math.max(0, Math.min(10, root.path(field).asInt(0)));
    }

    private int safeTotal(CandidateSceneScore score) {
        return score == null || score.totalScore() == null ? 0 : score.totalScore();
    }

    private String text(JsonNode root, String field) {
        if (!root.path(field).isTextual()) {
            return null;
        }
        String value = root.path(field).asText().replaceAll("\\s+", " ").trim();
        return value.isEmpty() ? null : value;
    }

    private List<String> strings(JsonNode root, String field) {
        if (!root.path(field).isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : root.path(field)) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().replaceAll("\\s+", " ").trim());
            }
        }
        return distinct(values);
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values == null ? List.of() : values));
    }
}
