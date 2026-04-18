package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.TagDto;
import com.sivalabs.ft.features.domain.entities.Tag;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TagMapper {
    TagDto toDto(Tag tag);
}
