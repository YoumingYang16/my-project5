package com.heritage.platform.dto.ai;

import java.util.List;

public record GrobidPaperDocument(
        GrobidMetadata metadata,
        String teiXml,
        List<String> warnings
) {
}
