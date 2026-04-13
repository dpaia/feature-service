package com.sivalabs.ft.features.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class EventPublisherPublishFeatureCreatedEventTest {
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    ApplicationProperties properties;

    @InjectMocks
    EventPublisher publisher;

    @Test
    void shouldSendFeatureCreatedEventToKafka() {
        Instant createdAt = Instant.parse("2024-03-14T10:15:30Z");
        var events = mock(ApplicationProperties.EventsProperties.class);
        when(properties.events()).thenReturn(events);
        when(events.newFeatures()).thenReturn("new-features-topic");
        var release = mock(Release.class);
        when(release.getCode()).thenReturn("IDEA-2024.1");
        var feature = mock(Feature.class);
        when(feature.getId()).thenReturn(1L);
        when(feature.getCode()).thenReturn("IDEA-1");
        when(feature.getTitle()).thenReturn("Title");
        when(feature.getDescription()).thenReturn("Description");
        when(feature.getStatus()).thenReturn(FeatureStatus.NEW);
        when(feature.getRelease()).thenReturn(release);
        when(feature.getAssignedTo()).thenReturn("user1");
        when(feature.getCreatedBy()).thenReturn("admin");
        when(feature.getCreatedAt()).thenReturn(createdAt);

        publisher.publishFeatureCreatedEvent(feature);

        var captor = ArgumentCaptor.forClass(FeatureCreatedEvent.class);
        verify(kafkaTemplate).send(eq("new-features-topic"), captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(1L);
        assertThat(captor.getValue().code()).isEqualTo("IDEA-1");
        assertThat(captor.getValue().title()).isEqualTo("Title");
        assertThat(captor.getValue().description()).isEqualTo("Description");
        assertThat(captor.getValue().status()).isEqualTo(FeatureStatus.NEW);
        assertThat(captor.getValue().releaseCode()).isEqualTo("IDEA-2024.1");
        assertThat(captor.getValue().assignedTo()).isEqualTo("user1");
        assertThat(captor.getValue().createdBy()).isEqualTo("admin");
        assertThat(captor.getValue().createdAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldSendFeatureCreatedEventWithNullReleaseCode() {
        var events = mock(ApplicationProperties.EventsProperties.class);
        when(properties.events()).thenReturn(events);
        when(events.newFeatures()).thenReturn("new-features-topic");
        var feature = mock(Feature.class);
        when(feature.getId()).thenReturn(2L);
        when(feature.getCode()).thenReturn("IDEA-2");
        when(feature.getRelease()).thenReturn(null);

        publisher.publishFeatureCreatedEvent(feature);

        var captor = ArgumentCaptor.forClass(FeatureCreatedEvent.class);
        verify(kafkaTemplate).send(eq("new-features-topic"), captor.capture());
        assertThat(captor.getValue().releaseCode()).isNull();
    }
}
