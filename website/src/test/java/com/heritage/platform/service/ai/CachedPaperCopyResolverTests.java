package com.heritage.platform.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CachedPaperCopyResolverTests {

    private final CachedPaperCopyResolver resolver = new CachedPaperCopyResolver();

    @Test
    void exactDoiTakesPriorityOverConflictingTitleAndFilename() {
        var copy = resolver.resolve(
                "https://doi.org/10.1007/978-981-96-4749-1_13",
                "Creating Panorama Virtual Tour Systems for the Built Environment: A Practitioner Perspective",
                "C57-CHI.ARTimeTravel.pdf"
        );

        assertThat(copy).isPresent();
        assertThat(copy.orElseThrow().hashtags()).containsExactly(
                "DigitalHeritage", "AugmentedReality", "ARContinuum", "MixedReality", "heritage"
        );
    }

    @Test
    void normalizedTitleMatchesAcrossCasePunctuationAndWhitespace() {
        var copy = resolver.resolve(
                null,
                "  augmented reality continuum -- categorising on-site\n digital heritage experiences ",
                "unknown.pdf"
        );

        assertThat(copy).isPresent();
        assertThat(copy.orElseThrow().copyText()).contains("AR Continuum", "#DigitalHeritage");
    }

    @Test
    void filenameAliasesMatchAllThreeUploadedPapers() {
        assertThat(resolver.resolve(null, null, "uuid-[C55]2025.04.AAB.Panorama.pdf")).isPresent();
        assertThat(resolver.resolve(null, null, "uuid-[C56]2025.04.AAB.ARContinuum.pdf")).isPresent();
        assertThat(resolver.resolve(null, null, "uuid-[C57]2025.04.CHI.ARTimeTravel-(1).pdf")).isPresent();
    }

    @Test
    void gapTwoAliasReturnsFixedChineseDemonstrationCopy() {
        var copy = resolver.resolve(null, null, "demo-Gap 2.pdf").orElseThrow();

        assertThat(copy.copyText()).isEqualTo("""
                第二个 gap 是：不同领域需要不同的文案和图片策略，通用 prompt 容易导致内容模板化。

                论文不是同一种文本。比如算法论文、HCI 用户研究、医学论文、文化遗产论文，它们的核心证据点完全不一样。算法论文可能要强调 task、dataset、baseline 和 limitation；HCI 用户研究要强调 participants、scenario、interaction task 和 study boundary；医学或公众健康论文则要强调适用人群、风险、证据等级和误读防护。如果所有论文都用同一个 prompt，就很容易生成表面上流畅、但结构高度相似的模板化内容。

                相关证据也比较明确。Fonseca 和 Cohen 提到，不同 scientific communication goals 对 style 和 content coverage 有不同要求。Bao 等人指出，论文的结构信息，比如 Background、Methods、Results、Discussion，会影响摘要质量。Yuan 和 Zhang 的 DomainSum 把 domain shift 拆成 genre、style 和 topic，这说明跨领域生成确实需要被单独评估。Goldsack 等人的 lay summarization 研究也说明，不同模型规模和 prompting 流程会影响 relevance、readability 和 factuality。

                所以，这个 gap 可以总结为：分类不是为了套模板，而是为了验证“领域感知的生成路由”是否比一个通用 prompt 更准确、更有差异，也更适合不同论文类型的传播。
                """.stripTrailing());
        assertThat(copy.hashtags()).isEmpty();
    }

    @Test
    void unrelatedPaperDoesNotMatch() {
        assertThat(resolver.resolve(
                "10.0000/unrelated",
                "A Different Heritage Paper",
                "different-paper.pdf"
        )).isEmpty();
    }

    @Test
    void fixedCopiesPreserveRequiredParagraphAndHashtagStructure() {
        for (String doi : new String[]{
                "10.1007/978-981-96-4749-1_12",
                "10.1007/978-981-96-4749-1_13",
                "10.1145/3706599.3719904"
        }) {
            var copy = resolver.resolve(doi, null, null).orElseThrow();
            assertThat(copy.copyText()).startsWith("📎【论文分享】\n\n🤔");
            assertThat(copy.copyText().split("\\n\\n")).hasSize(7);
            assertThat(copy.copyText()).doesNotContain("\n\n\n");
            assertThat(copy.hashtags()).hasSize(5);
            assertThat(copy.copyText().substring(copy.copyText().lastIndexOf('#'))).contains("#");
        }
    }
}
