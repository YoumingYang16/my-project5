package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;

public interface PaperUnderstandingProvider {

    String providerName();

    String modelName();

    boolean isEnabled();

    boolean isAvailable();

    OpenAiPaperUnderstanding understand(PaperEvidencePacket evidencePacket);
}
