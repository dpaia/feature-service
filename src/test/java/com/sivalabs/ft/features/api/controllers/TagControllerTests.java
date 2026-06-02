package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;

class TagControllerTests extends AbstractIT {

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldSearchTagsByName() {
        var result = mvc.get().uri("/api/tags/search?name=bug").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(1);

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].name")
                .asString()
                .isEqualTo("bug");
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturnAllTagsWhenSearchNameIsMissing() {
        var result = mvc.get().uri("/api/tags/search").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .isEqualTo(4);
    }
}
