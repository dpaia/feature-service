package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class FeatureUsageRepositoryTest extends AbstractIT {

    @Autowired
    private FeatureUsageRepository featureUsageRepository;

    @BeforeEach
    void setUp() {
        featureUsageRepository.deleteAll();
    }

    @Test
    void shouldSaveAndRetrieveFeatureUsage() {
        // Given
        FeatureUsage usage = createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED);

        // When
        FeatureUsage saved = featureUsageRepository.save(usage);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo("user1");
        assertThat(saved.getFeatureCode()).isEqualTo("FEAT-001");
        assertThat(saved.getProductCode()).isEqualTo("PROD-001");
        assertThat(saved.getActionType()).isEqualTo(ActionType.FEATURE_VIEWED);
    }

    @Test
    void shouldFindByUserId() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-002", "PROD-001", ActionType.FEATURE_CREATED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-003", "PROD-002", ActionType.FEATURE_VIEWED));

        // When
        Page<FeatureUsage> result = featureUsageRepository.findByUserId("user1", PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(u -> u.getUserId().equals("user1"));
    }

    @Test
    void shouldFindByFeatureCode() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-001", "PROD-001", ActionType.FEATURE_UPDATED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-002", "PROD-001", ActionType.FEATURE_VIEWED));

        // When
        Page<FeatureUsage> result = featureUsageRepository.findByFeatureCode("FEAT-001", PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(u -> u.getFeatureCode().equals("FEAT-001"));
    }

    @Test
    void shouldFindByProductCode() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-002", "PROD-001", ActionType.FEATURE_CREATED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-003", "PROD-002", ActionType.FEATURE_VIEWED));

        // When
        Page<FeatureUsage> result = featureUsageRepository.findByProductCode("PROD-001", PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(u -> u.getProductCode().equals("PROD-001"));
    }

    @Test
    void shouldFindByActionType() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-002", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-003", "PROD-002", ActionType.FEATURE_CREATED));

        // When
        Page<FeatureUsage> result =
                featureUsageRepository.findByActionType(ActionType.FEATURE_VIEWED, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(u -> u.getActionType() == ActionType.FEATURE_VIEWED);
    }

    @Test
    void shouldFindByTimestampBetween() {
        // Given
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);

        FeatureUsage recent = createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED);
        recent.setTimestamp(now.minus(30, ChronoUnit.MINUTES));
        featureUsageRepository.save(recent);

        FeatureUsage old = createFeatureUsage("user2", "FEAT-002", "PROD-001", ActionType.FEATURE_CREATED);
        old.setTimestamp(twoHoursAgo);
        featureUsageRepository.save(old);

        // When
        Page<FeatureUsage> result =
                featureUsageRepository.findByTimestampBetween(oneHourAgo, now, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFeatureCode()).isEqualTo("FEAT-001");
    }

    @Test
    void shouldFindByFilters() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-002", "PROD-001", ActionType.FEATURE_CREATED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-001", "PROD-002", ActionType.FEATURE_VIEWED));

        // When
        Page<FeatureUsage> result = featureUsageRepository.findByFilters(
                "user1", null, "PROD-001", null, null, null, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .allMatch(
                        u -> u.getUserId().equals("user1") && u.getProductCode().equals("PROD-001"));
    }

    @Test
    void shouldFindTopFeatures() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user3", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-002", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-002", "PROD-001", ActionType.FEATURE_VIEWED));

        // When
        List<Object[]> topFeatures = featureUsageRepository.findTopFeatures(null, null);

        // Then
        assertThat(topFeatures).isNotEmpty();
        assertThat(topFeatures.get(0)[0]).isEqualTo("FEAT-001");
        assertThat(((Number) topFeatures.get(0)[1]).longValue()).isEqualTo(3L);
    }

    @Test
    void shouldFindTopUsers() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-002", "PROD-001", ActionType.FEATURE_CREATED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-003", "PROD-001", ActionType.FEATURE_UPDATED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-002", "PROD-001", ActionType.FEATURE_VIEWED));

        // When
        List<Object[]> topUsers = featureUsageRepository.findTopUsers(null, null);

        // Then
        assertThat(topUsers).isNotEmpty();
        assertThat(topUsers.get(0)[0]).isEqualTo("user1");
        assertThat(((Number) topUsers.get(0)[1]).longValue()).isEqualTo(3L);
    }

    @Test
    void shouldFindActionTypeStats() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-002", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-003", "PROD-001", ActionType.FEATURE_CREATED));

        // When
        List<Object[]> stats = featureUsageRepository.findActionTypeStats(null, null);

        // Then
        assertThat(stats).hasSize(2);
        assertThat(stats.get(0)[0]).isEqualTo(ActionType.FEATURE_VIEWED);
        assertThat(((Number) stats.get(0)[1]).longValue()).isEqualTo(2L);
    }

    @Test
    void shouldCountByUserId() {
        // Given
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED));
        featureUsageRepository.save(createFeatureUsage("user1", "FEAT-002", "PROD-001", ActionType.FEATURE_CREATED));
        featureUsageRepository.save(createFeatureUsage("user2", "FEAT-003", "PROD-002", ActionType.FEATURE_VIEWED));

        // When
        long count = featureUsageRepository.countByUserId("user1");

        // Then
        assertThat(count).isEqualTo(2L);
    }

    private FeatureUsage createFeatureUsage(
            String userId, String featureCode, String productCode, ActionType actionType) {
        FeatureUsage usage = new FeatureUsage();
        usage.setUserId(userId);
        usage.setFeatureCode(featureCode);
        usage.setProductCode(productCode);
        usage.setActionType(actionType);
        usage.setTimestamp(Instant.now());
        return usage;
    }
}
