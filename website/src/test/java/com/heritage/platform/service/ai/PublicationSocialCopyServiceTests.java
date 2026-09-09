package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ForbiddenException;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.GrobidMetadata;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingResult;
import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import com.heritage.platform.dto.ai.ResolvedPaperUnderstanding;
import com.heritage.platform.dto.ai.SocialCopyGenerationRequest;
import com.heritage.platform.dto.ai.SocialCopyGenerationResult;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.PostRepository;
import com.heritage.platform.service.AuthContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicationSocialCopyServiceTests {

    private AuthContextService auth;
    private PostRepository posts;
    private UploadPathService paths;
    private PaperUnderstandingResolverService resolver;
    private SocialCopyProviderRouter router;
    private PaperAiQualityValidator quality;
    private PublicationSocialCopyService service;
    private Post publication;

    @BeforeEach
    void setUp() {
        auth = mock(AuthContextService.class);
        posts = mock(PostRepository.class);
        paths = mock(UploadPathService.class);
        resolver = mock(PaperUnderstandingResolverService.class);
        router = mock(SocialCopyProviderRouter.class);
        quality = mock(PaperAiQualityValidator.class);
        service = new PublicationSocialCopyService(
                auth, posts, paths, resolver, router, quality,
                new SocialCopyEvidenceDigestService(), new SocialCopyDebugService(new ObjectMapper()),
                new SocialCopyContentSpecService(), new EvidenceRetrievalService(), new SocialCopyClaimVerifier()
        );
        User user = mock(User.class);
        User author = mock(User.class);
        publication = mock(Post.class);
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(UserRole.CONTRIBUTOR);
        when(author.getId()).thenReturn(7L);
        when(publication.getPublication()).thenReturn(true);
        when(publication.getAuthor()).thenReturn(author);
        when(publication.getPdfUrl()).thenReturn("/uploads/paper.pdf");
        when(auth.requireActiveUser()).thenReturn(user);
        when(posts.findById(12L)).thenReturn(Optional.of(publication));
        when(paths.resolveUploadedPdf("/uploads/paper.pdf")).thenReturn(Path.of("paper.pdf"));
    }

    @Test
    void uploaderReceivesCopyOnlyWhenDeepSeekReturnsIt() {
        ResolvedPaperUnderstanding resolved = resolved();
        when(resolver.resolve(12L, publication, Path.of("paper.pdf"))).thenReturn(resolved);
        when(quality.isUsableUnderstanding(resolved.paperUnderstanding())).thenReturn(true);
        when(router.generate(any(), any(), eq("zh"), any(), any())).thenReturn(routed("DeepSeek copy"));

        SocialCopyGenerationResult result = service.generate(
                12L, new SocialCopyGenerationRequest("zh", "engaging", "student", "engagement", "short", "欢迎讨论", "outreach")
        );

        assertThat(result.source()).isEqualTo("DEEPSEEK");
        assertThat(result.copyText()).isEqualTo("DeepSeek copy");
        assertThat(result.warnings()).anyMatch(value -> value.contains("DeepSeek"));
        verify(router).generate(any(), any(), eq("zh"), any(), any());
    }

    @Test
    void deepSeekCreditFailureIsShownInsteadOfAReplacementCopy() {
        ResolvedPaperUnderstanding resolved = resolved();
        when(resolver.resolve(12L, publication, Path.of("paper.pdf"))).thenReturn(resolved);
        when(quality.isUsableUnderstanding(resolved.paperUnderstanding())).thenReturn(true);
        when(router.generate(any(), any(), eq("zh"), any(), any())).thenThrow(
                new SocialCopyProviderException("DEEPSEEK", SocialCopyFailureReason.INSUFFICIENT_CREDIT,
                        402, null, "DeepSeek account credit is insufficient.")
        );

        assertThatThrownBy(() -> service.generate(12L, new SocialCopyGenerationRequest("zh", "engaging")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("余额或可用额度不足");
    }

    @Test
    void rejectsUsersWhoAreNeitherUploaderNorAdmin() {
        when(publication.getAuthor().getId()).thenReturn(99L);

        assertThatThrownBy(() -> service.generate(12L, new SocialCopyGenerationRequest("zh", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("uploader or an administrator");
    }

    private SocialCopyProviderRouter.RoutedSocialCopy routed(String value) {
        OpenAiSocialCopyService.GeneratedSocialCopy copy = new OpenAiSocialCopyService.GeneratedSocialCopy(
                Map.of("engaging", value, "concise", value, "professional", value),
                List.of("工业质检", "人机协作", "检测系统")
        );
        return new SocialCopyProviderRouter.RoutedSocialCopy(copy, "DEEPSEEK", List.of(), null, true, true);
    }

    private ResolvedPaperUnderstanding resolved() {
        FinalPaperUnderstanding understanding = new FinalPaperUnderstanding(
                "Inspection assistant", "An assistant supports reliable industrial inspection.",
                List.of("A. Researcher"), 2026, "manual review is inconsistent", "quality inspectors",
                "sensor observations guide review", "sensor module and interface", "consistent review",
                List.of("sensor", "interface"), "observations become findings", "supports inspectors",
                null, List.of("generic poster"), "inspector reviews a part", List.of("inspector", "part"),
                List.of("reviews highlighted findings"), "inspection station", "HIGH", "Inspection Journal",
                "sensor-guided inspection assistant", "inspection interface", "sensor observations",
                "highlighted findings", "consistent defect review",
                List.of("inspector", "assistant", "station", "findings"), List.of("generic poster"),
                "inspection station", "inspect a part", "review highlighted findings"
        );
        PaperUnderstandingResult result = new PaperUnderstandingResult(
                "COMPLETED", GrobidMetadata.unavailable(null), null, understanding, List.of(), "OPENAI"
        );
        return new ResolvedPaperUnderstanding(
                "hash", evidence(), ReferenceGroundingContext.empty(null), result,
                false, "OPENAI", null, List.of()
        );
    }

    private PaperEvidencePacket evidence() {
        String body = "The paper proposes a sensor-guided inspection assistant for quality inspectors. ".repeat(5);
        return new PaperEvidencePacket(
                "Inspection assistant", body, List.of(), 2026, null, null, List.of("inspection"),
                List.of(body), List.of(body), List.of(body), List.of(body), List.of(body), List.of(), List.of(),
                List.of("inspection"), List.of(body), List.of("inspection interface"), body, body.length(), List.of()
        );
    }
}
