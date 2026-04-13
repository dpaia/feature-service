package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Feature;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class FeatureRepositoryUnsetReleaseTest extends AbstractIT {
    @Autowired
    FeatureRepository featureRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void shouldUnsetReleaseForMatchingFeatures() {
        var before = featureRepository.findByReleaseCode("IDEA-2023.3.8");
        assertThat(before).extracting(Feature::getCode).containsExactlyInAnyOrder("IDEA-1", "IDEA-2");

        featureRepository.unsetRelease("IDEA-2023.3.8");
        entityManager.clear();

        var features = featureRepository.findByReleaseCode("IDEA-2023.3.8");
        assertThat(features).isEmpty();
        assertThat(featureRepository.findByCode("IDEA-1").orElseThrow().getRelease())
                .isNull();
        assertThat(featureRepository.findByCode("IDEA-2").orElseThrow().getRelease())
                .isNull();
    }
}
