package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface MilestoneMapper {

    @Mapping(target = "productCode", source = "product.code")
    @Mapping(target = "progress", source = ".", qualifiedByName = "calculateProgress")
    @Mapping(target = "releases", source = "releases", qualifiedByName = "mapReleases")
    MilestoneDto toDto(Milestone milestone);

    @Named("calculateProgress")
    default Integer calculateProgress(Milestone milestone) {
        if (milestone.getReleases() == null || milestone.getReleases().isEmpty()) {
            return 0;
        }

        long totalReleases = milestone.getReleases().size();
        long releasedCount = milestone.getReleases().stream()
                .mapToLong(release -> release.getStatus() == ReleaseStatus.RELEASED ? 1 : 0)
                .sum();

        return (int) ((releasedCount * 100) / totalReleases);
    }

    @Named("mapReleases")
    default List<ReleaseDto> mapReleases(java.util.Set<Release> releases) {
        if (releases == null) {
            return List.of();
        }
        return releases.stream().map(this::mapRelease).toList();
    }

    default ReleaseDto mapRelease(Release release) {
        return new ReleaseDto(
                release.getId(),
                release.getCode(),
                release.getDescription(),
                release.getStatus(),
                release.getReleasedAt(),
                release.getMilestone() != null ? release.getMilestone().getCode() : null,
                release.getCreatedBy(),
                release.getCreatedAt(),
                release.getUpdatedBy(),
                release.getUpdatedAt());
    }
}
