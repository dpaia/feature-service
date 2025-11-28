package com.sivalabs.ft.features.messaging;

import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureEventListenerTest {

    @Mock
    private RabbitMQEventPublisher rabbitMQEventPublisher;

    private FeatureEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new FeatureEventListener(rabbitMQEventPublisher);
    }

    @Test
    void shouldRelayFeatureCreatedEventToRabbitMQ() {
        FeatureCreatedEvent event = new FeatureCreatedEvent(
                1L,
                "TEST-1",
                "Test Feature",
                "Test Description",
                FeatureStatus.NEW,
                "RELEASE-1",
                "user1",
                "creator",
                Instant.now());

        listener.handleFeatureCreatedEvent(event);

        verify(rabbitMQEventPublisher).publishFeatureCreatedEvent(event);
    }

    @Test
    void shouldRelayFeatureUpdatedEventToRabbitMQ() {
        FeatureUpdatedEvent event = new FeatureUpdatedEvent(
                1L,
                "TEST-1",
                "Updated Feature",
                "Updated Description",
                FeatureStatus.IN_PROGRESS,
                "RELEASE-1",
                "user1",
                "creator",
                Instant.now(),
                "updater",
                Instant.now());

        listener.handleFeatureUpdatedEvent(event);

        verify(rabbitMQEventPublisher).publishFeatureUpdatedEvent(event);
    }

    @Test
    void shouldRelayFeatureDeletedEventToRabbitMQ() {
        FeatureDeletedEvent event = new FeatureDeletedEvent(
                1L,
                "TEST-1",
                "Deleted Feature",
                "Deleted Description",
                FeatureStatus.RELEASED,
                "RELEASE-1",
                "user1",
                "creator",
                Instant.now(),
                "updater",
                Instant.now(),
                "deleter",
                Instant.now());

        listener.handleFeatureDeletedEvent(event);

        verify(rabbitMQEventPublisher).publishFeatureDeletedEvent(event);
    }

    @Test
    void shouldNotThrowExceptionWhenRabbitMQPublisherFails() {
        FeatureCreatedEvent event = new FeatureCreatedEvent(
                1L,
                "TEST-1",
                "Test Feature",
                "Test Description",
                FeatureStatus.NEW,
                "RELEASE-1",
                "user1",
                "creator",
                Instant.now());

        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitMQEventPublisher)
                .publishFeatureCreatedEvent(event);

        // When/Then - Should not throw exception, just log error
        listener.handleFeatureCreatedEvent(event);

        verify(rabbitMQEventPublisher).publishFeatureCreatedEvent(event);
    }
}
