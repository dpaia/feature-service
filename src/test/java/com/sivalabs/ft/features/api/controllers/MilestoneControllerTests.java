package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MilestoneControllerTests extends AbstractIT {

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetMilestonesByProductCode() {
        var result =
                mvc.get().uri("/api/milestones?productCode={code}", "intellij").exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400WhenProductCodeMissing() {
        var result = mvc.get().uri("/api/milestones").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400WhenProductCodeMissingAndStatusProvided() {
        var result = mvc.get().uri("/api/milestones?status={status}", "PLANNED").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
