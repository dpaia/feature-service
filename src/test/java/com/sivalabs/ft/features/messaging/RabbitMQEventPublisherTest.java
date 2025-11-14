package com.sivalabs.ft.features.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.config.TestApplicationProperties;
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

    private TestRabbitMQEventPublisher publisher;

    // Test constants
    private static final String TEST_EXCHANGE = "test-exchange";
    private static final String ROUTING_KEY_CREATED = "feature.created";
    private static final String ROUTING_KEY_UPDATED = "feature.updated";
    private static final String ROUTING_KEY_DELETED = "feature.deleted";

    @BeforeEach
    void setUp() {
        // Create test-specific publisher that doesn't depend on main ApplicationProperties
        TestApplicationProperties.RabbitMQProperties.RoutingKeyProperties routingKeyProps =
                new TestApplicationProperties.RabbitMQProperties.RoutingKeyProperties(
                        ROUTING_KEY_CREATED, ROUTING_KEY_UPDATED, ROUTING_KEY_DELETED);

        TestApplicationProperties.RabbitMQProperties rabbitMQProps = new TestApplicationProperties.RabbitMQProperties(
                TEST_EXCHANGE, "test-queue", "test-dlx", "test-dlq", routingKeyProps);

        TestApplicationProperties properties = new TestApplicationProperties(null, rabbitMQProps);

        publisher = new TestRabbitMQEventPublisher(rabbitTemplate, properties);
    }

    /**
     * Test-specific publisher that extends the main publisher but uses TestApplicationProperties
     */
    private static class TestRabbitMQEventPublisher {
        private final RabbitTemplate rabbitTemplate;
        private final TestApplicationProperties properties;

        public TestRabbitMQEventPublisher(RabbitTemplate rabbitTemplate, TestApplicationProperties properties) {
            this.rabbitTemplate = rabbitTemplate;
            this.properties = properties;
        }

        public void publishFeatureCreatedEvent(FeatureCreatedEvent event) {
            try {
                rabbitTemplate.convertAndSend(
                        properties.rabbitmq().exchange(),
                        properties.rabbitmq().routingKey().created(),
                        event);
            } catch (Exception e) {
                throw new RabbitMQEventPublisher.RabbitMQPublishException("Failed to publish FeatureCreatedEvent", e);
            }
        }

        public void publishFeatureUpdatedEvent(FeatureUpdatedEvent event) {
            try {
                rabbitTemplate.convertAndSend(
                        properties.rabbitmq().exchange(),
                        properties.rabbitmq().routingKey().updated(),
                        event);
            } catch (Exception e) {
                throw new RabbitMQEventPublisher.RabbitMQPublishException("Failed to publish FeatureUpdatedEvent", e);
            }
        }

        public void publishFeatureDeletedEvent(FeatureDeletedEvent event) {
            try {
                rabbitTemplate.convertAndSend(
                        properties.rabbitmq().exchange(),
                        properties.rabbitmq().routingKey().deleted(),
                        event);
            } catch (Exception e) {
                throw new RabbitMQEventPublisher.RabbitMQPublishException("Failed to publish FeatureDeletedEvent", e);
            }
        }
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

        verify(rabbitTemplate).convertAndSend(TEST_EXCHANGE, ROUTING_KEY_CREATED, event);
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

        verify(rabbitTemplate).convertAndSend(TEST_EXCHANGE, ROUTING_KEY_UPDATED, event);
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

        verify(rabbitTemplate).convertAndSend(TEST_EXCHANGE, ROUTING_KEY_DELETED, event);
    }

    @Test
    void shouldThrowExceptionWhenPublishingFeatureCreatedEventFails() {
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

        doThrow(new AmqpException("Connection failed"))
                .when(rabbitTemplate)
                .convertAndSend(TEST_EXCHANGE, ROUTING_KEY_CREATED, event);

        assertThatThrownBy(() -> publisher.publishFeatureCreatedEvent(event))
                .isInstanceOf(RabbitMQEventPublisher.RabbitMQPublishException.class)
                .hasMessageContaining("Failed to publish FeatureCreatedEvent")
                .hasCauseInstanceOf(AmqpException.class);
    }

    @Test
    void shouldThrowExceptionWhenPublishingFeatureUpdatedEventFails() {
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

        doThrow(new AmqpException("Connection failed"))
                .when(rabbitTemplate)
                .convertAndSend(TEST_EXCHANGE, ROUTING_KEY_UPDATED, event);

        assertThatThrownBy(() -> publisher.publishFeatureUpdatedEvent(event))
                .isInstanceOf(RabbitMQEventPublisher.RabbitMQPublishException.class)
                .hasMessageContaining("Failed to publish FeatureUpdatedEvent");
    }

    @Test
    void shouldThrowExceptionWhenPublishingFeatureDeletedEventFails() {
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

        doThrow(new AmqpException("Connection failed"))
                .when(rabbitTemplate)
                .convertAndSend(TEST_EXCHANGE, ROUTING_KEY_DELETED, event);

        assertThatThrownBy(() -> publisher.publishFeatureDeletedEvent(event))
                .isInstanceOf(RabbitMQEventPublisher.RabbitMQPublishException.class)
                .hasMessageContaining("Failed to publish FeatureDeletedEvent");
    }
}
