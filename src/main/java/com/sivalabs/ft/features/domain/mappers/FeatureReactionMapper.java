package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.FeatureReactionDto;
import com.sivalabs.ft.features.domain.entities.FeatureReaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FeatureReactionMapper {
    FeatureReactionMapper INSTANCE = Mappers.getMapper(FeatureReactionMapper.class);

    @Mapping(target = "featureCode", source = "feature.code")
    FeatureReactionDto toDto(FeatureReaction reaction);

    List<FeatureReactionDto> toDtoList(List<FeatureReaction> reactions);
}