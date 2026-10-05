package com.sivalabs.ft.features.domain.dtos;

import java.time.Instant;
import java.util.List;

public record CommentDto(
        Long id,
        String featureCode,
        String content,
        String createdBy,
        Instant createdAt,
        Long parentCommentId,
        int depth,
        List<CommentDto> replies) {}
