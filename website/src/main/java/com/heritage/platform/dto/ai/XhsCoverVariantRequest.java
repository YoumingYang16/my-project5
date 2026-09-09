package com.heritage.platform.dto.ai;

public record XhsCoverVariantRequest(String imageUrl, String headline, String textColor,
                                     String backgroundColor, Boolean showTitle, String templatePreset, String backgroundMode) {
}
