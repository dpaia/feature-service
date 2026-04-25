package com.sivalabs.ft.features.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FAIL-TO-PASS Integration Test for RabbitMQ event serialization.
 *
 * <p>This test demonstrates the FAIL-TO-PASS approach:
 * 1. FAIL: Without RabbitConfig - messages are sent as byte[], deserialization fails
 * 2. PASS: With RabbitConfig - Jackson serialization works correctly
 *
 * <p>This test verifies:
 * - JSON serialization using Jackson for RabbitMQ messages
 * - Proper message converter configuration
 * - End-to-end event flow through RabbitMQ
 */
@Import(RabbitMQSerializationIT.TestRabbitListenerConfig.class)
class RabbitMQSerializationIT extends AbstractIT {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQSerializationIT.class);
    private static final String CREATED_QUEUE = "feature-created-events";
    private static final String UPDATED_QUEUE = "feature-updated-events";
    private static final String DELETED_QUEUE = "feature-deleted-events";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRabbitListener testRabbitListener;

    private ListAppender<ILoggingEvent> listAppender;
    private ch.qos.logback.classic.Logger rabbitConfigLogger;

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        // RabbitMQ test queue configuration
        registry.add("spring.rabbitmq.listener.simple.acknowledge-mode", () -> "auto");
    }

    @BeforeEach
    void setUp() {
        testRabbitListener.clear();

        // Set up log appender to capture RabbitConfig error logs
        rabbitConfigLogger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(com.sivalabs.ft.features.config.RabbitConfig.class);

        if (listAppender != null) {
            listAppender.stop();
            rabbitConfigLogger.detachAppender(listAppender);
            listAppender.list.clear();
        }

        listAppender = new ListAppender<>();
        listAppender.start();
        rabbitConfigLogger.addAppender(listAppender);
        listAppender.list.clear();
    }

    @AfterEach
    void tearDown() {
        log.info("TEST: Teardown completed");
    }

    @Test
    @WithMockOAuth2User(username = "test-user")
    void shouldSerializeFeatureEventToRabbitMQ() {
        log.info("TEST: Starting RabbitMQ serialization test");

        // Create feature via API which will trigger RabbitMQ publish
        var createPayload =
                """
            {
                "productCode": "intellij",
                "releaseCode": "IDEA-2023.3.8",
                "title": "RabbitMQ Test Feature",
                "description": "Testing Jackson serialization with RabbitMQ",
                "assignedTo": "test.user"
            }
            """;

        log.info("TEST: Sending POST request to create feature");
        var response = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        assertThat(response).hasStatus(201);
        String location = response.getMvcResult().getResponse().getHeader("Location");
        log.info("TEST: Feature created at location: {}", location);

        // Manually send an event to RabbitMQ to test serialization
        FeatureCreatedEvent testEvent = new FeatureCreatedEvent(
                999L,
                "TEST-999",
                "RabbitMQ Test",
                "Testing serialization",
                FeatureStatus.NEW,
                "IDEA-2023.3.8",
                "test.user",
                "test-user",
                java.time.Instant.now());

        log.info("TEST: Sending test event to RabbitMQ queue: {}", CREATED_QUEUE);
        rabbitTemplate.convertAndSend(CREATED_QUEUE, testEvent);

        // Wait for message to be received and deserialized
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    assertThat(testRabbitListener.getReceivedEvents())
                            .as("Should receive and deserialize FeatureCreatedEvent")
                            .isNotEmpty();
                });

        List<FeatureCreatedEvent> events = testRabbitListener.getReceivedEvents();
        log.info("TEST: Received {} event(s) from RabbitMQ", events.size());

        FeatureCreatedEvent receivedEvent = events.get(0);
        log.info(
                "TEST: Event details - id: {}, code: {}, title: {}",
                receivedEvent.id(),
                receivedEvent.code(),
                receivedEvent.title());

        assertThat(receivedEvent)
                .as("Event should be properly serialized and deserialized")
                .satisfies(e -> {
                    assertThat(e.id()).isEqualTo(999L);
                    assertThat(e.code()).isEqualTo("TEST-999");
                    assertThat(e.title()).isEqualTo("RabbitMQ Test");
                    assertThat(e.description()).isEqualTo("Testing serialization");
                    assertThat(e.status()).isEqualTo(FeatureStatus.NEW);
                    assertThat(e.assignedTo()).isEqualTo("test.user");
                });

        log.info("TEST: RabbitMQ serialization test completed successfully");
    }

    @Test
    @WithMockOAuth2User(username = "test-user")
    void shouldSerializeFeatureUpdatedEventToRabbitMQ() {
        log.info("TEST: Testing FeatureUpdatedEvent serialization to RabbitMQ");

        FeatureUpdatedEvent updateEvent = new FeatureUpdatedEvent(
                888L,
                "TEST-888",
                "Updated Feature",
                "Updated description",
                FeatureStatus.IN_PROGRESS,
                "IDEA-2023.3.8",
                "updated.user",
                "original-user",
                java.time.Instant.now().minusSeconds(3600),
                "test-user",
                java.time.Instant.now());

        log.info("TEST: Sending FeatureUpdatedEvent to RabbitMQ queue: {}", UPDATED_QUEUE);
        rabbitTemplate.convertAndSend(UPDATED_QUEUE, updateEvent);

        // Wait for message
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(testRabbitListener.getReceivedUpdatedEvents()).isNotEmpty();
        });

        FeatureUpdatedEvent received =
                testRabbitListener.getReceivedUpdatedEvents().get(0);
        assertThat(received).satisfies(e -> {
            assertThat(e.id()).isEqualTo(888L);
            assertThat(e.code()).isEqualTo("TEST-888");
            assertThat(e.title()).isEqualTo("Updated Feature");
            assertThat(e.status()).isEqualTo(FeatureStatus.IN_PROGRESS);
            assertThat(e.updatedBy()).isEqualTo("test-user");
            assertThat(e.updatedAt()).isNotNull();
        });

        log.info("TEST: ✅ FeatureUpdatedEvent successfully serialized/deserialized");
    }

    @Test
    @WithMockOAuth2User(username = "test-user")
    void shouldSerializeFeatureDeletedEventToRabbitMQ() {
        log.info("TEST: Testing FeatureDeletedEvent serialization to RabbitMQ");

        FeatureDeletedEvent deleteEvent = new FeatureDeletedEvent(
                777L,
                "TEST-777",
                "Deleted Feature",
                "To be deleted",
                FeatureStatus.RELEASED,
                "IDEA-2023.3.8",
                "assigned.user",
                "creator-user",
                java.time.Instant.now().minusSeconds(7200),
                "updater-user",
                java.time.Instant.now().minusSeconds(3600),
                "test-user",
                java.time.Instant.now());

        log.info("TEST: Sending FeatureDeletedEvent to RabbitMQ queue: {}", DELETED_QUEUE);
        rabbitTemplate.convertAndSend(DELETED_QUEUE, deleteEvent);

        // Wait for message
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(testRabbitListener.getReceivedDeletedEvents()).isNotEmpty();
        });

        FeatureDeletedEvent received =
                testRabbitListener.getReceivedDeletedEvents().get(0);
        assertThat(received).satisfies(e -> {
            assertThat(e.id()).isEqualTo(777L);
            assertThat(e.code()).isEqualTo("TEST-777");
            assertThat(e.title()).isEqualTo("Deleted Feature");
            assertThat(e.status()).isEqualTo(FeatureStatus.RELEASED);
            assertThat(e.deletedBy()).isEqualTo("test-user");
            assertThat(e.deletedAt()).isNotNull();
        });

        log.info("TEST: ✅ FeatureDeletedEvent successfully serialized/deserialized");
    }

    @Test
    void shouldHandleBrokenJson() {
        log.info("TEST 1: Broken JSON (битый JSON) - Black-box test");

        String brokenJson = "{\"id\": 123, \"code\": \"TEST\", \"title";
        log.info("TEST: Sending broken JSON: {}", brokenJson);

        testRabbitListener.clear();
        sendRawMessage(brokenJson);

        verifyConsumerRecovery("broken JSON");
    }

    @Test
    void shouldHandleWrongTypeJson() {
        log.info("TEST 2: Wrong Type JSON (String instead of Long) - Black-box test");

        String wrongTypeJson =
                """
            {
                "id": "not-a-number-but-string",
                "code": "TEST-100",
                "title": "Test",
                "description": "Test",
                "status": "NEW",
                "releaseCode": null,
                "assignedTo": null,
                "createdBy": "user",
                "createdAt": "2024-01-01T10:00:00Z"
            }
            """;
        log.info("TEST: Sending JSON with wrong field type (id as String instead of Long)");

        testRabbitListener.clear();
        sendRawMessage(wrongTypeJson);

        verifyConsumerRecovery("wrong type JSON");
    }

    @Test
    void shouldDeserializeMissingFieldsAsNull() {
        log.info("TEST 3: Missing Fields → null (NOT an error) - Black-box test");

        testRabbitListener.clear();

        String missingFieldJson =
                """
            {
                "id": 300,
                "code": "TEST-300",
                "title": "Missing Fields Test",
                "description": "Test",
                "status": "NEW",
                "createdBy": "user",
                "createdAt": "2024-01-01T10:00:00Z"
            }
            """;
        log.info("TEST: Sending JSON with missing optional fields (releaseCode, assignedTo)");

        testRabbitListener.clear();
        sendRawMessage(missingFieldJson);

        // Wait for successful deserialization
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(testRabbitListener.getReceivedEvents())
                    .as("Missing optional fields should deserialize as null")
                    .isNotEmpty();
        });

        FeatureCreatedEvent event = testRabbitListener.getReceivedEvents().get(0);
        assertThat(event).satisfies(e -> {
            assertThat(e.code()).isEqualTo("TEST-300");
            assertThat(e.releaseCode()).as("Missing field → null").isNull();
            assertThat(e.assignedTo()).as("Missing field → null").isNull();
        });

        log.info("TEST: ✅ Missing fields correctly handled as null (no error)");
    }

    private void sendRawMessage(String jsonContent) {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        Message message = new Message(jsonContent.getBytes(), props);
        rabbitTemplate.send(CREATED_QUEUE, message);
    }

    /**
     * Black-box verification with log analysis:
     * 1. Verifies deserialization error was logged
     * 2. Verifies consumer recovered by processing valid message
     */
    private void verifyConsumerRecovery(String errorType) {
        log.info("TEST: Malformed {} sent - waiting for ERROR log", errorType);

        // Wait for deserialization error to be logged
        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    long errorLogs = listAppender.list.stream()
                            .filter(event -> event.getLevel() == Level.ERROR)
                            .filter(event -> event.getFormattedMessage().contains("RabbitMQ deserialization error"))
                            .count();
                    assertThat(errorLogs)
                            .as("ERROR log should be present for " + errorType)
                            .isGreaterThan(0);
                });

        log.info("TEST: ✅ Deserialization error logged for {}", errorType);

        // Black-box verification: Send valid message to verify consumer recovered
        FeatureCreatedEvent validEvent = new FeatureCreatedEvent(
                777L,
                "RECOVERY-TEST",
                "Recovery Test",
                "Testing recovery",
                FeatureStatus.NEW,
                null,
                null,
                "test-user",
                java.time.Instant.now());

        testRabbitListener.clear();
        rabbitTemplate.convertAndSend(CREATED_QUEUE, validEvent);

        // Consumer should process valid message after error
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(testRabbitListener.getReceivedEvents())
                    .as("Consumer should recover after " + errorType)
                    .isNotEmpty();
        });

        log.info("TEST: ✅ Consumer recovered and processed valid message after {}", errorType);
    }

    /**
     * Test configuration to register RabbitMQ listener.
     */
    @TestConfiguration
    static class TestRabbitListenerConfig {
        @Bean
        public TestRabbitListener testRabbitListener() {
            return new TestRabbitListener();
        }
    }

    /**
     * Test RabbitMQ listener to capture all event types.
     * Requires RabbitConfig with Jackson2JsonMessageConverter to work.
     */
    static class TestRabbitListener {
        private static final Logger log = LoggerFactory.getLogger(TestRabbitListener.class);
        private final List<FeatureCreatedEvent> createdEvents = new CopyOnWriteArrayList<>();
        private final List<FeatureUpdatedEvent> updatedEvents = new CopyOnWriteArrayList<>();
        private final List<FeatureDeletedEvent> deletedEvents = new CopyOnWriteArrayList<>();

        @RabbitListener(queues = CREATED_QUEUE)
        public void handleCreatedEvent(@Payload FeatureCreatedEvent event, Message message) {
            log.info("RABBIT LISTENER: Received FeatureCreatedEvent - code: {}", event.code());
            log.info(
                    "RABBIT LISTENER: Content-Type: {}",
                    message.getMessageProperties().getContentType());
            createdEvents.add(event);
        }

        @RabbitListener(queues = UPDATED_QUEUE)
        public void handleUpdatedEvent(@Payload FeatureUpdatedEvent event, Message message) {
            log.info("RABBIT LISTENER: Received FeatureUpdatedEvent - code: {}", event.code());
            log.info(
                    "RABBIT LISTENER: Content-Type: {}",
                    message.getMessageProperties().getContentType());
            updatedEvents.add(event);
        }

        @RabbitListener(queues = DELETED_QUEUE)
        public void handleDeletedEvent(@Payload FeatureDeletedEvent event, Message message) {
            log.info("RABBIT LISTENER: Received FeatureDeletedEvent - code: {}", event.code());
            log.info(
                    "RABBIT LISTENER: Content-Type: {}",
                    message.getMessageProperties().getContentType());
            deletedEvents.add(event);
        }

        public List<FeatureCreatedEvent> getReceivedEvents() {
            return createdEvents;
        }

        public List<FeatureUpdatedEvent> getReceivedUpdatedEvents() {
            return updatedEvents;
        }

        public List<FeatureDeletedEvent> getReceivedDeletedEvents() {
            return deletedEvents;
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }
    }
}
