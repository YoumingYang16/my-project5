package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.AiCoverCandidate;

import java.util.List;

public interface PublicationImageProvider {

    String providerName();

    boolean enabled();

    List<AiCoverCandidate> generate(PublicationImageGenerationRequest request);
}
