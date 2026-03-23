package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.CommentDto;
import com.sivalabs.ft.features.domain.entities.Comment;

public interface CommentMapper {
    CommentDto toDto(Comment comment);
}
