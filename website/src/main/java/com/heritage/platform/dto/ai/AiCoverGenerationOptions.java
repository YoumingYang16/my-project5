package com.heritage.platform.dto.ai;

public record AiCoverGenerationOptions(
        String audience,
        String communicationGoal,
        String imageStyle,
        String sourcePolicy,
        String referenceMode,
        String imageProviderMode,
        String outputAspectRatio
) {
    public static AiCoverGenerationOptions defaults() {
        return new AiCoverGenerationOptions("public", "show-application", "auto", "balanced", "auto", "dual", "website-3:2");
    }

    public String normalizedAudience() {
        return allowed(audience, "public", "student", "practitioner", "researcher");
    }

    public String normalizedCommunicationGoal() {
        return allowed(communicationGoal, "show-application", "attract-attention", "science-explanation", "spark-discussion");
    }

    public String normalizedImageStyle() {
        return allowed(imageStyle, "auto", "documentary", "artifact-closeup", "study-context", "interaction-scene", "editorial-concept", "method-summary");
    }

    public String normalizedSourcePolicy() {
        return allowed(sourcePolicy, "balanced", "source-first", "ai-only");
    }

    public String normalizedReferenceMode() {
        return allowed(referenceMode, "auto", "off");
    }

    public String normalizedImageProviderMode() {
        return allowed(imageProviderMode, "dual", "qwen", "comfyui", "automatic");
    }

    public String normalizedOutputAspectRatio() {
        return allowed(outputAspectRatio, "website-3:2", "website-3:2", "xiaohongshu-3:4", "square-1:1", "wide-16:9");
    }

    private static String allowed(String value, String fallback, String... accepted) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        for (String item : accepted) {
            if (item.equals(normalized)) return normalized;
        }
        return fallback;
    }
}
