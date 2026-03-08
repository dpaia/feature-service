package com.sivalabs.ft.features.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.FeatureService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class GlobalExceptionHandlerIntegrationTests extends AbstractIT {

    @MockitoBean
    private FeatureService featureService;

    @Test
    void shouldReturnInternalServerErrorForUnhandledException() {
        when(featureService.findReleaseFeaturesWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("unexpected"));

        var result = mvc.get()
                .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                .exchange();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Internal Server Error");
    }
}
