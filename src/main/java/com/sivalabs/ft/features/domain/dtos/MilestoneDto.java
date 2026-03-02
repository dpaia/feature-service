package com.sivalabs.ft.features.domain.dtos;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneDto {
    private Long id;
    private String code;
    private String releaseCode;
    private LocalDate targetDate;
    private int completedFeatures;
    private int totalFeatures;
}
