package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for verifying cache behavior in FeatureService.
 * <p>
 * These tests verify that:
 * 1. Multiple calls with the same parameters return the same results (cache hit)
 * 2. Calls with different parameters return different results (cache miss)
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class})
@Sql(scripts = {"/test-data.sql"})
@Transactional
class FeatureServiceCacheTest {

    @Autowired
    private FeatureService featureService;

    @Test
    void findByIdForRepeatedCalls() {
        Long featureId = 1L;
        Optional<Feature> feature1 = featureService.findById(featureId);
        Optional<Feature> feature2 = featureService.findById(featureId);

        // Verify both calls returned the same result
        assertThat(feature1).isPresent();
        assertThat(feature2).isPresent();
        assertThat(feature1).isEqualTo(feature2);
    }

    @Test
    void findByIdWithDifferentIds() {
        Long featureId1 = 1L;
        Optional<Feature> feature1 = featureService.findById(featureId1);
        Long featureId2 = 2L;
        Optional<Feature> feature2 = featureService.findById(featureId2);

        assertThat(feature1).isPresent();
        assertThat(feature2).isPresent();
        assertThat(feature1).isNotEqualTo(feature2);
    }

    @Test
    void findByStatusForRepeatedCalls() {
        FeatureStatus status = FeatureStatus.NEW;
        List<Feature> features1 = featureService.findByStatus(status);
        List<Feature> features2 = featureService.findByStatus(status);

        assertThat(features1).isEqualTo(features2);
    }

    @Test
    void findByStatusForDifferentStatuses() {
        FeatureStatus status1 = FeatureStatus.NEW;
        List<Feature> features1 = featureService.findByStatus(status1);
        FeatureStatus status2 = FeatureStatus.IN_PROGRESS;
        List<Feature> features2 = featureService.findByStatus(status2);

        assertThat(features1).isNotEqualTo(features2);
    }

    @Test
    void findByAssigneeForRepeatedCalls() {
        String assignee = "andreybelyaev";
        List<Feature> features1 = featureService.findByAssignee(assignee);
        List<Feature> features2 = featureService.findByAssignee(assignee);

        // Verify both calls returned the same result (cache hit)
        assertThat(features1).isEqualTo(features2);
    }

    @Test
    void findByAssigneeForDifferentAssignees() {
        String assignee1 = "siva";
        List<Feature> features1 = featureService.findByAssignee(assignee1);
        String assignee2 = "andreybelyaev";
        List<Feature> features2 = featureService.findByAssignee(assignee2);

        assertThat(features1).isNotEqualTo(features2);
    }
}