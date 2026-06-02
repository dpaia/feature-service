package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.EmailDeliveryFailureDto;
import com.sivalabs.ft.features.domain.entities.EmailDeliveryFailure;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmailDeliveryFailureMapper {
    EmailDeliveryFailureDto toDto(EmailDeliveryFailure failure);

    EmailDeliveryFailure toEntity(EmailDeliveryFailureDto dto);
}
