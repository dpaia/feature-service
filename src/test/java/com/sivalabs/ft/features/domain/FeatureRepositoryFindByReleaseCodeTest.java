package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Feature;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeatureRepositoryFindByReleaseCodeTest extends AbstractIT {
    @Autowired
    FeatureRepository featureRepository;

    @Test
    void shouldFindFeaturesByReleaseCode() {
        List<Feature> features = featureRepository.findByReleaseCode("IDEA-2023.3.8");
        assertThat(features).extracting(Feature::getCode).containsExactlyInAnyOrder("IDEA-1", "IDEA-2");
        assertThat(features)
                .extracting(feature -> feature.getRelease().getCode())
                .containsOnly("IDEA-2023.3.8");
    }

    @Test
    void shouldReturnEmptyForUnknownRelease() {
        List<Feature> features = featureRepository.findByReleaseCode("UNKNOWN");
        assertThat(features).isEmpty();
    }
}
