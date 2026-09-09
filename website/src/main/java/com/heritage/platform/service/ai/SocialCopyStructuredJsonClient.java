package com.heritage.platform.service.ai;

import java.util.Map;

public interface SocialCopyStructuredJsonClient {

    String providerName();

    boolean isEnabled();

    String requestJson(
            String systemPrompt,
            String userPrompt,
            String schemaName,
            Map<String, Object> schema
    );
}
