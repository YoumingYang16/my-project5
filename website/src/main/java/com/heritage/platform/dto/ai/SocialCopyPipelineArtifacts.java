package com.heritage.platform.dto.ai;

import java.util.List;
import java.util.Map;

public record SocialCopyPipelineArtifacts(
        SocialCopyEvidenceDigest evidenceDigest,
        SocialCopyAngle angle,
        List<HookCandidate> hookCandidates,
        SocialCopySelectedHook selectedHook,
        Map<String, String> draftVariants,
        CopyQualityReview qualityReview,
        SocialCopyFinalValidation finalValidation,
        Map<String, String> finalVariants,
        List<String> finalHashtags,
        boolean rewritten,
        boolean repaired
) {
    public SocialCopyPipelineArtifacts {
        hookCandidates = hookCandidates == null ? List.of() : List.copyOf(hookCandidates);
        draftVariants = draftVariants == null ? Map.of() : Map.copyOf(draftVariants);
        finalVariants = finalVariants == null ? Map.of() : Map.copyOf(finalVariants);
        finalHashtags = finalHashtags == null ? List.of() : List.copyOf(finalHashtags);
    }
}
