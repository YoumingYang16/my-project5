package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.response.AdminPostPageResult;
import com.heritage.platform.dto.request.AdminPostReviewRequest;
import com.heritage.platform.dto.response.AdminPostDetailResponse;
import com.heritage.platform.dto.response.AdminPostSummaryResponse;
import com.heritage.platform.dto.response.CommentResponse;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.PostImage;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.AdminPostSort;
import com.heritage.platform.enums.PostStatus;
import com.heritage.platform.repository.CommentRepository;
import com.heritage.platform.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminPostService {

    private static final PostStatus HIDDEN_ADMIN_STATUS = PostStatus.DRAFT;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AuthContextService authContextService;
    private final PublicationWorkflowValidationService publicationWorkflowValidationService;

    public AdminPostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            AuthContextService authContextService,
            PublicationWorkflowValidationService publicationWorkflowValidationService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.authContextService = authContextService;
        this.publicationWorkflowValidationService = publicationWorkflowValidationService;
    }

    @Transactional(readOnly = true)
    public List<AdminPostSummaryResponse> listPosts(PostStatus status, String title, AdminPostSort sort) {
        authContextService.requireAdmin();

        String trimmedTitle = title == null ? "" : title.trim();
        boolean hasTitle = !trimmedTitle.isEmpty();
        AdminPostSort sortOption = sort == null ? AdminPostSort.UPDATED_DESC : sort;

        if (status == HIDDEN_ADMIN_STATUS) {
            return List.of();
        }

        List<Post> posts;
        if (status != null && hasTitle) {
            posts = postRepository.findAllByStatusAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(status, trimmedTitle);
        } else if (status != null) {
            posts = postRepository.findAllByStatusOrderByUpdatedAtDesc(status);
        } else if (hasTitle) {
            posts = postRepository.findAllByStatusNotAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(HIDDEN_ADMIN_STATUS, trimmedTitle);
        } else {
            posts = postRepository.findAllByStatusNotOrderByUpdatedAtDesc(HIDDEN_ADMIN_STATUS);
        }

        posts = posts.stream()
                .filter(Post::getPublication)
                .sorted(sortComparator(sortOption))
                .toList();

        return posts.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public AdminPostPageResult listPostsPage(PostStatus status, String title, AdminPostSort sort, Integer page, Integer size) {
        authContextService.requireAdmin();

        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 10 : size;
        if (pageNumber < 0) {
            throw new BadRequestException("Page number cannot be less than 0.");
        }
        if (pageSize <= 0) {
            throw new BadRequestException("Page size must be greater than 0.");
        }
        if (pageSize > 50) {
            pageSize = 50;
        }

        String trimmedTitle = title == null ? "" : title.trim();
        boolean hasTitle = !trimmedTitle.isEmpty();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, buildSort(sort == null ? AdminPostSort.UPDATED_DESC : sort));

        if (status == HIDDEN_ADMIN_STATUS) {
            return new AdminPostPageResult(
                    List.of(),
                    pageNumber,
                    pageSize,
                    0,
                    0,
                    pageNumber > 0,
                    false
            );
        }

        Page<Post> posts;
        if (status != null && hasTitle) {
            posts = postRepository.findAllByStatusAndTitleContainingIgnoreCase(status, trimmedTitle, pageable);
        } else if (status != null) {
            posts = postRepository.findAllByStatus(status, pageable);
        } else if (hasTitle) {
            posts = postRepository.findAllByStatusNotAndTitleContainingIgnoreCase(HIDDEN_ADMIN_STATUS, trimmedTitle, pageable);
        } else {
            posts = postRepository.findAllByStatusNot(HIDDEN_ADMIN_STATUS, pageable);
        }

        return new AdminPostPageResult(
                posts.stream().filter(Post::getPublication).map(this::toSummary).toList(),
                posts.getNumber(),
                posts.getSize(),
                posts.getTotalElements(),
                posts.getTotalPages(),
                posts.hasPrevious(),
                posts.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminPostDetailResponse getDetail(Long postId) {
        authContextService.requireAdmin();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }
        return toDetail(post);
    }

    @Transactional
    public AdminPostDetailResponse review(Long postId, AdminPostReviewRequest request) {
        User admin = authContextService.requireAdmin();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }

        if (post.getStatus() != PostStatus.PENDING_REVIEW) {
            throw new BadRequestException("Only pending review publications can be reviewed.");
        }

        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(action)) {
            publicationWorkflowValidationService.validateReadyForSubmission(post);
            post.approve(admin);
        } else if ("REJECT".equals(action)) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new BadRequestException("Please provide a rejection reason.");
            }
            post.reject(admin, request.reason().trim());
        } else {
            throw new BadRequestException("Unsupported review action.");
        }

        return toDetail(post);
    }

    @Transactional
    public AdminPostDetailResponse archive(Long postId) {
        User admin = authContextService.requireAdmin();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }

        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BadRequestException("Only published publications can be archived.");
        }

        post.archive(admin);
        return toDetail(post);
    }

    @Transactional
    public AdminPostDetailResponse restore(Long postId) {
        User admin = authContextService.requireAdmin();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("The publication could not be found."));
        if (!post.getPublication()) {
            throw new ResourceNotFoundException("The publication could not be found.");
        }

        if (post.getStatus() != PostStatus.ARCHIVED) {
            throw new BadRequestException("Only archived publications can be restored.");
        }

        post.restore(admin);
        return toDetail(post);
    }

    private Comparator<Post> sortComparator(AdminPostSort sortOption) {
        return switch (sortOption) {
            case SUBMITTED_DESC -> Comparator
                    .comparing(Post::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Post::getUpdatedAt, Comparator.reverseOrder());
            case REVIEWED_DESC -> Comparator
                    .comparing(Post::getReviewedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Post::getUpdatedAt, Comparator.reverseOrder());
            case UPDATED_DESC -> Comparator.comparing(Post::getUpdatedAt, Comparator.reverseOrder());
        };
    }

    private Sort buildSort(AdminPostSort sortOption) {
        return switch (sortOption) {
            case SUBMITTED_DESC -> Sort.by(
                    Sort.Order.desc("submittedAt"),
                    Sort.Order.desc("updatedAt")
            );
            case REVIEWED_DESC -> Sort.by(
                    Sort.Order.desc("reviewedAt"),
                    Sort.Order.desc("updatedAt")
            );
            case UPDATED_DESC -> Sort.by(Sort.Order.desc("updatedAt"));
        };
    }

    private AdminPostSummaryResponse toSummary(Post post) {
        return new AdminPostSummaryResponse(
                post.getId(),
                post.getParticipantId(),
                post.getTitle(),
                post.getStatus(),
                post.getAuthor().getNickname(),
                post.getCategory().getName(),
                post.getRejectReason(),
                post.getSubmittedAt(),
                post.getReviewedAt(),
                post.getReviewedBy() == null ? null : post.getReviewedBy().getNickname(),
                post.getUpdatedAt(),
                post.getCreatedAt()
        );
    }

    private AdminPostDetailResponse toDetail(Post post) {
        List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId()).stream()
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

        return new AdminPostDetailResponse(
                post.getId(),
                post.getParticipantId(),
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
                post.getHeritageName(),
                post.getRegion(),
                post.getStatus(),
                post.getAuthor().getId(),
                post.getAuthor().getNickname(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getLikeCount(),
                post.getFavoriteCount(),
                post.getCommentCount(),
                imageUrls,
                comments,
                post.getRejectReason(),
                post.getSubmittedAt(),
                post.getReviewedAt(),
                post.getReviewedBy() == null ? null : post.getReviewedBy().getId(),
                post.getReviewedBy() == null ? null : post.getReviewedBy().getNickname(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}

