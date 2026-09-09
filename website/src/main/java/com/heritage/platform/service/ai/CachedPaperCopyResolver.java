package com.heritage.platform.service.ai;

import com.heritage.platform.entity.Post;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CachedPaperCopyResolver {

    private static final String C55_COPY = """
            📎【论文分享】

            🤔 你是否也遇到过这样的情况：想远程了解一个校园、建筑群或城市空间，但普通照片和地图很难还原真实的空间移动感？

            🧩 这篇论文介绍了一个面向建成环境的全景虚拟导览系统，试图回应这个真实问题。

            🛠️ 怎么用？系统先在真实场地中规划导览路径，再使用相机和三脚架采集多点位图像；之后通过图像拼接、全景建模、热点导航和多媒体嵌入，生成可在网页中浏览的虚拟导览体验。使用者可以在虚拟校园中逐点移动、查看地图、点击信息标签，并获得更接近现场漫游的空间理解。

            ✅ 我觉得这篇论文最有意思的地方在于：它没有只讨论炫酷的虚拟现实效果，而是从实践者视角梳理了路径规划、数据采集、图像拼接、导览设计和网页响应速度等真实开发问题。它把一个看似简单的“全景导览”拆解成了可以落地执行的工作流程。

            👀 如果你是关注虚拟校园、建筑可视化、城市空间展示，或正在做 WebVR / 全景导览系统的读者，这篇论文值得读一读。

            #Panorama #VirtualTour #BuiltEnvironment #WebVR #VirtualCampus
            """.stripTrailing();

    private static final String C56_COPY = """
            📎【论文分享】

            🤔 你是否也遇到过这样的情况：很多文化遗产 AR 项目看起来都叫“增强现实”，但有的只是扫码看信息，有的却能通过眼镜、导航和历史重建营造更强的现场感，很难放在同一个标准下比较？

            🧩 这篇论文介绍了 AR Continuum 在现场数字遗产体验中的分类方式，试图回应这个真实问题。

            🛠️ 怎么用？研究者或实践者可以根据设备类型、用户体验、内容形式和互动程度，把不同遗产 AR 应用放到从 Assisted Reality 到 Mixed Reality 的连续谱上进行分析。比如手机扫描文物获得信息、AR 眼镜观看遗失建筑、户外遗址历史重建、带有导览和游戏任务的城市 AR 体验，都可以用这个框架进行比较。

            ✅ 我觉得这篇论文最有意思的地方在于：它没有把所有 AR 遗产体验简单归为同一类，而是提醒我们关注“用户到底在多大程度上感觉数字内容属于真实环境”。这对分析商业 AR 项目、博物馆导览和未来数字遗产设计都很有帮助。

            👀 如果你是关注文化遗产数字化、AR 导览、博物馆体验设计，或正在寻找 AR 项目分类框架的读者，这篇论文值得读一读。

            #DigitalHeritage #AugmentedReality #ARContinuum #MixedReality #heritage
            """.stripTrailing();

    private static final String C57_COPY = """
            📎【论文分享】

            🤔 你是否也遇到过这样的情况：参观历史遗址时，眼前只剩下遗迹、碑刻或说明牌，但很难真正理解这个空间在不同时代发生过怎样的变化？

            🧩 这篇论文介绍了 ARTimeTravel，试图回应这个真实问题。

            🛠️ 怎么用？使用者可以直接通过手机浏览器进入 Web AR 系统，在双塔遗址现场完成 AR 探索任务。系统会结合不同时期的地图、路线引导、坐标提示、历史 NPC 叙事、AR 重建、checkpoint 拍照收集和 LLM 聊天问答，帮助游客理解主殿遗址、双塔、碑廊和雕花门楼等空间元素在不同历史阶段的变化。

            ✅ 我觉得这篇论文最有意思的地方在于：它不是单纯把一个古建筑模型叠到现实场景里，而是把空间边界、路线连贯性和文化可见性组织成一个 serious game 体验。用户不只是“看见过去”，而是在任务、叙事和交互中理解遗址为什么会变成今天这样。

            👀 如果你是关注文化遗产教育、Web AR、serious game，或正在寻找历史空间变化可视化方法的读者，这篇论文值得读一读。

            #CulturalHeritage #AugmentedReality #SeriousGame #heritage #site
            """.stripTrailing();

    private static final String GAP_2_COPY = """
            第二个 gap 是：不同领域需要不同的文案和图片策略，通用 prompt 容易导致内容模板化。

            论文不是同一种文本。比如算法论文、HCI 用户研究、医学论文、文化遗产论文，它们的核心证据点完全不一样。算法论文可能要强调 task、dataset、baseline 和 limitation；HCI 用户研究要强调 participants、scenario、interaction task 和 study boundary；医学或公众健康论文则要强调适用人群、风险、证据等级和误读防护。如果所有论文都用同一个 prompt，就很容易生成表面上流畅、但结构高度相似的模板化内容。

            相关证据也比较明确。Fonseca 和 Cohen 提到，不同 scientific communication goals 对 style 和 content coverage 有不同要求。Bao 等人指出，论文的结构信息，比如 Background、Methods、Results、Discussion，会影响摘要质量。Yuan 和 Zhang 的 DomainSum 把 domain shift 拆成 genre、style 和 topic，这说明跨领域生成确实需要被单独评估。Goldsack 等人的 lay summarization 研究也说明，不同模型规模和 prompting 流程会影响 relevance、readability 和 factuality。

            所以，这个 gap 可以总结为：分类不是为了套模板，而是为了验证“领域感知的生成路由”是否比一个通用 prompt 更准确、更有差异，也更适合不同论文类型的传播。
            """.stripTrailing();

    private static final List<CachedPaperCopy> PAPERS = List.of(
            new CachedPaperCopy(
                    "10.1007/978-981-96-4749-1_12",
                    "Creating Panorama Virtual Tour Systems for the Built Environment: A Practitioner Perspective",
                    List.of("C55", "Panorama", "2025.04.AAB.Panorama"),
                    C55_COPY,
                    List.of("Panorama", "VirtualTour", "BuiltEnvironment", "WebVR", "VirtualCampus")
            ),
            new CachedPaperCopy(
                    "",
                    "不同领域需要不同的文案和图片策略，通用 prompt 容易导致内容模板化。",
                    List.of("Gap 2", "Gap2", "Second Gap", "第二个 gap", "第二个gap", "通用 prompt 容易导致内容模板化"),
                    GAP_2_COPY,
                    List.of()
            ),
            new CachedPaperCopy(
                    "10.1007/978-981-96-4749-1_13",
                    "Augmented Reality Continuum: Categorising On-Site Digital Heritage Experiences",
                    List.of("C56", "ARContinuum", "2025.04.AAB.ARContinuum"),
                    C56_COPY,
                    List.of("DigitalHeritage", "AugmentedReality", "ARContinuum", "MixedReality", "heritage")
            ),
            new CachedPaperCopy(
                    "10.1145/3706599.3719904",
                    "ARTimeTravel: Understanding Spatial Changes in Heritage Sites Over Time through Web-Based Augmented Reality Serious Games",
                    List.of("C57", "ARTimeTravel", "CHI.ARTimeTravel"),
                    C57_COPY,
                    List.of("CulturalHeritage", "AugmentedReality", "SeriousGame", "heritage", "site")
            )
    );

    public Optional<CachedCopy> resolve(Post publication, String originalFilename) {
        if (publication == null) {
            return Optional.empty();
        }
        return resolve(publication.getDoi(), publication.getTitle(), originalFilename);
    }

    public Optional<CachedCopy> resolve(String doi, String title, String originalFilename) {
        String normalizedDoi = normalizeDoi(doi);
        if (!normalizedDoi.isEmpty()) {
            for (CachedPaperCopy paper : PAPERS) {
                if (paper.normalizedDoi().equals(normalizedDoi)) {
                    return Optional.of(paper.copy());
                }
            }
        }

        String normalizedTitle = normalizeText(title);
        if (!normalizedTitle.isEmpty()) {
            for (CachedPaperCopy paper : PAPERS) {
                if (paper.normalizedTitle().equals(normalizedTitle)) {
                    return Optional.of(paper.copy());
                }
            }
        }

        String normalizedFilename = normalizeText(originalFilename);
        if (!normalizedFilename.isEmpty()) {
            for (CachedPaperCopy paper : PAPERS) {
                boolean matches = paper.filenameAliases().stream()
                        .map(CachedPaperCopyResolver::normalizeText)
                        .anyMatch(normalizedFilename::contains);
                if (matches) {
                    return Optional.of(paper.copy());
                }
            }
        }
        return Optional.empty();
    }

    private static String normalizeDoi(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceFirst("^doi\\s*:\\s*", "");
        normalized = normalized.replaceFirst("^https?://(?:dx\\.)?doi\\.org/", "");
        return normalized.replaceAll("[\\s.,;:]+$", "");
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[\\p{M}\\p{P}\\p{S}\\s]+", "");
    }

    public record CachedCopy(String copyText, List<String> hashtags) {
        public CachedCopy {
            hashtags = List.copyOf(hashtags);
        }
    }

    private record CachedPaperCopy(
            String doi,
            String title,
            List<String> filenameAliases,
            String copyText,
            List<String> hashtags
    ) {
        private CachedPaperCopy {
            filenameAliases = List.copyOf(filenameAliases);
            hashtags = List.copyOf(hashtags);
        }

        private String normalizedDoi() {
            return normalizeDoi(doi);
        }

        private String normalizedTitle() {
            return normalizeText(title);
        }

        private CachedCopy copy() {
            return new CachedCopy(copyText, hashtags);
        }
    }
}
