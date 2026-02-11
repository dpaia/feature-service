package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ErrorLogDto;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ErrorLogMapper {
    ErrorLogDto toDto(ErrorLog errorLog);
}
