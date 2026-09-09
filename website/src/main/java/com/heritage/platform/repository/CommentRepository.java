package com.heritage.platform.repository;

import com.heritage.platform.entity.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    @EntityGraph(attributePaths = {"author"})
    List<Comment> findByPostIdOrderByCreatedAtDesc(Long postId);

    @EntityGraph(attributePaths = {"author", "post"})
    Optional<Comment> findByIdAndPostId(Long id, Long postId);
}
