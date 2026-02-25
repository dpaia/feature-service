package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeaturePlanningStatusTransitionTest {

    private FeatureService featureService;

    @BeforeEach
    void setUp() {
        featureService = new FeatureService(
                mock(FavoriteFeatureService.class),
                mock(ReleaseRepository.class),
                mock(FeatureRepository.class),
                mock(ProductRepository.class),
                mock(FavoriteFeatureRepository.class),
                mock(com.sivalabs.ft.features.domain.events.EventPublisher.class),
                mock(com.sivalabs.ft.features.domain.mappers.FeatureMapper.class));
    }

    @Test
    void nullCurrentStatusAllowsAnyTransition() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(null, FeaturePlanningStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
        assertThatCode(() -> featureService.validatePlanningStatusTransition(null, FeaturePlanningStatus.DONE))
                .doesNotThrowAnyException();
        assertThatCode(() -> featureService.validatePlanningStatusTransition(null, FeaturePlanningStatus.BLOCKED))
                .doesNotThrowAnyException();
        assertThatCode(() -> featureService.validatePlanningStatusTransition(null, FeaturePlanningStatus.NOT_STARTED))
                .doesNotThrowAnyException();
    }

    @Test
    void sameStatusTransitionIsAllowed() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.NOT_STARTED, FeaturePlanningStatus.NOT_STARTED))
                .doesNotThrowAnyException();
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.DONE, FeaturePlanningStatus.DONE))
                .doesNotThrowAnyException();
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.BLOCKED, FeaturePlanningStatus.BLOCKED))
                .doesNotThrowAnyException();
    }

    // NOT_STARTED valid transitions
    @Test
    void notStartedToInProgressIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.NOT_STARTED, FeaturePlanningStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void notStartedToBlockedIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.NOT_STARTED, FeaturePlanningStatus.BLOCKED))
                .doesNotThrowAnyException();
    }

    @Test
    void notStartedToDoneIsInvalid() {
        assertThatThrownBy(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.NOT_STARTED, FeaturePlanningStatus.DONE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("NOT_STARTED")
                .hasMessageContaining("DONE");
    }

    // IN_PROGRESS valid transitions
    @Test
    void inProgressToDoneIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.DONE))
                .doesNotThrowAnyException();
    }

    @Test
    void inProgressToBlockedIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.BLOCKED))
                .doesNotThrowAnyException();
    }

    @Test
    void inProgressToNotStartedIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.NOT_STARTED))
                .doesNotThrowAnyException();
    }

    // BLOCKED valid transitions
    @Test
    void blockedToInProgressIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.BLOCKED, FeaturePlanningStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void blockedToNotStartedIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.BLOCKED, FeaturePlanningStatus.NOT_STARTED))
                .doesNotThrowAnyException();
    }

    @Test
    void blockedToDoneIsInvalid() {
        assertThatThrownBy(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.BLOCKED, FeaturePlanningStatus.DONE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BLOCKED")
                .hasMessageContaining("DONE");
    }

    // DONE valid transitions
    @Test
    void doneToNotStartedIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.DONE, FeaturePlanningStatus.NOT_STARTED))
                .doesNotThrowAnyException();
    }

    @Test
    void doneToInProgressIsValid() {
        assertThatCode(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.DONE, FeaturePlanningStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void doneToBlockedIsInvalid() {
        assertThatThrownBy(() -> featureService.validatePlanningStatusTransition(
                        FeaturePlanningStatus.DONE, FeaturePlanningStatus.BLOCKED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DONE")
                .hasMessageContaining("BLOCKED");
    }
}
