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
import org.springframework.http.HttpStatus;

class AdminHealthMetricsTests extends AbstractIT {

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @Autowired
    private FeatureUsageRepository featureUsageRepository;

    private void insertErrorLog(
            String errorType, String errorMessage, String stackTrace, String eventPayload, String userId) {
        insertErrorLog(errorType, errorMessage, stackTrace, eventPayload, userId, Instant.now());
    }

    private void insertErrorLog(
            String errorType,
            String errorMessage,
            String stackTrace,
            String eventPayload,
            String userId,
            Instant timestamp) {
        ErrorLog errorLog = new ErrorLog();
        errorLog.setTimestamp(timestamp);
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
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant olderUsage = now.minus(2, ChronoUnit.HOURS);
        Instant newerUsage = now.minus(1, ChronoUnit.HOURS);
        insertFeatureUsage("user-a", ActionType.FEATURE_VIEWED, olderUsage);
        insertFeatureUsage("user-b", ActionType.FEATURE_VIEWED, newerUsage);

        insertErrorLog("PROCESSING_ERROR", "Error 1", null, null, "user1");
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
                .startsWith(newerUsage.toString().substring(0, 19));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.PROCESSING_ERROR")
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
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        insertFeatureUsage("u1", ActionType.FEATURE_VIEWED, now.minus(4, ChronoUnit.HOURS));
        insertFeatureUsage("u2", ActionType.FEATURE_VIEWED, now.minus(3, ChronoUnit.HOURS));
        insertFeatureUsage("u3", ActionType.FEATURE_VIEWED, now.minus(2, ChronoUnit.HOURS));
        Instant latestUsage = now.minus(1, ChronoUnit.HOURS);
        insertFeatureUsage("u4", ActionType.FEATURE_VIEWED, latestUsage);

        insertErrorLog("PROCESSING_ERROR", "Error 1", null, null, "user1");

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
        assertThat(result)
                .bodyJson()
                .extractingPath("$.lastEventTimestamp")
                .asString()
                .startsWith(latestUsage.toString().substring(0, 19));
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn400ForInvalidDateRangeOnHealthEndpoint() {
        Instant startDate = Instant.now();
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);

        var result = mvc.get()
                .uri("/api/admin/health?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn400WhenOnlyStartDateProvidedOnHealthEndpoint() {
        Instant startDate = Instant.now().minus(1, ChronoUnit.DAYS);
        var result =
                mvc.get().uri("/api/admin/health?startDate={start}", startDate).exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturn400WhenOnlyEndDateProvidedOnHealthEndpoint() {
        Instant endDate = Instant.now();
        var result = mvc.get().uri("/api/admin/health?endDate={end}", endDate).exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnHealthMetricsWithCustomDateRange() {
        Instant now = Instant.now();
        insertErrorLog("PROCESSING_ERROR", "Recent error", null, null, "user1", now.minus(1, ChronoUnit.HOURS));
        insertErrorLog("DATABASE_ERROR", "Old error", null, null, "user2", now.minus(10, ChronoUnit.DAYS));

        Instant startDate = now.minus(1, ChronoUnit.DAYS);
        Instant endDate = now.plus(1, ChronoUnit.HOURS);

        var result = mvc.get()
                .uri("/api/admin/health?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.PROCESSING_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.errorsByType").asMap().doesNotContainKey("DATABASE_ERROR");
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldGroupErrorsByType() {
        insertErrorLog("PROCESSING_ERROR", "Validation 1", null, null, "user1");
        insertErrorLog("PROCESSING_ERROR", "Validation 2", null, null, "user1");
        insertErrorLog("DATABASE_ERROR", "DB Error", null, null, "user2");
        insertErrorLog("UNKNOWN_ERROR", "Unknown", null, null, "user3");

        var result = mvc.get().uri("/api/admin/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .isEqualTo(4);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.PROCESSING_ERROR")
                .asNumber()
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.DATABASE_ERROR")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.UNKNOWN_ERROR")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldReturnAllSupportedErrorTypesInHealthMetrics() {
        insertErrorLog("PROCESSING_ERROR", "Validation", null, null, "user1");
        insertErrorLog("DATABASE_ERROR", "Database", null, null, "user2");
        insertErrorLog("PROCESSING_ERROR", "Processing", null, null, "user3");
        insertErrorLog("UNKNOWN_ERROR", "Unknown", null, null, "user4");

        var result = mvc.get().uri("/api/admin/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.failedEvents")
                .asNumber()
                .isEqualTo(4);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.PROCESSING_ERROR")
                .asNumber()
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errorsByType.UNKNOWN_ERROR")
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

    @Test
    @WithMockOAuth2User(
            username = "admin",
            roles = {"ADMIN"})
    void shouldCalculateLastEventTimestampWithinRequestedPeriod() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant outsidePeriod = now.minus(10, ChronoUnit.DAYS);
        Instant insidePeriodOlder = now.minus(2, ChronoUnit.DAYS);
        Instant insidePeriodNewer = now.minus(1, ChronoUnit.HOURS);

        insertFeatureUsage("u-outside", ActionType.FEATURE_VIEWED, outsidePeriod);
        insertFeatureUsage("u-inside-1", ActionType.FEATURE_VIEWED, insidePeriodOlder);
        insertFeatureUsage("u-inside-2", ActionType.FEATURE_VIEWED, insidePeriodNewer);

        Instant startDate = now.minus(3, ChronoUnit.DAYS);
        Instant endDate = now;

        var result = mvc.get()
                .uri("/api/admin/health?startDate={start}&endDate={end}", startDate, endDate)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.lastEventTimestamp")
                .asString()
                .startsWith(insidePeriodNewer.toString().substring(0, 19));
    }
}
