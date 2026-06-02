package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Comment;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

interface CommentRepository extends ListCrudRepository<Comment, Long> {
    List<Comment> findByFeature(Feature feature);

    List<Comment> findByRelease(Release release);

    List<Comment> findByAuthor(String author);

    @Modifying
    @Query("delete from Comment c where c.author = :userId and c.id = :commentId")
    int deleteComment(Long commentId, String userId);

    @Query("""
            select c from Comment c where c.feature.code = :featureCode
            """)
    List<Comment> findCommentsByFeatureCode(String featureCode, Pageable pageable);
}
