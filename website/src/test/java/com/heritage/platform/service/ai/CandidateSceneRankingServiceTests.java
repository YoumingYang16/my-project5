package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.CandidateRankingResult;
import com.heritage.platform.dto.ai.CandidateSceneScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CandidateSceneRankingServiceTests {

    private CandidateSceneRankingService service;
    private OpenAiStructuredResponseClient openAiClient;

    @BeforeEach
    void setUp() {
        openAiClient = mock(OpenAiStructuredResponseClient.class);
        service = new CandidateSceneRankingService(
                new ObjectMapper(),
                openAiClient,
                mock(UploadPathService.class)
        );
        ReflectionTestUtils.setField(service, "returnCandidates", 3);
        ReflectionTestUtils.setField(service, "showRankingDetails", true);
    }

    @Test
    void sortsCandidatesByCalculatedSceneScoreAndReturnsTopThree() {
        List<AiCoverCandidate> candidates = candidates();
        Map<String, CandidateSceneScore> scores = new LinkedHashMap<>();
        scores.put("candidate-1", score(5, "Average scene"));
        scores.put("candidate-2", score(9, "Most realistic and relevant"));
        scores.put("candidate-3", score(8, "Strong runner-up"));
        scores.put("candidate-4", score(7, "Clear scene"));
        scores.put("candidate-5", score(4, "Generic"));
        scores.put("candidate-6", score(3, "Wrong environment"));

        CandidateRankingResult result = service.rankWithScores(candidates, scores, "Default reason");

        assertThat(result.candidates()).extracting(AiCoverCandidate::candidateId)
                .containsExactly("candidate-2", "candidate-3", "candidate-4");
        assertThat(result.candidates().get(0).recommended()).isTrue();
        assertThat(result.candidates().get(0).sceneScore().totalScore()).isEqualTo(63);
    }

    @Test
    void rankingFailureFallsBackToFirstThreeCandidates() {
        CandidateRankingResult result = service.fallback(
                candidates(),
                CandidateSceneRankingService.RANKING_FALLBACK_WARNING
        );

        assertThat(result.rankingAvailable()).isFalse();
        assertThat(result.candidates()).extracting(AiCoverCandidate::candidateId)
                .containsExactly("candidate-1", "candidate-2", "candidate-3");
        assertThat(result.candidates().get(0).recommended()).isFalse();
        assertThat(result.warnings()).contains(CandidateSceneRankingService.RANKING_FALLBACK_WARNING);
    }

    @Test
    void disabledRankingReturnsThreeCandidatesForManualSelectionWithoutWarnings() {
        ReflectionTestUtils.setField(service, "enabled", false);

        CandidateRankingResult result = service.rank(65L, null, null, null, candidates());

        assertThat(result.rankingAvailable()).isFalse();
        assertThat(result.candidates()).extracting(AiCoverCandidate::candidateId)
                .containsExactly("candidate-1", "candidate-2", "candidate-3");
        assertThat(result.candidates()).noneMatch(AiCoverCandidate::recommended);
        assertThat(result.warnings()).isEmpty();
        verifyNoInteractions(openAiClient);
    }

    private List<AiCoverCandidate> candidates() {
        return List.of(
                candidate(1), candidate(2), candidate(3), candidate(4), candidate(5), candidate(6)
        );
    }

    private AiCoverCandidate candidate(int index) {
        return new AiCoverCandidate(
                "candidate-" + index,
                "/uploads/generated-covers/1/candidate-" + index + ".png",
                (long) index,
                false,
                "prompt"
        );
    }

    private CandidateSceneScore score(int value, String reason) {
        return new CandidateSceneScore(
                value, value, value, value, value, value, value,
                value * 7, List.of(), reason
        );
    }
}
