package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.CandidateRankingResult;
import com.heritage.platform.dto.ai.CandidateSceneScore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RendererCandidateRankingCompatibilityTests {

    @Test
    void keepsRendererScoresAndRecommendsHighestCandidateWhenRemoteRankingIsDisabled() {
        OpenAiStructuredResponseClient openAiClient = mock(OpenAiStructuredResponseClient.class);
        CandidateSceneRankingService service = new CandidateSceneRankingService(
                new ObjectMapper(),
                openAiClient,
                mock(UploadPathService.class)
        );
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "returnCandidates", 3);

        List<AiCoverCandidate> candidates = List.of(
                candidate("source", 68),
                candidate("qwen", 91),
                candidate("comfy", 80)
        );

        CandidateRankingResult result = service.rank(42L, null, null, null, candidates);

        assertThat(result.rankingAvailable()).isTrue();
        assertThat(result.candidates()).extracting(AiCoverCandidate::candidateId)
                .containsExactly("qwen", "comfy", "source");
        assertThat(result.candidates()).filteredOn(AiCoverCandidate::recommended)
                .extracting(AiCoverCandidate::candidateId)
                .containsExactly("qwen");
        assertThat(result.candidates().get(0).sceneScore().totalScore()).isEqualTo(91);
        verifyNoInteractions(openAiClient);
    }

    private AiCoverCandidate candidate(String id, int totalScore) {
        CandidateSceneScore score = new CandidateSceneScore(
                10, 10, 10, 10, 10, 10, 10,
                totalScore,
                List.of(),
                "Renderer API score"
        );
        return new AiCoverCandidate(
                id,
                "/uploads/generated-covers/42/" + id + ".jpg",
                42L,
                false,
                "paper-grounded prompt",
                score,
                "Renderer recommendation",
                List.of()
        );
    }
}
