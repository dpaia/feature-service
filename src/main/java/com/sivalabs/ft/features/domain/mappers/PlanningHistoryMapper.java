package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.PlanningHistoryDto;
import com.sivalabs.ft.features.domain.entities.PlanningHistory;
import org.springframework.stereotype.Component;

@Component
public class PlanningHistoryMapper {

    public PlanningHistoryDto toDto(PlanningHistory entity) {
        if (entity == null) {
            return null;
        }

        return new PlanningHistoryDto(
                entity.getId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getEntityCode(),
                entity.getChangeType(),
                entity.getFieldName(),
                entity.getOldValue(),
                entity.getNewValue(),
                entity.getRationale(),
                entity.getChangedBy(),
                entity.getChangedAt());
    }

    public PlanningHistory toEntity(PlanningHistoryDto dto) {
        if (dto == null) {
            return null;
        }

        PlanningHistory entity = new PlanningHistory();
        entity.setId(dto.id());
        entity.setEntityType(dto.entityType());
        entity.setEntityId(dto.entityId());
        entity.setEntityCode(dto.entityCode());
        entity.setChangeType(dto.changeType());
        entity.setFieldName(dto.fieldName());
        entity.setOldValue(dto.oldValue());
        entity.setNewValue(dto.newValue());
        entity.setRationale(dto.rationale());
        entity.setChangedBy(dto.changedBy());
        entity.setChangedAt(dto.changedAt());

        return entity;
    }
}
