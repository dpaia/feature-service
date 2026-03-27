package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.PlanningHealthResponse;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
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
    void shouldCalculateFullPlanningHealthDataForOneRelease() {
        setupOneReleaseData();

        var result = mvc.get().uri("/api/planning/health").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningHealthResponse.class)
                .satisfies(response -> {
                    assertThat(response.releasesByStatus()).isNotNull();
                    assertThat(response.releasesByStatus().size()).isEqualTo(4);

                    assertThat(response.releasesByStatus().get(ReleaseStatus.IN_PROGRESS.name()))
                            .isEqualTo(1);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.DRAFT.name()))
                            .isEqualTo(0);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.RELEASED.name()))
                            .isEqualTo(0);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.CANCELLED.name()))
                            .isEqualTo(0);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCalculateFullPlanningHealthDataForNoReleaseData() {
        var result = mvc.get().uri("/api/planning/health").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningHealthResponse.class)
                .satisfies(response -> {
                    assertThat(response.releasesByStatus()).isNotNull();
                    assertThat(response.releasesByStatus().size()).isEqualTo(4);

                    assertThat(response.releasesByStatus().get(ReleaseStatus.IN_PROGRESS.name()))
                            .isEqualTo(0);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.DRAFT.name()))
                            .isEqualTo(0);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.RELEASED.name()))
                            .isEqualTo(0);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.CANCELLED.name()))
                            .isEqualTo(0);
                });
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldCalculateFullPlanningHealthDataForManyStatusesData() {
        setupAllPossibleReleaseData();

        var result = mvc.get().uri("/api/planning/health").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .convertTo(PlanningHealthResponse.class)
                .satisfies(response -> {
                    assertThat(response.releasesByStatus()).isNotNull();
                    assertThat(response.releasesByStatus().size()).isEqualTo(4);

                    assertThat(response.releasesByStatus().get(ReleaseStatus.IN_PROGRESS.name()))
                            .isEqualTo(1);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.DRAFT.name()))
                            .isEqualTo(2);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.RELEASED.name()))
                            .isEqualTo(2);
                    assertThat(response.releasesByStatus().get(ReleaseStatus.CANCELLED.name()))
                            .isEqualTo(2);
                });
    }

    private void setupOneReleaseData() {
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00')
                        """);
    }

    private void setupAllPossibleReleaseData() {
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
                        (1, 1, 'IDEA-2023.3.1', 'IntelliJ IDEA 2023.3.1', 'RELEASED', 'admin', '2023-03-25 00:00:00'),
                        (2, 1, 'IDEA-2023.3.2', 'IntelliJ IDEA 2023.3.2', 'DRAFT', 'admin', '2023-03-25 00:00:00'),
                        (3, 1, 'IDEA-2023.3.3', 'IntelliJ IDEA 2023.3.3', 'RELEASED', 'admin', '2023-03-25 00:00:00'),
                        (4, 1, 'IDEA-2023.3.4', 'IntelliJ IDEA 2023.3.4', 'CANCELLED', 'admin', '2023-03-25 00:00:00'),
                        (5, 1, 'IDEA-2023.3.5', 'IntelliJ IDEA 2023.3.5', 'CANCELLED', 'admin', '2023-03-25 00:00:00'),
                        (6, 1, 'IDEA-2023.3.6', 'IntelliJ IDEA 2023.3.6', 'DRAFT', 'admin', '2023-03-25 00:00:00'),
                        (7, 1, 'IDEA-2023.3.7', 'IntelliJ IDEA 2023.3.7', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00')
                        """);
    }
}
