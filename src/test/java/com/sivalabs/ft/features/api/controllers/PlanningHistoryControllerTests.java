package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class PlanningHistoryControllerTests extends AbstractIT {

    @Test
    void shouldGetAllPlanningHistory() {
        var result = mvc.get().uri("/api/planning-history").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(6));
    }

    @Test
    void shouldFilterPlanningHistoryByEntityType() {
        var result = mvc.get().uri("/api/planning-history?entityType=FEATURE").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(4));
    }

    @Test
    void shouldFilterPlanningHistoryByEntityCode() {
        var result = mvc.get().uri("/api/planning-history?entityCode=IDEA-1").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(3));
    }

    @Test
    void shouldFilterPlanningHistoryByChangeType() {
        var result = mvc.get().uri("/api/planning-history?changeType=CREATED").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(3));
    }

    @Test
    void shouldFilterPlanningHistoryByChangedBy() {
        var result = mvc.get().uri("/api/planning-history?changedBy=admin").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(2));
    }

    @Test
    void shouldReturnPagedResult() {
        var result = mvc.get().uri("/api/planning-history?page=0&size=2").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(2));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.size")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(2));
        assertThat(result).bodyJson().extractingPath("$.first").isEqualTo(true);
    }

    @Test
    void shouldReturnEmptyPageWhenNoResults() {
        var result = mvc.get()
                .uri("/api/planning-history?entityCode=NONEXISTENT-CODE")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(0));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content.size()")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(0));
    }

    @Test
    void shouldGetFeatureHistory() {
        var result = mvc.get().uri("/api/features/{code}/history", "IDEA-1").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(3));
    }

    @Test
    void shouldGetReleaseHistory() {
        var result =
                mvc.get().uri("/api/releases/{code}/history", "IDEA-2023.3.8").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(2));
    }

    @Test
    void shouldReturnEmptyHistoryForUnknownCode() {
        var result =
                mvc.get().uri("/api/features/{code}/history", "UNKNOWN-999").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isEqualTo(0));
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldTrackFeatureCreation() {
        var payload =
                """
            {
                "productCode": "intellij",
                "releaseCode": "IDEA-2023.3.8",
                "title": "History Test Feature",
                "description": "Feature for testing history tracking"
            }
            """;

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        var historyResult =
                mvc.get().uri("/api/features/{code}/history", featureCode).exchange();
        assertThat(historyResult).hasStatusOk();
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(1));
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.content[0].changeType")
                .isEqualTo("CREATED");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldTrackFeatureStatusChange() {
        var payload =
                """
            {
                "title": "Feature for status change",
                "status": "IN_PROGRESS"
            }
            """;

        mvc.put()
                .uri("/api/features/{code}", "IDEA-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        var historyResult = mvc.get()
                .uri("/api/planning-history?entityCode=IDEA-2&changeType=STATUS_CHANGED")
                .exchange();
        assertThat(historyResult).hasStatusOk();
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(1));
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldTrackFeatureDeletion() {
        mvc.delete().uri("/api/features/{code}", "IDEA-2").exchange();

        var historyResult = mvc.get()
                .uri("/api/planning-history?entityCode=IDEA-2&changeType=DELETED")
                .exchange();
        assertThat(historyResult).hasStatusOk();
        assertThat(historyResult)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(n -> assertThat(n).isGreaterThanOrEqualTo(1));
    }
}
