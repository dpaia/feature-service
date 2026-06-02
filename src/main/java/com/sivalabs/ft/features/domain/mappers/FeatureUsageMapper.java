package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.FeatureUsageDto;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import org.springframework.stereotype.Component;

@Component
public class FeatureUsageMapper {

    public FeatureUsageDto toDto(FeatureUsage entity) {
        if (entity == null) {
            return null;
        }

        return new FeatureUsageDto(
                entity.getId(),
                entity.getUserId(),
                entity.getFeatureCode(),
                entity.getProductCode(),
                entity.getReleaseCode(),
                entity.getActionType(),
                entity.getTimestamp(),
                entity.getContext(),
                null, // ipAddress - not stored in entity, would need to be passed separately
                null // userAgent - not stored in entity, would need to be passed separately
                );
    }
}
