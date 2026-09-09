package com.heritage.platform.dto.ai;

public record CoverImageVariantRequest(
        String imageUrl,
        Integer width,
        Integer height,
        String mode,
        Double focusX,
        Double focusY,
        Double cropX,
        Double cropY,
        Double cropWidth,
        Double cropHeight,
        String backgroundColor
) {}