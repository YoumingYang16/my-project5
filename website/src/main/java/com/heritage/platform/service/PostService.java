package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ForbiddenException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.request.CommentCreateRequest;
import com.heritage.platform.dto.request.PostCreateRequest;
import com.heritage.platform.dto.request.PostUpdateRequest;
import com.heritage.platform.dto.response.CommentResponse;
import com.heritage.platform.dto.response.MyPostSummaryResponse;
import com.heritage.platform.dto.response.PostDetailResponse;
import com.heritage.platform.dto.response.PostSummaryResponse;
import com.heritage.platform.entity.Category;
import com.heritage.platform.entity.Comment;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.PostImage;
import com.heritage.platform.entity.PostLike;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.PostStatus;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.CommentRepository;
import com.heritage.platform.repository.PostLikeRepository;
import com.heritage.platform.repository.PostRepository;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PostService {

    private static final String PUBLICATION_REGION = "Academic Publication";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final CategoryService categoryService;
    private final AuthContextService authContextService;
    private final PublicationWorkflowValidationService publicationWorkflowValidationService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDirectory;

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            PostLikeRepository postLikeRepository,
            CategoryService categoryService,
            AuthContextService authContextService,
            PublicationWorkflowValidationService publicationWorkflowValidationService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.categoryService = categoryService;
        this.authContextService = authContextService;
        this.publicationWorkflowValidationService = publicationWorkflowValidationService;
    }

    @Transactional
    public PostDetailResponse create(PostCreateRequest request) {
        User author = authContextService.requireActiveUser();
        PublicationLegacyFields legacyFields = legacyFields(request.title(), request.content(), request.abstractText());
        Category publicationCategory = categoryService.getPublicationCategory();

        Post post = Post.create(
                legacyFields.title(),
                legacyFields.content(),
                request.coverImageUrl(),
                legacyFields.heritageName(),
                PUBLICATION_REGION,
                author,
                publicationCategory
        );

        post.updatePublicationMetadata(
                true,
                clean(request.publicationAuthors()),
                request.publicationYear(),
                clean(request.venue()),
                clean(request.abstractText()),
                clean(request.keywords()),
                clean(request.doi()),
                clean(request.bibtex()),
                clean(request.researchArea()),
                clean(request.pdfUrl()),
                clean(request.codeUrl()),
                clean(request.datasetUrl()),
                clean(request.participantId())
        );
        post.replaceImages(buildImages(post, request.imageUrls()));
        if (author.getRole() == UserRole.ADMIN) {
            publicationWorkflowValidationService.validateReadyForSubmission(post);
            post.submitForReview();
            post.approve(author);
        }
        Post saved = postRepository.save(post);
        writeParticipantRecord(saved);
        return toDetail(saved);
    }

    @Transactional
    public PostDetailResponse update(Long postId, PostUpdateRequest request) {
        User currentUser = authContextService.requireActiveUser();
        Post post = postRepository.findByIdAndAuthorId(postId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));

        if (post.getStatus() != PostStatus.DRAFT && post.getStatus() != PostStatus.REJECTED) {
            throw new BadRequestException("Only draft or rejected publications can be edited.");
        }
        PublicationLegacyFields legacyFields = legacyFields(request.title(), request.content(), request.abstractText());

        post.update(
                legacyFields.title(),
                legacyFields.content(),
                request.coverImageUrl(),
                legacyFields.heritageName(),
                PUBLICATION_REGION,
                categoryService.getPublicationCategory()
        );
        post.updatePublicationMetadata(
                true,
                clean(request.publicationAuthors()),
                request.publicationYear(),
                clean(request.venue()),
                clean(request.abstractText()),
                clean(request.keywords()),
                clean(request.doi()),
                clean(request.bibtex()),
                clean(request.researchArea()),
                clean(request.pdfUrl()),
                clean(request.codeUrl()),
                clean(request.datasetUrl()),
                clean(request.participantId())
        );
        post.replaceImages(buildImages(post, request.imageUrls()));
        writeParticipantRecord(post);
        return toDetail(post);
    }

    @Transactional
    public PostDetailResponse submitForReview(Long postId) {
        User currentUser = authContextService.requireActiveUser();
        Post post = postRepository.findByIdAndAuthorId(postId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));

        if (post.getStatus() != PostStatus.DRAFT && post.getStatus() != PostStatus.REJECTED) {
            throw new BadRequestException("This publication cannot be submitted for review right now.");
        }
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }

        publicationWorkflowValidationService.validateReadyForSubmission(post);
        post.submitForReview();
        return toDetail(post);
    }

    @Transactional(readOnly = true)
    public List<MyPostSummaryResponse> listMyPosts(PostStatus status) {
        User currentUser = authContextService.requireActiveUser();
        if (status == PostStatus.ARCHIVED) {
            return List.of();
        }
        List<Post> posts = status == null
                ? postRepository.findAllByAuthorIdOrderByUpdatedAtDesc(currentUser.getId())
                : postRepository.findAllByAuthorIdAndStatusOrderByUpdatedAtDesc(currentUser.getId(), status);

        return posts.stream()
                .filter(post -> post.getStatus() != PostStatus.ARCHIVED)
                .filter(Post::getPublication)
                .map(this::toMySummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getMyPostDetail(Long postId) {
        User currentUser = authContextService.requireActiveUser();
        Post post = postRepository.findByIdAndAuthorId(postId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (post.getStatus() == PostStatus.ARCHIVED) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        return toDetail(post);
    }

    @Transactional
    public void deleteMyPost(Long postId) {
        User currentUser = authContextService.requireActiveUser();
        Post post = postRepository.findByIdAndAuthorId(postId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (post.getStatus() == PostStatus.ARCHIVED) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }

        post.archive(currentUser);
    }

    @Transactional
    public PostDetailResponse returnMyPostToDraft(Long postId) {
        User currentUser = authContextService.requireActiveUser();
        Post post = postRepository.findByIdAndAuthorId(postId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (post.getStatus() == PostStatus.ARCHIVED) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        if (post.getStatus() == PostStatus.DRAFT || post.getStatus() == PostStatus.REJECTED) {
            return toDetail(post);
        }

        post.returnToDraftForEditing();
        return toDetail(post);
    }

    @Transactional(readOnly = true)
    public List<PostSummaryResponse> listAll() {
        return listAll(null, null);
    }

    @Transactional(readOnly = true)
    public List<PostSummaryResponse> listAll(String keyword, String categoryName) {
        String trimmedKeyword = keyword == null ? null : keyword.trim();
        if (trimmedKeyword != null && trimmedKeyword.isEmpty()) {
            trimmedKeyword = null;
        }

        String trimmedCategoryName = categoryName == null ? null : categoryName.trim();
        if (trimmedCategoryName != null && trimmedCategoryName.isEmpty()) {
            trimmedCategoryName = null;
        }

        return postRepository.searchPublishedPosts(PostStatus.PUBLISHED, trimmedKeyword, trimmedCategoryName)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public PostDetailResponse getDetail(Long postId) {
        Post post = postRepository.findByIdAndStatusAndPublicationTrue(postId, PostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        post.increaseViewCount();
        return toDetail(post);
    }

    @Transactional
    public PostDetailResponse likePost(Long postId) {
        User currentUser = authContextService.requireActiveUser();
        Post post = postRepository.findByIdAndStatusAndPublicationTrue(postId, PostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));

        postLikeRepository.findByUserIdAndPostId(currentUser.getId(), post.getId())
                .ifPresentOrElse(existingLike -> {
                    postLikeRepository.delete(existingLike);
                }, () -> {
                    postLikeRepository.save(PostLike.create(currentUser, post));
                });

        post.syncLikeCount(postLikeRepository.countByPostId(post.getId()));
        return toDetail(post);
    }

    @Transactional
    public CommentResponse addComment(Long postId, CommentCreateRequest request) {
        Post post = postRepository.findByIdAndStatusAndPublicationTrue(postId, PostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        User author = authContextService.requireActiveUser();

        Comment comment = commentRepository.save(Comment.create(request.content(), author, post));
        post.increaseCommentCount();

        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                author.getId(),
                author.getNickname(),
                comment.getCreatedAt()
        );
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId) {
        User currentUser = authContextService.requireActiveUser();
        Comment comment = commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("The comment could not be found."));

        boolean isAuthor = comment.getAuthor().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("Only the comment author or an administrator can delete this comment.");
        }

        Post post = comment.getPost();
        commentRepository.delete(comment);
        post.decreaseCommentCount();
    }

    private List<PostImage> buildImages(Post post, List<String> imageUrls) {
        List<String> urls = imageUrls == null ? List.of() : imageUrls;
        List<PostImage> images = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            images.add(PostImage.create(urls.get(i), null, i + 1, post));
        }
        return images;
    }

    private PostDetailResponse toDetail(Post post) {
        List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId()).stream()
                .map(comment -> new CommentResponse(
                        comment.getId(),
                        comment.getContent(),
                        comment.getAuthor().getId(),
                        comment.getAuthor().getNickname(),
                        comment.getCreatedAt()
                ))
                .toList();

        List<String> imageUrls = post.getImages() == null
                ? new ArrayList<>()
                : post.getImages().stream().map(PostImage::getImageUrl).toList();

        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCoverImageUrl(),
                post.getPublication(),
                post.getPublicationAuthors(),
                post.getPublicationYear(),
                post.getVenue(),
                post.getAbstractText(),
                post.getKeywords(),
                post.getDoi(),
                post.getBibtex(),
                post.getResearchArea(),
                post.getPdfUrl(),
                post.getCodeUrl(),
                post.getDatasetUrl(),
                post.getParticipantId(),
                post.getHeritageName(),
                post.getRegion(),
                post.getStatus(),
                post.getAuthor().getNickname(),
                post.getAuthor().getId(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getLikeCount(),
                post.getFavoriteCount(),
                post.getCommentCount(),
                post.getViewCount(),
                isLikedByCurrentUser(post.getId()),
                imageUrls,
                comments,
                post.getCreatedAt()
        );
    }

    private PostSummaryResponse toSummary(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getCoverImageUrl(),
                post.getPublication(),
                post.getPublicationAuthors(),
                post.getPublicationYear(),
                post.getVenue(),
                post.getAbstractText(),
                post.getKeywords(),
                post.getDoi(),
                post.getBibtex(),
                post.getResearchArea(),
                post.getPdfUrl(),
                post.getCodeUrl(),
                post.getDatasetUrl(),
                post.getHeritageName(),
                post.getRegion(),
                post.getStatus(),
                post.getAuthor().getNickname(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getLikeCount(),
                post.getFavoriteCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getCreatedAt()
        );
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void writeParticipantRecord(Post post) {
        String participantId = clean(post.getParticipantId());
        if (participantId == null || post.getId() == null) {
            return;
        }
        String safeParticipantId = participantId.replaceAll("[^A-Za-z0-9_-]", "_");
        try {
            Path root = Path.of(uploadDirectory == null || uploadDirectory.isBlank() ? "uploads" : uploadDirectory)
                    .toAbsolutePath().normalize();
            Path directory = root.resolve("participant-records").resolve(safeParticipantId).normalize();
            if (!directory.startsWith(root)) {
                return;
            }
            Files.createDirectories(directory);
            String outputDirectory = root.resolve("generated-covers").resolve(String.valueOf(post.getId())).toString();
            String value = "Participant ID: " + participantId + System.lineSeparator()
                    + "Publication ID: " + post.getId() + System.lineSeparator()
                    + "Title: " + post.getTitle() + System.lineSeparator()
                    + "PDF: " + (post.getPdfUrl() == null ? "" : post.getPdfUrl()) + System.lineSeparator()
                    + "Generated outputs: " + outputDirectory + System.lineSeparator()
                    + "Social copy: " + Path.of(outputDirectory, "debug", "social-copy-final-result.json") + System.lineSeparator()
                    + "Image manifest: " + Path.of(outputDirectory, "renderer-manifest.json") + System.lineSeparator();
            Files.writeString(directory.resolve("publication-" + post.getId() + ".txt"), value, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Experiment indexing must never interrupt publication upload or generation.
        }
    }

    private PublicationLegacyFields legacyFields(String rawTitle, String rawContent, String rawAbstract) {
        String title = clean(rawTitle);
        if (title == null) {
            throw new BadRequestException("A title is required.");
        }
        String abstractText = clean(rawAbstract);
        String content = clean(rawContent);
        if (content == null) {
            content = abstractText == null ? title : abstractText;
        }
        String legacyHeritageName = title.length() > 100 ? title.substring(0, 100) : title;
        return new PublicationLegacyFields(title, content, legacyHeritageName);
    }

    private boolean isLikedByCurrentUser(Long postId) {
        Long userId = currentRequestUserId();
        return userId != null && postLikeRepository.existsByUserIdAndPostId(userId, postId);
    }

    private Long currentRequestUserId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        Object userId = servletAttributes.getRequest().getAttribute("userId");
        return userId instanceof Long id ? id : null;
    }

    private MyPostSummaryResponse toMySummary(Post post) {
        return new MyPostSummaryResponse(
                post.getId(),
                post.getParticipantId(),
                post.getTitle(),
                post.getCoverImageUrl(),
                post.getHeritageName(),
                post.getRegion(),
                post.getPublication(),
                post.getPublicationAuthors(),
                post.getPublicationYear(),
                post.getVenue(),
                post.getAbstractText(),
                post.getKeywords(),
                post.getDoi(),
                post.getBibtex(),
                post.getResearchArea(),
                post.getPdfUrl(),
                post.getCodeUrl(),
                post.getDatasetUrl(),
                post.getStatus(),
                post.getCategory().getName(),
                post.getRejectReason(),
                post.getSubmittedAt(),
                post.getUpdatedAt(),
                post.getCreatedAt()
        );
    }

    private record PublicationLegacyFields(
            String title,
            String content,
            String heritageName
    ) {
    }
}

