package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import org.springframework.stereotype.Component;

@Component
public class MilestoneMapper {
    public MilestoneDto toDto(Milestone milestone) {
        return MilestoneDto.builder()
                .id(milestone.getId())
                .code(milestone.getCode())
                .releaseCode(milestone.getReleaseCode())
                .targetDate(milestone.getTargetDate())
                .completedFeatures(milestone.getCompletedFeatures())
                .totalFeatures(milestone.getTotalFeatures())
                .build();
    }
}
