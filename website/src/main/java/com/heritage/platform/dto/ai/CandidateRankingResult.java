package com.heritage.platform.dto.ai;

import java.util.List;

public record CandidateRankingResult(
        List<AiCoverCandidate> candidates,
        boolean rankingAvailable,
        List<String> warnings
) {
}
