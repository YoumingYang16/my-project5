package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.SocialCopyEvidenceDigest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocialCopyFinalValidatorTests {

    private final SocialCopyFinalValidator validator = new SocialCopyFinalValidator();

    @Test
    void acceptsGroundedChineseParaphraseWithoutExactEnglishSystemToken() {
        SocialCopyEvidenceDigest digest = new SocialCopyEvidenceDigest(
                "Sensor-guided Inspection Assistant", "Inspection Journal · 2026", "industrial inspection",
                "Manual inspection can be inconsistent.", "Sensor-guided Inspection Assistant",
                "Inspectors review visible findings at an inspection station.",
                "The design supports the review workflow.", "quality inspectors",
                List.of("inspection"), List.of("工业质检", "人机协作", "质量控制"),
                List.of("GROBID", "DOI", "PDF_TEXT"), "HIGH", List.of()
        );
        String copy = """
                📎【论文分享】

                🤔 当质检员连续查看复杂零件时，最让人担心的往往不是没有数据，而是重要线索会不会从重复检查中溜走。

                🧩 这篇论文介绍了一种由传感信息辅助的检测工具，把零散观测整理成质检员可以继续核对的可见提示。

                🛠️ 怎么用？质检员在检测工位查看界面给出的线索，再结合零件情况完成复核，让系统真正进入人的检查流程。

                ✅ 值得关注的是，它把传感、界面和人工判断连接在同一个具体任务里，为理解人机协作式质检提供了清楚的设计思路。

                👀 如果你关注工业质检、交互系统或质量控制，这篇论文值得读一读。

                #工业质检 #人机协作 #质量控制
                """;

        assertThat(validator.validate(copy, "zh", digest, false).valid()).isTrue();
    }

    @Test
    void rejectsPlaceholdersEllipsesAndMissingUsageStructure() {
        String copy = "📎【论文分享】 🤔 test1 正在遇到问题… 🧩 本文提出一个系统。✅ 很有价值。👀 值得阅读。 #研究";

        var result = validator.validate(copy, "zh", null, false);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("Placeholder"));
        assertThat(result.issues()).anyMatch(issue -> issue.contains("Ellipses"));
        assertThat(result.issues()).anyMatch(issue -> issue.contains("how the system"));
    }
}
