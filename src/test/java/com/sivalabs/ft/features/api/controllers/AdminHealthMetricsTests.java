package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.ErrorLogRepository;
import com.sivalabs.ft.features.domain.FeatureUsageRepository;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ActionType;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminHealthMetricsTests extends AbstractIT {

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @Autowired
    private FeatureUsageRepository featureUsageRepository;

    private void insertErrorLog(
            String errorType, String errorMessage, String stackTrace, String eventPayload, String userId) {
        ErrorLog errorLog = new ErrorLog();
        errorLog.setTimestamp(Instant.now());
        errorLog.setErrorType(ErrorType.valueOf(errorType));
        errorLog.setErrorMessage(errorMessage);
        errorLog.setStackTrace(stackTrace);
        errorLog.setEventPayload(eventPayload);
        errorLog.setUserId(userId);
        errorLog.setResolved(false);
        errorLogRepository.save(errorLog);
    }

    private void insertFeatureUsage(String userId, ActionType actionType, Instant timestamp) {
        FeatureUsage featureUsage = new FeatureUsage();
        featureUsage.setUserId(userId);
        featureUsage.setActionType(actionType);
        featureUsage.setTimestamp(timestamp);
        featureUsage.setFeatureCode("TEST-FEATURE");
        featureUsage.setProductCode("test-product");
        featureUsageRepository.save(featureUsage);
    }

    @BeforeEach
    void setUp() {
        errorLogRepository.deleteAll();
        featureUsageRepository.deleteAll();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnExpectedHealthMetricsPayload() {
        Instant older = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant newer = Instant.now().minus(1, ChronoUnit.HOURS);
        insertFeatureUsage("u1", ActionType.FEATURE_VIEWED, older);
        insertFeatureUsage("u2", ActionType.FEATURE_VIEWED, newer);
        insertErrorLog("VALIDATION_ERROR", "Error 1", null, null, "user1");
        insertErrorLog("DATABASE_ERROR", "Error 2", null, null, "user2");

        long successfulEvents = 2;
        long failedEvents = 2;
        long totalEvents = successfulEvents + failedEvents;
        double expectedSuccessRate = (successfulEvents * 100.0) / totalEvents;
        double expectedErrorRate = (failedEvents * 100.0) / totalEvents;

        var result = mvc.get().uri("/api/admin/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.totalEvents").asNumber().satisfies(count -> {
            assertThat(count.longValue()).isEqualTo(totalEvents);
        });
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .satisfies(count -> {
                    assertThat(count.longValue()).isEqualTo(failedEvents);
                });
        assertThat(result).bodyJson().extractingPath("$.successRate").asNumber().satisfies(rate -> {
            assertThat(rate.doubleValue()).isCloseTo(expectedSuccessRate, org.assertj.core.data.Offset.offset(0.001));
        });
        assertThat(result).bodyJson().extractingPath("$.errorRate").asNumber().satisfies(rate -> {
            assertThat(rate.doubleValue()).isCloseTo(expectedErrorRate, org.assertj.core.data.Offset.offset(0.001));
        });
        assertThat(result)
                .bodyJson()
                .extractingPath("$.lastEventTimestamp")
                .asString()
                .startsWith(newer.toString().substring(0, 19));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.VALIDATION_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.DATABASE_ERROR")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldCalculateSuccessRateCorrectly() {
        insertFeatureUsage("u1", ActionType.FEATURE_VIEWED, Instant.now().minus(10, ChronoUnit.MINUTES));
        insertFeatureUsage("u2", ActionType.FEATURE_VIEWED, Instant.now().minus(9, ChronoUnit.MINUTES));
        insertFeatureUsage("u3", ActionType.FEATURE_VIEWED, Instant.now().minus(8, ChronoUnit.MINUTES));
        insertFeatureUsage("u4", ActionType.FEATURE_VIEWED, Instant.now().minus(7, ChronoUnit.MINUTES));
        insertErrorLog("VALIDATION_ERROR", "Error 1", null, null, "user1");

        long successfulEvents = 4;
        long failedEvents = 1;
        long totalEvents = successfulEvents + failedEvents;
        double expectedErrorRate = (failedEvents * 100.0) / totalEvents;
        double expectedSuccessRate = (successfulEvents * 100.0) / totalEvents;

        var result = mvc.get().uri("/api/admin/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.totalEvents").asNumber().satisfies(count -> {
            assertThat(count.longValue()).isEqualTo(totalEvents);
        });
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .satisfies(count -> {
                    assertThat(count.longValue()).isEqualTo(failedEvents);
                });
        assertThat(result).bodyJson().extractingPath("$.successRate").asNumber().satisfies(rate -> {
            assertThat(rate.doubleValue()).isCloseTo(expectedSuccessRate, org.assertj.core.data.Offset.offset(0.001));
        });
        assertThat(result).bodyJson().extractingPath("$.errorRate").asNumber().satisfies(rate -> {
            assertThat(rate.doubleValue()).isCloseTo(expectedErrorRate, org.assertj.core.data.Offset.offset(0.001));
        });
        assertThat(result).bodyJson().extractingPath("$.lastEventTimestamp").isNotNull();
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnMostRecentLastEventTimestamp() {
        Instant older = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant newer = Instant.now().minus(1, ChronoUnit.HOURS);
        insertFeatureUsage("u1", ActionType.FEATURE_VIEWED, older);
        insertFeatureUsage("u2", ActionType.FEATURE_VIEWED, newer);

        var result = mvc.get().uri("/api/admin/health").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.lastEventTimestamp")
                .asString()
                .startsWith(newer.toString().substring(0, 19));
    }
}
