package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CapacityPlanningResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PlanningAnalyticsIntegrationTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM favorite_features");
        jdbcTemplate.execute("DELETE FROM comments");
        jdbcTemplate.execute("DELETE FROM features");
        jdbcTemplate.execute("DELETE FROM releases");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCalculateUtilizationRateForOneIsOverloadedResource() {
        setupOverloadedTestData();

        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    assertThat(capacity.overallCapacity()).isNotNull();
                    assertThat(capacity.overallCapacity().totalResources()).isEqualTo(2);
                    assertThat(capacity.overallCapacity().utilizationRate()).isEqualTo(100.0);
                    assertThat(capacity.overallCapacity().availableCapacity()).isEqualTo(0.0);
                    assertThat(capacity.overallCapacity().overallocatedResources())
                            .isEqualTo(1);
                    assertThat(capacity.workloadByOwner())
                            .hasSize(2)
                            .extracting("owner", "currentWorkload", "capacity")
                            .containsExactlyInAnyOrder(tuple("alice", 9, 10), tuple("carol", 11, 10));
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCalculateUtilizationRateForOneBusyAnotherAlmostFreeResource() {
        setupOneBusyAnotherAlmostFreeTestData();

        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    assertThat(capacity.overallCapacity()).isNotNull();
                    assertThat(capacity.overallCapacity().totalResources()).isEqualTo(2);
                    assertThat(capacity.overallCapacity().utilizationRate()).isEqualTo(55.0);
                    assertThat(capacity.overallCapacity().availableCapacity()).isEqualTo(45.0);
                    assertThat(capacity.overallCapacity().overallocatedResources())
                            .isEqualTo(0);
                    assertThat(capacity.workloadByOwner())
                            .hasSize(2)
                            .extracting("owner", "currentWorkload", "capacity")
                            .containsExactlyInAnyOrder(tuple("alice", 10, 10), tuple("carol", 1, 10));
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCalculateUtilizationRateForBothHalfBusy() {
        setupBothHalfBusyTestData();

        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    assertThat(capacity.overallCapacity()).isNotNull();
                    assertThat(capacity.overallCapacity().totalResources()).isEqualTo(2);
                    assertThat(capacity.overallCapacity().utilizationRate()).isEqualTo(50.0);
                    assertThat(capacity.overallCapacity().availableCapacity()).isEqualTo(50.0);
                    assertThat(capacity.overallCapacity().overallocatedResources())
                            .isEqualTo(0);
                    assertThat(capacity.workloadByOwner())
                            .hasSize(2)
                            .extracting("owner", "currentWorkload", "capacity")
                            .containsExactlyInAnyOrder(tuple("alice", 5, 10), tuple("carol", 5, 10));
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCalculateUtilizationRateForBothOverloaded() {
        setupBothOverloadedTestData();

        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    assertThat(capacity.overallCapacity()).isNotNull();
                    assertThat(capacity.overallCapacity().totalResources()).isEqualTo(2);
                    assertThat(capacity.overallCapacity().utilizationRate()).isEqualTo(120.0);
                    assertThat(capacity.overallCapacity().availableCapacity()).isEqualTo(-20.0);
                    assertThat(capacity.overallCapacity().overallocatedResources())
                            .isEqualTo(2);
                    assertThat(capacity.workloadByOwner())
                            .hasSize(2)
                            .extracting("owner", "currentWorkload", "capacity")
                            .containsExactlyInAnyOrder(tuple("alice", 12, 10), tuple("carol", 12, 10));
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleEmptyDatabase() {
        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    assertThat(capacity.overallCapacity()).isNotNull();
                    assertThat(capacity.overallCapacity().totalResources()).isEqualTo(0);
                    assertThat(capacity.overallCapacity().utilizationRate()).isEqualTo(0.0);
                    assertThat(capacity.overallCapacity().availableCapacity()).isEqualTo(100.0);
                    assertThat(capacity.overallCapacity().overallocatedResources())
                            .isEqualTo(0);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldHandleAllFeaturesWithNullOwner() {
        setupNullOwnerTestData();

        var result = mvc.get().uri("/api/planning/capacity").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(CapacityPlanningResponse.class)
                .satisfies(capacity -> {
                    assertThat(capacity.overallCapacity()).isNotNull();
                    assertThat(capacity.overallCapacity().totalResources()).isEqualTo(0);
                    assertThat(capacity.overallCapacity().utilizationRate()).isEqualTo(0.0);
                    assertThat(capacity.overallCapacity().availableCapacity()).isEqualTo(100.0);
                    assertThat(capacity.overallCapacity().overallocatedResources())
                            .isEqualTo(0);
                });
    }

    private void setupNullOwnerTestData() {
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00')
                        """);
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, priority) VALUES
                        (200, 1, 1, 'F-1', 'Feature 1', '...', 'IN_PROGRESS', 'admin', null, '2025-01-02 00:00:00', '2025-01-08 00:00:00', 'IN_PROGRESS', null, 'HIGH'),
                        (201, 1, 1, 'F-2', 'Feature 2', '...', 'IN_PROGRESS', 'admin', null, '2025-01-03 00:00:00', '2025-01-09 00:00:00', 'IN_PROGRESS', null, 'MEDIUM')
                        """);
    }

    private void setupOverloadedTestData() {
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00'),
                        (2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.3', 'IN_PROGRESS', 'admin', '2024-02-25 00:00:00')
                        """);
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, priority) VALUES
                        (200, 1, 1, 'F-1', 'Fast feature 1', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-02 00:00:00', '2025-01-08 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (201, 1, 1, 'F-2', 'Fast feature 2', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-03 00:00:00', '2025-01-09 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (202, 1, 1, 'F-3', 'Fast feature 3', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-04 00:00:00', '2025-01-12 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (203, 1, 1, 'F-4', 'Fast feature 4', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-05 00:00:00', '2025-01-15 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (204, 1, 1, 'F-5', 'Fast feature 5', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (205, 1, 1, 'F-6', 'Fast feature 6', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-07 00:00:00', '2025-01-14 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (206, 1, 1, 'F-7', 'Fast feature 7', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-08 00:00:00', '2025-01-14 01:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (207, 1, 1, 'F-8', 'Fast feature 8', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-09 00:00:00', '2025-01-14 02:00:00', 'DONE', 'alice', 'LOW'),
                        (208, 1, 2, 'F-9', 'Fast feature 9', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-10 00:00:00', '2025-01-14 03:00:00', 'DONE', 'alice', 'CRITICAL'),
                        (300, 2, 1, 'F-10','Fast feature 10','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (301, 2, 2, 'F-11','Fast feature 11','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 05:00:00', 'DONE', 'carol', 'HIGH'),
                        (302, 2, 1, 'F-12','Fast feature 12','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 06:00:00', 'DONE', 'carol', 'HIGH'),
                        (303, 2, 1, 'F-13','Fast feature 13','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 07:00:00', 'DONE', 'carol', 'HIGH'),
                        (304, 2, 1, 'F-14','Fast feature 14','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 08:00:00', 'DONE', 'carol', 'HIGH'),
                        (305, 2, 1, 'F-15','Fast feature 15','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 09:00:00', 'DONE', 'carol', 'HIGH'),
                        (306, 2, 1, 'F-16','Fast feature 16','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 10:00:00', 'DONE', 'carol', 'HIGH'),
                        (307, 2, 1, 'F-17','Fast feature 17','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 01:10:00', 'DONE', 'carol', 'HIGH'),
                        (308, 2, 1, 'F-18','Fast feature 18','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 02:20:00', 'DONE', 'carol', 'HIGH'),
                        (309, 2, 1, 'F-19','Fast feature 19','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 03:30:00', 'DONE', 'carol', 'HIGH'),
                        (310, 2, 1, 'F-20','Fast feature 20','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:40:00', 'DONE', 'carol', 'HIGH')
                        """);
    }

    private void setupOneBusyAnotherAlmostFreeTestData() {
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00'),
                        (2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.3', 'IN_PROGRESS', 'admin', '2024-02-25 00:00:00')
                        """);
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, priority) VALUES
                        (200, 1, 1, 'F-1', 'Fast feature 1', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-02 00:00:00', '2025-01-08 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (201, 1, 1, 'F-2', 'Fast feature 2', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-03 00:00:00', '2025-01-09 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (202, 1, 1, 'F-3', 'Fast feature 3', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-04 00:00:00', '2025-01-12 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (203, 1, 1, 'F-4', 'Fast feature 4', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-05 00:00:00', '2025-01-15 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (204, 1, 1, 'F-5', 'Fast feature 5', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (205, 1, 1, 'F-6', 'Fast feature 6', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-07 00:00:00', '2025-01-14 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (206, 1, 1, 'F-7', 'Fast feature 7', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-08 00:00:00', '2025-01-14 01:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (207, 1, 1, 'F-8', 'Fast feature 8', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-09 00:00:00', '2025-01-14 02:00:00', 'DONE', 'alice', 'LOW'),
                        (208, 1, 2, 'F-9', 'Fast feature 9', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-10 00:00:00', '2025-01-14 03:00:00', 'DONE', 'alice', 'CRITICAL'),
                        (209, 1, 2, 'F-10', 'Fast feature 9', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-10 00:00:00', '2025-01-14 03:00:00', 'DONE', 'alice', 'CRITICAL'),
                        (300, 2, 1, 'F-11','Fast feature 10','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH')
                        """);
    }

    private void setupBothHalfBusyTestData() {
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00'),
                        (2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.3', 'IN_PROGRESS', 'admin', '2024-02-25 00:00:00')
                        """);
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, priority) VALUES
                        (200, 1, 1, 'F-1', 'Fast feature 1', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-02 00:00:00', '2025-01-08 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (201, 1, 1, 'F-2', 'Fast feature 2', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-03 00:00:00', '2025-01-09 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (202, 1, 1, 'F-3', 'Fast feature 3', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-04 00:00:00', '2025-01-12 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (203, 1, 1, 'F-4', 'Fast feature 4', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-05 00:00:00', '2025-01-15 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (204, 1, 1, 'F-5', 'Fast feature 5', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (300, 2, 1, 'F-11','Fast feature 10','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (301, 2, 1, 'F-12','Fast feature 11','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (302, 2, 1, 'F-13','Fast feature 12','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (303, 2, 1, 'F-14','Fast feature 13','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (304, 2, 1, 'F-15','Fast feature 14','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH')
                        """);
    }

    private void setupBothOverloadedTestData() {
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00'),
                        (2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.3', 'IN_PROGRESS', 'admin', '2024-02-25 00:00:00')
                        """);
        jdbcTemplate.update(
                """
                        INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                            created_at, updated_at, planning_status, feature_owner, priority) VALUES
                        (200, 1, 1, 'F-1', 'Fast feature 1', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-02 00:00:00', '2025-01-08 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (201, 1, 1, 'F-2', 'Fast feature 2', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-03 00:00:00', '2025-01-09 00:00:00', 'DONE', 'alice', 'HIGH'),
                        (202, 1, 1, 'F-3', 'Fast feature 3', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-04 00:00:00', '2025-01-12 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (203, 1, 1, 'F-4', 'Fast feature 4', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-05 00:00:00', '2025-01-15 00:00:00', 'DONE', 'alice', 'MEDIUM'),
                        (204, 1, 1, 'F-5', 'Fast feature 5', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (205, 1, 1, 'F-6', 'Fast feature 6', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (206, 1, 1, 'F-7', 'Fast feature 7', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (207, 1, 1, 'F-8', 'Fast feature 8', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (208, 1, 1, 'F-9', 'Fast feature 9', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (209, 1, 1, 'F-10', 'Fast feature 10', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (210, 1, 1, 'F-11', 'Fast feature 11', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (211, 1, 1, 'F-12', 'Fast feature 12', '...', 'IN_PROGRESS', 'admin', 'alice', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'alice', 'LOW'),
                        (300, 2, 1, 'F-21','Fast feature 20','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (301, 2, 1, 'F-22','Fast feature 21','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (302, 2, 1, 'F-23','Fast feature 22','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (303, 2, 1, 'F-24','Fast feature 23','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (304, 2, 1, 'F-25','Fast feature 24','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (305, 2, 1, 'F-26','Fast feature 25','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (306, 2, 1, 'F-27','Fast feature 26','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (307, 2, 1, 'F-28','Fast feature 27','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (308, 2, 1, 'F-29','Fast feature 28','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (309, 2, 1, 'F-30','Fast feature 29','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (310, 2, 1, 'F-31','Fast feature 30','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH'),
                        (311, 2, 1, 'F-32','Fast feature 31','...', 'IN_PROGRESS', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 04:00:00', 'DONE', 'carol', 'HIGH')
                        """);
    }
}
