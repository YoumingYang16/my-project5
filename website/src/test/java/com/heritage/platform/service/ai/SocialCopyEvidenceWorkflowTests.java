package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.EvidenceSpan;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.SocialCopyContentPlan;
import com.heritage.platform.dto.ai.SocialCopyContentSpec;
import com.heritage.platform.dto.ai.SocialCopyGenerationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocialCopyEvidenceWorkflowTests {

    @Test
    void normalizesToTheFixedXiaohongshuPlatformAndCreatesAnAudiencePlan() {
        SocialCopyContentSpecService service = new SocialCopyContentSpecService();
        SocialCopyContentSpec spec = service.normalize(
                new SocialCopyGenerationRequest("zh", "engaging", "student", "engagement", "short", "欢迎讨论"),
                "zh", "engaging"
        );
        var digest = new com.heritage.platform.dto.ai.SocialCopyEvidenceDigest(
                "Paper", "2026", "HCI", "A difficult task", "A proposed tool", "Users use the tool",
                "The paper explains a useful contribution", "readers", List.of(), List.of(), List.of(), "HIGH", List.of()
        );
        SocialCopyContentPlan plan = service.plan(spec, digest);

        assertThat(spec.platform()).isEqualTo("xiaohongshu");
        assertThat(plan.audienceGuidance()).contains("通俗");
        assertThat(plan.callToAction()).isEqualTo("欢迎讨论");
    }

    @Test
    void retrievesPageEvidenceAndFlagsUnsupportedStrongClaims() {
        EvidenceSpan pageOne = new EvidenceSpan("pdf-page-1", "PDF_TEXT", 1, "page",
                "The method reduced review time by 20 percent in the reported study.", 1d);
        EvidenceSpan pageTwo = new EvidenceSpan("pdf-page-2", "PDF_TEXT", 2, "page",
                "The tool supports reviewers in a workstation workflow.", 1d);
        PaperEvidencePacket packet = new PaperEvidencePacket("Paper", "", List.of(), 2026, "", "", List.of(),
                List.of("review time"), List.of("method"), List.of(), List.of("20 percent"), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "", 0, List.of(), null, null,
                List.of(), null, List.of(), List.of(pageOne, pageTwo));
        SocialCopyContentSpec spec = new SocialCopyContentSpec("practitioner", "visibility", "en", "engaging",
                "standard", null, "xiaohongshu");
        SocialCopyContentPlan plan = new SocialCopyContentPlan("review workflow", "review time", List.of("20 percent"),
                List.of(), "Read the paper");

        List<EvidenceSpan> retrieved = new EvidenceRetrievalService().retrieve(packet, spec, plan);
        var checks = new SocialCopyClaimVerifier().verify(
                "The method reduced review time by 20 percent. It is the best tool in the world.", retrieved
        );

        assertThat(retrieved).extracting(EvidenceSpan::id).contains("pdf-page-1");
        assertThat(checks).anyMatch(check -> "SUPPORTED".equals(check.verdict()));
        assertThat(checks).anyMatch(check -> "UNSUPPORTED".equals(check.verdict()));
    }
}
