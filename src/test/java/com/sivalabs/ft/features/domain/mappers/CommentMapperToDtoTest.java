package com.sivalabs.ft.features.domain.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.domain.dtos.CommentDto;
import com.sivalabs.ft.features.domain.entities.Comment;
import com.sivalabs.ft.features.domain.entities.Feature;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CommentMapperToDtoTest {
    private final CommentMapper mapper = Mappers.getMapper(CommentMapper.class);

    @Test
    void shouldMapFeatureCodeToDto() {
        var feature = new Feature();
        feature.setCode("IDEA-1");
        var comment = new Comment();
        comment.setId(1L);
        comment.setFeature(feature);
        comment.setContent("Looks good");
        comment.setCreatedBy("user");

        CommentDto dto = mapper.toDto(comment);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.featureCode()).isEqualTo("IDEA-1");
        assertThat(dto.content()).isEqualTo("Looks good");
        assertThat(dto.createdBy()).isEqualTo("user");
    }

    @Test
    void shouldMapNullFeatureToNullCode() {
        var comment = new Comment();
        comment.setId(1L);
        comment.setFeature(null);
        comment.setContent("Looks good");
        comment.setCreatedBy("user");

        CommentDto dto = mapper.toDto(comment);

        assertThat(dto.featureCode()).isNull();
        assertThat(dto.content()).isEqualTo("Looks good");
        assertThat(dto.createdBy()).isEqualTo("user");
    }
}
