package com.sivalabs.ft.features.domain.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sivalabs.ft.features.DatabaseConfiguration;
import com.sivalabs.ft.features.integration.EventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(DatabaseConfiguration.class)
@TestPropertySource("classpath:application-test.properties")
class FeatureServiceTest {

    @Autowired
    private FeatureService featureService;

    @MockitoBean
    private EventPublisher eventPublisher;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(eventPublisher);
    }

    @Test
    void shouldGetFeaturesByReleaseCode() {
        List<Feature> features = featureService.findFeatures("IJ-2023.3.8");
        assertThat(features).hasSize(3);
    }

    @Test
    void shouldGetFeatureByCode() {
        Optional<Feature> feature = featureService.findFeatureByCode("IJ-10001");
        assertThat(feature.isPresent()).isTrue();
        assertThat(feature.get().getCode()).isEqualTo("IJ-10001");
    }

    @Test
    void shouldCreateNewFeature() {
        CreateFeatureCommand request = new CreateFeatureCommand(
                "intellij",
                "IJ-2023.3.TEST",
                "IJ-999998",
                "New Feature",
                "New feature description",
                "john.doe",
                "jane.doe");
        ArgumentCaptor<Feature> eventCaptor = ArgumentCaptor.forClass(Feature.class);
        Long featureId = featureService.createFeature(request);
        verify(eventPublisher, VerificationModeFactory.only()).publishFeatureCreatedEvent(eventCaptor.capture());
        assertThat(featureId).isNotNull();
        assertThat(featureId).isGreaterThan(3L);
    }

    @Test
    void shouldUpdateFeature() {
        UpdateFeatureCommand request = new UpdateFeatureCommand(
                "IJ-10001",
                "Updated Feature",
                "Updated description",
                FeatureStatus.IN_DEVELOPMENT,
                "jane.doe",
                "john.doe");
        ArgumentCaptor<Feature> eventCaptor = ArgumentCaptor.forClass(Feature.class);
        featureService.updateFeature(request);
        verify(eventPublisher, VerificationModeFactory.only()).publishFeatureUpdatedEvent(eventCaptor.capture());
        Feature updatedFeature = eventCaptor.getValue();
        assertThat(updatedFeature.getTitle()).isEqualTo("Updated Feature");
        assertThat(updatedFeature.getDescription()).isEqualTo("Updated description");
        assertThat(updatedFeature.getStatus()).isEqualTo(FeatureStatus.IN_DEVELOPMENT);
        assertThat(updatedFeature.getAssignedTo()).isEqualTo("jane.doe");
        assertThat(updatedFeature.getUpdatedBy()).isEqualTo("john.doe");
    }

    @Test
    void shouldDeleteFeature() {
        String featureCode = "IJ-10003";
        DeleteFeatureCommand cmd = new DeleteFeatureCommand(featureCode, "john.doe");
        ArgumentCaptor<Feature> featureCaptor = ArgumentCaptor.forClass(Feature.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);

        featureService.deleteFeature(cmd);

        verify(eventPublisher, VerificationModeFactory.only())
                .publishFeatureDeletedEvent(featureCaptor.capture(), userCaptor.capture(), timeCaptor.capture());
        Feature deletedFeature = featureCaptor.getValue();
        assertThat(deletedFeature.getCode()).isEqualTo(featureCode);
        assertThat(userCaptor.getValue()).isEqualTo("john.doe");
        assertThat(timeCaptor.getValue()).isNotNull();
    }
}
