package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.SocialCopyContentPlan;
import com.heritage.platform.dto.ai.SocialCopyContentSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeepSeekSocialCopyServiceTests {

    @Test
    void outreachPromptCarriesTheSelectedAudienceAndCommunicationGoal() {
        OpenAiSocialCopyService pipeline = mock(OpenAiSocialCopyService.class);
        DeepSeekStructuredResponseClient client = mock(DeepSeekStructuredResponseClient.class);
        DeepSeekSocialCopyService service = new DeepSeekSocialCopyService(pipeline, client);
        SocialCopyContentSpec spec = new SocialCopyContentSpec(
                "practitioner", "collaboration", "zh", "engaging", "standard", "欢迎交流",
                SocialCopyContentSpec.XIAOHONGSHU, "outreach"
        );
        SocialCopyContentPlan plan = new SocialCopyContentPlan(
                "强调可落地的使用场景、工作流程和适用边界",
                "说明适合哪些研究者或实践者关注，并邀请基于论文边界进行交流",
                List.of(), List.of(), "欢迎交流"
        );

        when(pipeline.generateCompact(any(), any(), eq("zh"), eq(client), any())).thenReturn(null);

        service.generate(null, null, "zh", spec, plan);

        verify(pipeline).generateCompact(
                any(), any(), eq("zh"), eq(client),
                argThat(prompt -> prompt.contains("industry practitioners and decision-makers")
                        && prompt.contains("invite relevant, evidence-bounded collaboration")
                        && prompt.contains("欢迎交流"))
        );
    }

    @Test
    void personalEditionStillCarriesTheSelectedAudienceAndCommunicationGoal() {
        OpenAiSocialCopyService pipeline = mock(OpenAiSocialCopyService.class);
        DeepSeekStructuredResponseClient client = mock(DeepSeekStructuredResponseClient.class);
        DeepSeekSocialCopyService service = new DeepSeekSocialCopyService(pipeline, client);
        SocialCopyContentSpec spec = new SocialCopyContentSpec(
                "student", "read_more", "zh", "engaging", "short", "查看原文",
                SocialCopyContentSpec.XIAOHONGSHU, "personal"
        );
        SocialCopyContentPlan plan = new SocialCopyContentPlan(
                "用通俗语言解释必要术语，并先说明研究问题为何与学习或实践有关",
                "用一个证据支持的发现制造阅读兴趣，CTA 引导查看原论文",
                List.of(), List.of(), "查看原文"
        );

        when(pipeline.generateCompact(any(), any(), eq("zh"), eq(client), any())).thenReturn(null);

        service.generate(null, null, "zh", spec, plan);

        verify(pipeline).generateCompact(
                any(), any(), eq("zh"), eq(client),
                argThat(prompt -> prompt.contains("students and early-career learners")
                        && prompt.contains("motivate readers to consult the original paper")
                        && prompt.contains("Follow this direction without weakening the required audience and goal adaptation."))
        );
    }
}
