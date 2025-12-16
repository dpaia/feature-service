package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.FeatureUsageRepository;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@WithMockOAuth2User
class FeatureUsageControllerTest extends AbstractIT {

    @Autowired
    private FeatureUsageRepository featureUsageRepository;

    @Test
    void shouldCreateUsageEventViaPostEndpoint() {
        var requestBody =
                """
                {
                    "actionType": "FEATURE_VIEWED",
                    "featureCode": "FEAT-001",
                    "productCode": "PROD-001",
                    "context": {
                        "source": "web",
                        "device": "desktop"
                    }
                }
                """;

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();

        assertThat(result).bodyJson().extractingPath("$.featureCode").asString().isEqualTo("FEAT-001");

        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.actionType").asString().isEqualTo("FEATURE_VIEWED");
    }

    @Test
    void shouldCreateUsageEventAndReturnLocationHeader() {
        var requestBody =
                """
                {
                    "actionType": "FEATURE_VIEWED",
                    "featureCode": "FEAT-001"
                }
                """;

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
        String location = result.getMvcResult().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
    }

    @Test
    void shouldAutoCaptureUserIdAndTimestamp() {
        var requestBody =
                """
                {
                    "actionType": "FEATURE_VIEWED",
                    "featureCode": "FEAT-001"
                }
                """;

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result).bodyJson().extractingPath("$.userId").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
    }

    @Test
    void shouldCreateUsageEventWithMinimalData() {
        var requestBody =
                """
                {
                    "actionType": "FEATURE_CREATED"
                }
                """;

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result).bodyJson().extractingPath("$.actionType").asString().isEqualTo("FEATURE_CREATED");
    }

    @Test
    void shouldRejectPostUsageEventWithoutActionType() {
        var requestBody =
                """
                {
                    "featureCode": "FEAT-001",
                    "productCode": "PROD-001"
                }
                """;

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldRejectPostUsageEventWithInvalidActionType() {
        var requestBody =
                """
                {
                    "actionType": "INVALID_ACTION",
                    "featureCode": "FEAT-001"
                }
                """;

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldRejectPostUsageEventWithMalformedJson() {
        var requestBody =
                """
                {
                    "actionType": "FEATURE_VIEWED",
                    "featureCode": "FEAT-001"
                """;

        var result = mvc.post()
                .uri("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldGetFeatureUsageEventsWithActionTypeFilter() {
        var result = mvc.get()
                .uri("/api/usage/feature/IDEA-1/events?actionType=FEATURE_VIEWED")
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$")
                .isNotNull();
    }

    @Test
    void shouldGetFeatureUsageEventsWithDateRangeFilter() {
        Instant startDate = Instant.now().minusSeconds(7200);
        Instant endDate = Instant.now();

        var result = mvc.get()
                .uri("/api/usage/feature/IDEA-1/events?startDate=" + startDate.toString() + "&endDate="
                        + endDate.toString())
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    void shouldGetProductUsageEventsWithActionTypeFilter() {
        var result = mvc.get()
                .uri("/api/usage/product/intellij/events?actionType=FEATURE_VIEWED")
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$")
                .isNotNull();
    }

    @Test
    void shouldGetProductUsageEventsWithDateRangeFilter() {
        Instant startDate = Instant.now().minusSeconds(7200);
        Instant endDate = Instant.now();

        var result = mvc.get()
                .uri("/api/usage/product/intellij/events?startDate=" + startDate.toString() + "&endDate="
                        + endDate.toString())
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    void shouldGetFeatureStatsWithActionTypeFilter() {
        var result = mvc.get()
                .uri("/api/usage/feature/IDEA-1/stats?actionType=FEATURE_VIEWED")
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.totalUsageCount")
                .asNumber()
                .satisfies(count -> assertThat(count.intValue()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void shouldGetProductStatsWithActionTypeFilter() {
        var result = mvc.get()
                .uri("/api/usage/product/intellij/stats?actionType=FEATURE_VIEWED")
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.totalUsageCount")
                .asNumber()
                .satisfies(count -> assertThat(count.intValue()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void shouldHandleInvalidDateFormatInFeatureStats() {
        var result = mvc.get()
                .uri("/api/usage/feature/FEAT-001/stats?startDate=invalid-date")
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldHandleInvalidDateFormatInProductStats() {
        var result = mvc.get()
                .uri("/api/usage/product/PROD-001/stats?endDate=invalid-date")
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldReturnEmptyStatsForNonExistentFeature() {
        var result = mvc.get().uri("/api/usage/feature/NON-EXISTENT/stats").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.totalUsageCount")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    void shouldReturnEmptyStatsForNonExistentProduct() {
        var result = mvc.get().uri("/api/usage/product/NON-EXISTENT/stats").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.totalUsageCount")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    void shouldGetFeatureStatsWithoutFilters() {
        var result = mvc.get().uri("/api/usage/feature/IDEA-1/stats").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.featureCode")
                .asString()
                .isEqualTo("IDEA-1");
    }

    @Test
    void shouldGetProductStatsWithoutFilters() {
        var result = mvc.get().uri("/api/usage/product/intellij/stats").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.productCode")
                .asString()
                .isEqualTo("intellij");
    }

    @Test
    void shouldGetFeatureStatsWithDateRangeFilter() {
        Instant startDate = Instant.now().minusSeconds(7200);
        Instant endDate = Instant.now();

        var result = mvc.get()
                .uri("/api/usage/feature/IDEA-1/stats?startDate=" + startDate.toString() + "&endDate="
                        + endDate.toString())
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    void shouldGetProductStatsWithDateRangeFilter() {
        Instant startDate = Instant.now().minusSeconds(7200);
        Instant endDate = Instant.now();

        var result = mvc.get()
                .uri("/api/usage/product/intellij/stats?startDate=" + startDate.toString() + "&endDate="
                        + endDate.toString())
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    void shouldReturnEmptyListForNonExistentFeatureEvents() {
        var result = mvc.get().uri("/api/usage/feature/NON-EXISTENT/events").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    void shouldReturnEmptyListForNonExistentProductEvents() {
        var result = mvc.get().uri("/api/usage/product/NON-EXISTENT/events").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(0);
    }

    @Test
    void shouldGetAllUsageEvents() {
        var result = mvc.get().uri("/api/usage/events").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .satisfies(size -> assertThat(size.intValue()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void shouldGetAllUsageEventsWithActionTypeFilter() {
        var result =
                mvc.get().uri("/api/usage/events?actionType=FEATURE_VIEWED").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$")
                .isNotNull();
    }

    @Test
    void shouldGetAllUsageEventsWithUserIdFilter() {
        var result = mvc.get().uri("/api/usage/events?userId=user1").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$")
                .isNotNull();
    }

    @Test
    void shouldGetAllUsageEventsWithFeatureCodeFilter() {
        var result = mvc.get().uri("/api/usage/events?featureCode=IDEA-1").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$")
                .isNotNull();
    }

    @Test
    void shouldGetAllUsageEventsWithProductCodeFilter() {
        var result = mvc.get().uri("/api/usage/events?productCode=intellij").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$")
                .isNotNull();
    }

    @Test
    void shouldGetAllUsageEventsWithDateRangeFilter() {
        Instant startDate = Instant.now().minusSeconds(7200);
        Instant endDate = Instant.now();

        var result = mvc.get()
                .uri("/api/usage/events?startDate=" + startDate.toString() + "&endDate=" + endDate.toString())
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    void shouldGetAllUsageEventsWithMultipleFilters() {
        var result = mvc.get()
                .uri("/api/usage/events?actionType=FEATURE_VIEWED&featureCode=IDEA-1&productCode=intellij")
                .exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$")
                .isNotNull();
    }

    @Test
    void shouldHandleInvalidDateFormatInAllUsageEvents() {
        var result = mvc.get().uri("/api/usage/events?startDate=invalid-date").exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldGetOverallUsageStats() {
        var result = mvc.get().uri("/api/usage/stats").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.totalUsageCount")
                .asNumber()
                .satisfies(count -> assertThat(count.intValue()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void shouldGetOverallUsageStatsWithActionTypeFilter() {
        var result = mvc.get().uri("/api/usage/stats?actionType=FEATURE_VIEWED").exchange();

        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.totalUsageCount")
                .asNumber()
                .satisfies(count -> assertThat(count.intValue()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void shouldGetOverallUsageStatsWithDateRangeFilter() {
        Instant startDate = Instant.now().minusSeconds(7200);
        Instant endDate = Instant.now();

        var result = mvc.get()
                .uri("/api/usage/stats?startDate=" + startDate.toString() + "&endDate=" + endDate.toString())
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
    }

    @Test
    void shouldHandleInvalidDateFormatInOverallStats() {
        var result = mvc.get().uri("/api/usage/stats?endDate=invalid-date").exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldGetAllUsageEventsAsList() {
        for (int i = 0; i < 25; i++) {
            createFeatureUsage("user" + i, "FEAT-" + i, "PROD-001", ActionType.FEATURE_VIEWED);
        }

        var result = mvc.get().uri("/api/usage/events").exchange();
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .satisfies(size -> assertThat(size.intValue()).isGreaterThanOrEqualTo(25));
    }

    @Test
    void shouldHandleInvalidDateRangeInAllUsageEvents() {
        Instant startDate = Instant.now();
        Instant endDate = Instant.now().minusSeconds(3600);

        var result = mvc.get()
                .uri("/api/usage/events?startDate=" + startDate.toString() + "&endDate=" + endDate.toString())
                .exchange();

        assertThat(result).hasStatus4xxClientError();
    }

    private FeatureUsage createFeatureUsage(
            String userId, String featureCode, String productCode, ActionType actionType) {
        FeatureUsage usage = new FeatureUsage();
        usage.setUserId(userId);
        usage.setFeatureCode(featureCode);
        usage.setProductCode(productCode);
        usage.setActionType(actionType);
        usage.setTimestamp(Instant.now());
        return featureUsageRepository.save(usage);
    }
}
