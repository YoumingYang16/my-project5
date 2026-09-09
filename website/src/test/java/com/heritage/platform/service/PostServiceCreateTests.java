package com.heritage.platform.service;

import com.heritage.platform.dto.request.PostCreateRequest;
import com.heritage.platform.dto.response.PostDetailResponse;
import com.heritage.platform.entity.Category;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.PostStatus;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.CommentRepository;
import com.heritage.platform.repository.PostLikeRepository;
import com.heritage.platform.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceCreateTests {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private PublicationWorkflowValidationService publicationWorkflowValidationService;

    private PostService postService;
    private Category publicationCategory;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postRepository,
                commentRepository,
                postLikeRepository,
                categoryService,
                authContextService,
                publicationWorkflowValidationService
        );
        publicationCategory = Category.create("Academic Publication", "Academic publication records");

        when(categoryService.getPublicationCategory()).thenReturn(publicationCategory);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(nullable(Long.class))).thenReturn(List.of());
    }

    @Test
    void adminCreatesPublicationAsPublished() {
        User admin = new User("admin", "hash", "Admin", null, UserRole.ADMIN, true);
        when(authContextService.requireContributor()).thenReturn(admin);

        PostDetailResponse response = postService.create(validPublicationRequest());

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();

        assertThat(response.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(savedPost.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(savedPost.getSubmittedAt()).isNotNull();
        assertThat(savedPost.getReviewedAt()).isNotNull();
        assertThat(savedPost.getReviewedBy()).isSameAs(admin);
        verify(publicationWorkflowValidationService).validateReadyForSubmission(savedPost);
    }

    @Test
    void contributorCreatesPublicationAsDraft() {
        User contributor = new User("contributor", "hash", "Contributor", null, UserRole.CONTRIBUTOR, true);
        when(authContextService.requireContributor()).thenReturn(contributor);

        PostDetailResponse response = postService.create(validPublicationRequest());

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());

        assertThat(response.status()).isEqualTo(PostStatus.DRAFT);
        assertThat(postCaptor.getValue().getStatus()).isEqualTo(PostStatus.DRAFT);
        verify(publicationWorkflowValidationService, never()).validateReadyForSubmission(any(Post.class));
    }

    @Test
    void longPublicationTitleKeepsFullTitleAndBoundsLegacyHeritageName() {
        User contributor = new User("contributor", "hash", "Contributor", null, UserRole.CONTRIBUTOR, true);
        when(authContextService.requireContributor()).thenReturn(contributor);
        String title = "Parametric H-BIM for Chinese Historical Architectures Based on Ancient Design Principles and 3D Reconstruction Technologies";
        PostCreateRequest request = new PostCreateRequest(
                title, "Paper abstract", null, null, null, null, List.of(), true,
                "Xinyu Tong", 2026, "Journal of Green Building", "Paper abstract",
                "H-BIM", "10.3992/jgb.21.2.313", null, "Digital heritage",
                "/uploads/paper.pdf", null, null
        );

        PostDetailResponse response = postService.create(request);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(response.title()).isEqualTo(title);
        assertThat(postCaptor.getValue().getHeritageName()).hasSize(100);
    }
    private PostCreateRequest validPublicationRequest() {
        return new PostCreateRequest(
                "Paper Title",
                "Paper abstract",
                null,
                null,
                null,
                null,
                List.of(),
                true,
                "Ada Lovelace",
                2026,
                "CHI 2026",
                "Paper abstract",
                "heritage, ai",
                null,
                null,
                "Human-Computer Interaction",
                "/uploads/paper.pdf",
                null,
                null
        );
    }
}
