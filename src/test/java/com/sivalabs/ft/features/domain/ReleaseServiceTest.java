package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceTest {

    @Mock
    private ReleaseRepository releaseRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReleaseService releaseService;

    @Test
    void shouldPropagateExceptionWhenEventDetailsSerializationFails() throws Exception {
        // Given - a release in IN_PROGRESS state with one feature
        Feature feature = new Feature();
        feature.setCreatedBy("developer");

        Release release = new Release();
        release.setCode("IDEA-1.0");
        release.setStatus(ReleaseStatus.IN_PROGRESS);
        release.setDescription("Test release");
        release.getFeatures().add(feature);

        when(releaseRepository.findByCode("IDEA-1.0")).thenReturn(Optional.of(release));

        // Simulate JSON serialization failure when building cascade notification payload
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("simulated serialization failure") {});

        UpdateReleaseCommand cmd =
                new UpdateReleaseCommand("IDEA-1.0", "desc", ReleaseStatus.RELEASED, null, "manager");

        // When / Then - the exception must propagate so @Transactional can roll back
        assertThatThrownBy(() -> releaseService.updateRelease(cmd))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated serialization failure");
    }
}
