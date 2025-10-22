package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Feature;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeatureRepositoryTest extends AbstractIT {

    @Autowired
    FeatureRepository featureRepository;

    @Test
    void shouldFindFeaturesByReleaseCodeWithParentsUsingNativeQuery() {
        // This test directly calls the findByReleaseCodeWithParents method
        // which uses a native SQL query with recursive CTE
        // This test will FAIL until the method is implemented in FeatureRepository

        // Test with the 3-level hierarchy from test-data.sql:
        // IDEA-2023.3.8 -> IDEA-2024.2.3 -> IDEA-2024.2.4
        List<Feature> features = featureRepository.findByReleaseCodeWithParents("IDEA-2024.2.4");

        // Should return all 6 features from the entire hierarchy
        assertThat(features).hasSize(6);

        // Verify feature codes
        assertThat(features)
                .extracting(Feature::getCode)
                .containsExactlyInAnyOrder("IDEA-1", "IDEA-2", "IDEA-3", "IDEA-4", "IDEA-5", "IDEA-6");
    }
}
