package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class CommentRepositoryDeleteCommentTest extends AbstractIT {
    @Autowired
    CommentRepository commentRepository;

    @Test
    @Transactional
    void shouldDeleteCommentByIdAndUser() {
        // comment ID 1 is created by "user" in test-data.sql
        int count = commentRepository.deleteComment(1L, "user");
        assertThat(count).isEqualTo(1);
        assertThat(commentRepository.findById(1L)).isEmpty();
    }

    @Test
    @Transactional
    void shouldReturnZeroWhenUserMismatch() {
        int count = commentRepository.deleteComment(1L, "wrong-user");
        assertThat(count).isEqualTo(0);
    }

    @Test
    @Transactional
    void shouldReturnZeroWhenCommentNotFound() {
        int count = commentRepository.deleteComment(999L, "user");
        assertThat(count).isEqualTo(0);
    }
}
