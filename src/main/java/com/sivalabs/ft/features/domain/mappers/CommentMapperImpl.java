package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.CommentDto;
import com.sivalabs.ft.features.domain.entities.Comment;

public class CommentMapperImpl implements CommentMapper {
    @Override
    public CommentDto toDto(Comment comment) {
        if (comment == null) {
            return null;
        }

        var feature = comment.getFeature();
        String featureCode = feature != null ? feature.getCode() : null;

        return new CommentDto(comment.getId(), featureCode, comment.getContent(), comment.getCreatedBy());
    }
}
