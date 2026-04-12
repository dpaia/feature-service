package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.dtos.CommentDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CommentServiceTest extends AbstractIT {

    @Autowired
    CommentService commentService;

    @Test
    void shouldFindCommentsByFeatureCode() {
        List<CommentDto> comments = commentService.findCommentsByFeatureCode("IDEA-1", 0, 10);
        assertThat(comments).isNotEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownFeatureCode() {
        List<CommentDto> comments = commentService.findCommentsByFeatureCode("UNKNOWN", 0, 10);
        assertThat(comments).isEmpty();
    }

    @Test
    void shouldRespectPagination() {
        List<CommentDto> page = commentService.findCommentsByFeatureCode("IDEA-1", 0, 1);
        assertThat(page).hasSize(1);
    }
}
