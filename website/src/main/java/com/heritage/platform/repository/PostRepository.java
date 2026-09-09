package com.heritage.platform.repository;

import com.heritage.platform.entity.Post;
import com.heritage.platform.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    Page<Post> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByStatusOrderByCreatedAtDesc(PostStatus status);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    @Query("""
            select p
            from Post p
            join p.author a
            join p.category c
            where p.status = :status
              and p.publication = true
              and (
                    :keyword is null
                    or :keyword = ''
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.keywords) like lower(concat('%', :keyword, '%'))
                    or lower(p.publicationAuthors) like lower(concat('%', :keyword, '%'))
                    or lower(cast(p.publicationYear as string)) like lower(concat('%', :keyword, '%'))
                    or lower(p.venue) like lower(concat('%', :keyword, '%'))
                    or lower(p.researchArea) like lower(concat('%', :keyword, '%'))
                    or lower(p.doi) like lower(concat('%', :keyword, '%'))
                    or lower(cast(p.abstractText as string)) like lower(concat('%', :keyword, '%'))
              )
              and (
                    :categoryName is null
                    or :categoryName = ''
                    or c.name = :categoryName
              )
            order by
                case
                    when :keyword is null or :keyword = '' then 5
                    when lower(p.title) like lower(concat('%', :keyword, '%')) then 1
                    when lower(p.publicationAuthors) like lower(concat('%', :keyword, '%')) then 2
                    when lower(p.venue) like lower(concat('%', :keyword, '%')) then 3
                    when lower(p.researchArea) like lower(concat('%', :keyword, '%')) then 4
                    when lower(p.keywords) like lower(concat('%', :keyword, '%')) then 5
                    when lower(p.doi) like lower(concat('%', :keyword, '%')) then 6
                    when lower(cast(p.publicationYear as string)) like lower(concat('%', :keyword, '%')) then 7
                    when lower(cast(p.abstractText as string)) like lower(concat('%', :keyword, '%')) then 8
                    else 10
                end,
                p.publicationYear desc,
                p.createdAt desc
            """)
    List<Post> searchPublishedPosts(
            @Param("status") PostStatus status,
            @Param("keyword") String keyword,
            @Param("categoryName") String categoryName
    );

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByStatusOrderByUpdatedAtDesc(PostStatus status);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    Page<Post> findAllByStatus(PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByStatusNotOrderByUpdatedAtDesc(PostStatus status);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    Page<Post> findAllByStatusNot(PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByStatusOrderBySubmittedAtDescUpdatedAtDesc(PostStatus status);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByTitleContainingIgnoreCaseOrderByUpdatedAtDesc(String title);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    Page<Post> findAllByTitleContainingIgnoreCase(String title, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByStatusNotAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(PostStatus status, String title);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    Page<Post> findAllByStatusNotAndTitleContainingIgnoreCase(PostStatus status, String title, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByStatusAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(PostStatus status, String title);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    Page<Post> findAllByStatusAndTitleContainingIgnoreCase(PostStatus status, String title, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByStatusAndTitleContainingIgnoreCaseOrderBySubmittedAtDescUpdatedAtDesc(PostStatus status, String title);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByAuthorIdOrderByUpdatedAtDesc(Long authorId);

    @EntityGraph(attributePaths = {"author", "category", "reviewedBy"})
    List<Post> findAllByAuthorIdAndStatusOrderByUpdatedAtDesc(Long authorId, PostStatus status);

    @EntityGraph(attributePaths = {"author", "category", "images", "reviewedBy"})
    Optional<Post> findById(Long id);

    @EntityGraph(attributePaths = {"author", "category", "images", "reviewedBy"})
    Optional<Post> findByIdAndStatus(Long id, PostStatus status);

    @EntityGraph(attributePaths = {"author", "category", "images", "reviewedBy"})
    Optional<Post> findByIdAndStatusAndPublicationTrue(Long id, PostStatus status);

    @EntityGraph(attributePaths = {"author", "category", "images", "reviewedBy"})
    Optional<Post> findByIdAndAuthorId(Long id, Long authorId);

    long countByAuthorIdAndStatus(Long authorId, PostStatus status);
}
