package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.dtos.MilestoneReleaseDto;
import com.sivalabs.ft.features.domain.dtos.MilestoneSummaryDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import com.sivalabs.ft.features.domain.entities.Release;
import java.util.Collection;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MilestoneMapper {
    @Mapping(target = "productCode", source = "product.code")
    @Mapping(target = "progress", ignore = true)
    @Mapping(target = "releases", ignore = true)
    MilestoneDto toDto(Milestone milestone);

    @Mapping(target = "productCode", source = "product.code")
    @Mapping(target = "progress", ignore = true)
    MilestoneSummaryDto toSummaryDto(Milestone milestone);

    MilestoneReleaseDto toReleaseDto(Release release);

    List<MilestoneReleaseDto> toReleaseDtoList(Collection<Release> releases);
}
