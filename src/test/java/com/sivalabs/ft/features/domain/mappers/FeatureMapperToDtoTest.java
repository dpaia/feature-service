package com.sivalabs.ft.features.domain.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class FeatureMapperToDtoTest {
    private final FeatureMapper mapper = Mappers.getMapper(FeatureMapper.class);

    @Test
    void shouldMapReleaseCodeToDto() {
        var release = new Release();
        release.setCode("IDEA-2024.1");
        var feature = new Feature();
        feature.setId(1L);
        feature.setCode("IDEA-1");
        feature.setTitle("Title");
        feature.setDescription("Description");
        feature.setRelease(release);

        FeatureDto dto = mapper.toDto(feature);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.code()).isEqualTo("IDEA-1");
        assertThat(dto.releaseCode()).isEqualTo("IDEA-2024.1");
        assertThat(dto.isFavorite()).isFalse();
    }

    @Test
    void shouldMapNullReleaseToNullCode() {
        var feature = new Feature();
        feature.setCode("IDEA-1");
        feature.setTitle("Title");
        feature.setDescription("Description");
        feature.setRelease(null);

        FeatureDto dto = mapper.toDto(feature);

        assertThat(dto.releaseCode()).isNull();
        assertThat(dto.code()).isEqualTo("IDEA-1");
    }
}
