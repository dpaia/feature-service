package com.sivalabs.ft.features.messaging;

import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.config.RabbitMQProperties;
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
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitMQEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitMQErrorHandler errorHandler;

    private RabbitMQEventPublisher publisher;

    // Test constants
    private static final String TEST_EXCHANGE = "test-exchange";
    private static final String ROUTING_KEY_CREATED = "feature.created";
    private static final String ROUTING_KEY_UPDATED = "feature.updated";
    private static final String ROUTING_KEY_DELETED = "feature.deleted";

    @BeforeEach
    void setUp() {
        RabbitMQProperties.Exchange.RoutingKeys routingKeys = new RabbitMQProperties.Exchange.RoutingKeys(
                ROUTING_KEY_CREATED, ROUTING_KEY_UPDATED, ROUTING_KEY_DELETED);

        RabbitMQProperties.Exchange exchange =
                new RabbitMQProperties.Exchange(TEST_EXCHANGE, "topic", true, routingKeys);

        RabbitMQProperties.Retry retry = new RabbitMQProperties.Retry(3, 1000, 2.0, 10000);

        RabbitMQProperties.DeadLetter deadLetter = new RabbitMQProperties.DeadLetter("test-dlx", "test-dlq", "#");

        RabbitMQProperties properties =
                new RabbitMQProperties("localhost", 5672, "guest", "guest", "/", exchange, retry, deadLetter);

        publisher = new RabbitMQEventPublisher(rabbitTemplate, properties, errorHandler);
    }

    @Test
    void shouldPublishFeatureCreatedEventSuccessfully() {
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

        publisher.publishFeatureCreatedEvent(event);

        verify(rabbitTemplate)
                .convertAndSend(
                        eq(TEST_EXCHANGE), eq(ROUTING_KEY_CREATED), any(RabbitMQEventPublisher.EventMessage.class));
    }

    @Test
    void shouldPublishFeatureUpdatedEventSuccessfully() {
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

        publisher.publishFeatureUpdatedEvent(event);

        verify(rabbitTemplate)
                .convertAndSend(
                        eq(TEST_EXCHANGE), eq(ROUTING_KEY_UPDATED), any(RabbitMQEventPublisher.EventMessage.class));
    }

    @Test
    void shouldPublishFeatureDeletedEventSuccessfully() {
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

        publisher.publishFeatureDeletedEvent(event);

        verify(rabbitTemplate)
                .convertAndSend(
                        eq(TEST_EXCHANGE), eq(ROUTING_KEY_DELETED), any(RabbitMQEventPublisher.EventMessage.class));
    }

    @Test
    void shouldCallErrorHandlerWhenPublishingFeatureCreatedEventFails() {
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

        AmqpException exception = new AmqpException("Connection failed");
        doThrow(exception)
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(TEST_EXCHANGE), eq(ROUTING_KEY_CREATED), any(RabbitMQEventPublisher.EventMessage.class));

        publisher.publishFeatureCreatedEvent(event);

        verify(errorHandler)
                .handlePublishingError(eq(event), eq(ROUTING_KEY_CREATED), eq("FeatureCreated"), eq(exception));
    }

    @Test
    void shouldCallErrorHandlerWhenPublishingFeatureUpdatedEventFails() {
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

        AmqpException exception = new AmqpException("Connection failed");
        doThrow(exception)
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(TEST_EXCHANGE), eq(ROUTING_KEY_UPDATED), any(RabbitMQEventPublisher.EventMessage.class));

        publisher.publishFeatureUpdatedEvent(event);

        verify(errorHandler)
                .handlePublishingError(eq(event), eq(ROUTING_KEY_UPDATED), eq("FeatureUpdated"), eq(exception));
    }

    @Test
    void shouldCallErrorHandlerWhenPublishingFeatureDeletedEventFails() {
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

        AmqpException exception = new AmqpException("Connection failed");
        doThrow(exception)
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(TEST_EXCHANGE), eq(ROUTING_KEY_DELETED), any(RabbitMQEventPublisher.EventMessage.class));

        publisher.publishFeatureDeletedEvent(event);

        verify(errorHandler)
                .handlePublishingError(eq(event), eq(ROUTING_KEY_DELETED), eq("FeatureDeleted"), eq(exception));
    }
}
