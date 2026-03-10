package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/test-data.sql"})
class ReleaseServiceTest {

    @Autowired
    private ReleaseService releaseService;

    // ---- Status transition validation unit tests ----

    @Test
    void draftCanOnlyTransitionToPlannedOrCancelled() {
        assertThat(ReleaseStatus.DRAFT.canTransitionTo(ReleaseStatus.PLANNED)).isTrue();
        assertThat(ReleaseStatus.DRAFT.canTransitionTo(ReleaseStatus.CANCELLED)).isTrue();
        assertThat(ReleaseStatus.DRAFT.canTransitionTo(ReleaseStatus.DRAFT)).isTrue();

        assertThat(ReleaseStatus.DRAFT.canTransitionTo(ReleaseStatus.IN_PROGRESS))
                .isFalse();
        assertThat(ReleaseStatus.DRAFT.canTransitionTo(ReleaseStatus.COMPLETED)).isFalse();
        assertThat(ReleaseStatus.DRAFT.canTransitionTo(ReleaseStatus.RELEASED)).isFalse();
    }

    @Test
    void plannedCanTransitionToInProgress() {
        assertThat(ReleaseStatus.PLANNED.canTransitionTo(ReleaseStatus.IN_PROGRESS))
                .isTrue();
    }

    @Test
    void plannedCanTransitionToDelayed() {
        assertThat(ReleaseStatus.PLANNED.canTransitionTo(ReleaseStatus.DELAYED)).isTrue();
    }

    @Test
    void plannedCanTransitionToCancelled() {
        assertThat(ReleaseStatus.PLANNED.canTransitionTo(ReleaseStatus.CANCELLED))
                .isTrue();
    }

    @Test
    void plannedCannotTransitionToCompleted() {
        assertThat(ReleaseStatus.PLANNED.canTransitionTo(ReleaseStatus.COMPLETED))
                .isFalse();
    }

    @Test
    void plannedCannotTransitionToReleased() {
        assertThat(ReleaseStatus.PLANNED.canTransitionTo(ReleaseStatus.RELEASED))
                .isFalse();
    }

    @Test
    void inProgressCanTransitionToCompleted() {
        assertThat(ReleaseStatus.IN_PROGRESS.canTransitionTo(ReleaseStatus.COMPLETED))
                .isTrue();
    }

    @Test
    void inProgressCanTransitionToDelayed() {
        assertThat(ReleaseStatus.IN_PROGRESS.canTransitionTo(ReleaseStatus.DELAYED))
                .isTrue();
    }

    @Test
    void inProgressCanTransitionToCancelled() {
        assertThat(ReleaseStatus.IN_PROGRESS.canTransitionTo(ReleaseStatus.CANCELLED))
                .isTrue();
    }

    @Test
    void inProgressCanTransitionToPlanned() {
        assertThat(ReleaseStatus.IN_PROGRESS.canTransitionTo(ReleaseStatus.PLANNED))
                .isTrue();
    }

    @Test
    void completedCanTransitionToReleased() {
        assertThat(ReleaseStatus.COMPLETED.canTransitionTo(ReleaseStatus.RELEASED))
                .isTrue();
    }

    @Test
    void completedCannotTransitionToInProgress() {
        assertThat(ReleaseStatus.COMPLETED.canTransitionTo(ReleaseStatus.IN_PROGRESS))
                .isFalse();
    }

    @Test
    void releasedIsTerminal() {
        for (ReleaseStatus target : ReleaseStatus.values()) {
            if (target == ReleaseStatus.RELEASED) continue;
            assertThat(ReleaseStatus.RELEASED.canTransitionTo(target))
                    .as("RELEASED should not be able to transition to " + target)
                    .isFalse();
        }
    }

    @Test
    void cancelledIsTerminal() {
        for (ReleaseStatus target : ReleaseStatus.values()) {
            if (target == ReleaseStatus.CANCELLED) continue;
            assertThat(ReleaseStatus.CANCELLED.canTransitionTo(target))
                    .as("CANCELLED should not be able to transition to " + target)
                    .isFalse();
        }
    }

    @Test
    void sameStatusTransitionIsAlwaysAllowed() {
        for (ReleaseStatus status : ReleaseStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("Self-transition for " + status + " should be allowed")
                    .isTrue();
        }
    }

    @Test
    void shouldThrowBadRequestForInvalidStatusTransition() {
        // IDEA-2023.3.8 is RELEASED in test data — cannot transition to IN_PROGRESS
        var cmd = new UpdateReleaseCommand(
                "IDEA-2023.3.8", "desc", ReleaseStatus.IN_PROGRESS, null, null, null, null, null, null, "user");

        assertThatThrownBy(() -> releaseService.updateRelease(cmd))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void shouldAllowValidStatusTransitionInService() {
        // IDEA-OVERDUE-1 is IN_PROGRESS — can transition to COMPLETED
        var cmd = new UpdateReleaseCommand(
                "IDEA-OVERDUE-1", "desc", ReleaseStatus.COMPLETED, null, null, null, null, null, null, "user");

        releaseService.updateRelease(cmd);

        var updated = releaseService.findReleaseByCode("IDEA-OVERDUE-1");
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(ReleaseStatus.COMPLETED);
    }

    @Test
    void shouldFindOverdueReleases() {
        var overdueReleases = releaseService.findOverdueReleases();
        assertThat(overdueReleases).isNotEmpty();
        assertThat(overdueReleases).anyMatch(r -> r.code().equals("IDEA-OVERDUE-1"));
    }

    @Test
    void shouldFindAtRiskReleases() {
        var atRiskReleases = releaseService.findAtRiskReleases(7);
        assertThat(atRiskReleases).isNotEmpty();
        assertThat(atRiskReleases).anyMatch(r -> r.code().equals("IDEA-AT-RISK-1"));
    }

    @Test
    void shouldFindReleasesByStatus() {
        var releasedReleases = releaseService.findReleasesByStatus(ReleaseStatus.RELEASED);
        assertThat(releasedReleases).isNotEmpty();
        assertThat(releasedReleases).allMatch(r -> r.status() == ReleaseStatus.RELEASED);
    }

    @Test
    void shouldFindReleasesByOwner() {
        var ownerReleases = releaseService.findReleasesByOwner("owner@example.com");
        assertThat(ownerReleases).isNotEmpty();
        assertThat(ownerReleases).anyMatch(r -> r.code().equals("GO-OWNED-1"));
    }

    @Test
    void shouldFindReleasesByDateRange() {
        var releases = releaseService.findReleasesByDateRange(
                java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.Instant.parse("2028-01-01T00:00:00Z"));
        assertThat(releases).isNotEmpty();
        assertThat(releases).anyMatch(r -> r.code().equals("GO-OWNED-1"));
    }

    @Test
    void shouldFindReleasesWithPagination() {
        var result = releaseService.findReleases(null, null, null, null, null, 0, 2);
        assertThat(result.data()).hasSize(2);
        assertThat(result.pageSize()).isEqualTo(2);
        assertThat(result.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldFindReleasesFilteredByProductCode() {
        var result = releaseService.findReleases("intellij", null, null, null, null, 0, 20);
        assertThat(result.data()).isNotEmpty();
        // All releases should belong to intellij product (IDEA- prefix)
        assertThat(result.data()).allMatch(r -> r.code().startsWith("IDEA-"));
    }

    @Test
    void shouldCreateAndLogRelease() {
        var cmd = new CreateReleaseCommand(
                "intellij", "IDEA-SERVICE-TEST", "Test Release", null, null, null, null, "user");
        String code = releaseService.createRelease(cmd);
        assertThat(code).isEqualTo("IDEA-SERVICE-TEST");

        var found = releaseService.findReleaseByCode("IDEA-SERVICE-TEST");
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ReleaseStatus.DRAFT);
    }
}
