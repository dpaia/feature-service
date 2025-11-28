package com.sivalabs.ft.features.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.config.TestRabbitMQConfig;
import com.sivalabs.ft.features.config.TestRabbitMQInfrastructureConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.RabbitMQContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true"})
@ActiveProfiles("test")
@Import({TestRabbitMQConfig.class, TestRabbitMQInfrastructureConfig.class})
@DirtiesContext
class RabbitMQIntegrationTest extends AbstractIT {

    @LocalServerPort
    private int port;

    @Autowired
    private RabbitMQContainer rabbitMQContainer;

    @Autowired
    private ObjectMapper objectMapper;

    private Channel channel;
    private Connection connection;

    @BeforeEach
    void setUp() throws IOException, TimeoutException, InterruptedException {
        // Create direct channel connection to the same RabbitMQ container that Spring uses
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQContainer.getHost());
        factory.setPort(rabbitMQContainer.getAmqpPort());
        factory.setUsername("guest");
        factory.setPassword("guest");

        connection = factory.newConnection();
        channel = connection.createChannel();

        // Wait for Spring Boot infrastructure to be ready and purge the queue for clean state
        // Spring Boot will have already created all the infrastructure via TestRabbitMQInfrastructureConfig
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // Try to access the queue - this will throw an exception if it doesn't exist
            channel.queueDeclarePassive(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE);
        });

        // Purge the queue before each test to ensure clean state
        channel.queuePurge(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE);
    }

    @AfterEach
    void tearDown() throws IOException, TimeoutException {
        // Clean up resources after each test
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldPublishFeatureCreatedEventToRabbitMQ() throws Exception {
        // Given
        var payload =
                """
                {
                    "productCode": "intellij",
                    "releaseCode": "IDEA-2023.3.8",
                    "title": "RabbitMQ Test Feature",
                    "description": "Testing RabbitMQ integration",
                    "assignedTo": "john.doe"
                }
                """;

        // When - Create a feature
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        // Then - Verify message is in RabbitMQ queue
        // Give async event processing a moment to complete
        Thread.sleep(1000);

        GetResponse response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, false);
        String messageBody = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(messageBody).contains("RabbitMQ Test Feature");
        assertThat(messageBody).contains("Testing RabbitMQ integration");

        // Acknowledge the message
        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldPublishFeatureUpdatedEventToRabbitMQ() throws Exception {
        // Given - Use an existing feature code from test data
        String existingFeatureCode = "IDEA-1";

        // Clear the queue first
        channel.queuePurge(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE);

        // When - Update the feature
        var updatePayload =
                """
                {
                    "title": "Updated Title for RabbitMQ",
                    "description": "Updated description",
                    "status": "IN_PROGRESS",
                    "assignedTo": "jane.doe"
                }
                """;

        var updateResult = mvc.put()
                .uri("/api/features/{code}", existingFeatureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        assertThat(updateResult).hasStatusOk();

        // Then - Verify updated event is in RabbitMQ queue
        // Give async event processing a moment to complete
        Thread.sleep(1000);

        GetResponse response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, false);
        String messageBody = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(messageBody).contains(existingFeatureCode);
        assertThat(messageBody).contains("Updated Title for RabbitMQ");
        assertThat(messageBody).contains("IN_PROGRESS");

        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldPublishFeatureDeletedEventToRabbitMQ() throws Exception {
        // Given - Create a feature first to delete
        var createPayload =
                """
                {
                    "productCode": "intellij",
                    "releaseCode": "IDEA-2023.3.8",
                    "title": "Feature to Delete",
                    "description": "Will be deleted",
                    "assignedTo": "john.doe"
                }
                """;

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        // Clear the created event from queue - wait for async processing
        Thread.sleep(500);
        GetResponse createdEvent = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, true);
        String createdEventBody = new String(createdEvent.getBody(), StandardCharsets.UTF_8);
        assertThat(createdEventBody).contains("Feature to Delete");

        // When - Delete the feature
        var deleteResult = mvc.delete().uri("/api/features/{code}", featureCode).exchange();

        assertThat(deleteResult).hasStatusOk();

        // Then - Verify deleted event is in RabbitMQ queue
        // Give async event processing a moment to complete
        Thread.sleep(1000);

        GetResponse response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, false);
        String messageBody = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(messageBody).contains(featureCode);
        assertThat(messageBody).contains("Feature to Delete");

        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldVerifyRoutingKeysForAllEventTypes() throws Exception {
        // Test Created Event Routing Key
        var createPayload =
                """
                {
                    "productCode": "intellij",
                    "releaseCode": "IDEA-2023.3.8",
                    "title": "Routing Key Test Feature",
                    "description": "Testing routing keys",
                    "assignedTo": "john.doe"
                }
                """;

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        assertThat(createResult).hasStatus(HttpStatus.CREATED);

        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        // Handle both absolute and relative URLs
        assertThat(location).contains("/api/features/");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        // Verify Created event routing key
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            GetResponse response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, false);
            assertThat(response.getEnvelope().getRoutingKey())
                    .isEqualTo(TestRabbitMQConfig.ROUTING_KEY_FEATURE_CREATED);
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
        });

        // Test Updated Event Routing Key
        var updatePayload =
                """
                {
                    "title": "Updated Routing Key Test",
                    "description": "Updated for routing key test",
                    "status": "IN_PROGRESS"
                }
                """;

        mvc.put()
                .uri("/api/features/{code}", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        // Verify Updated event routing key
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            GetResponse response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, false);
            assertThat(response.getEnvelope().getRoutingKey())
                    .isEqualTo(TestRabbitMQConfig.ROUTING_KEY_FEATURE_UPDATED);
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
        });

        // Test Deleted Event Routing Key
        mvc.delete().uri("/api/features/{code}", featureCode).exchange();

        // Verify Deleted event routing key
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            GetResponse response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, false);
            assertThat(response.getEnvelope().getRoutingKey())
                    .isEqualTo(TestRabbitMQConfig.ROUTING_KEY_FEATURE_DELETED);
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
        });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldVerifyAsynchronousEventProcessing() throws Exception {
        // Given multiple features created rapidly
        int numberOfFeatures = 5;

        for (int i = 0; i < numberOfFeatures; i++) {
            var payload = String.format(
                    """
                            {
                                "productCode": "intellij",
                                "releaseCode": "IDEA-2023.3.8",
                                "title": "Async Test Feature %d",
                                "description": "Testing async processing",
                                "assignedTo": "john.doe"
                            }
                            """,
                    i);

            mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
        }

        // Then - All events should eventually be processed
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            int messageCount = 0;
            GetResponse response;

            do {
                response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, true);
                if (response != null) {
                    messageCount++;
                }
            } while (response != null);

            assertThat(messageCount).isEqualTo(numberOfFeatures);
        });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void shouldHandleMultipleEventTypesSequentially() throws Exception {
        // Create a feature
        var createPayload =
                """
                {
                    "productCode": "intellij",
                    "releaseCode": "IDEA-2023.3.8",
                    "title": "Sequential Events Test",
                    "description": "Testing sequential event processing",
                    "assignedTo": "john.doe"
                }
                """;

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .exchange();

        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        // Update the feature
        var updatePayload =
                """
                {
                    "title": "Updated Sequential Test",
                    "status": "IN_PROGRESS"
                }
                """;

        mvc.put()
                .uri("/api/features/{code}", featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .exchange();

        // Delete the feature
        mvc.delete().uri("/api/features/{code}", featureCode).exchange();

        // Verify all three event types were processed
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            int createdEvents = 0, updatedEvents = 0, deletedEvents = 0;

            GetResponse response;
            do {
                response = channel.basicGet(TestRabbitMQConfig.FEATURE_EVENTS_QUEUE, true);
                if (response != null) {
                    String routingKey = response.getEnvelope().getRoutingKey();
                    if (TestRabbitMQConfig.ROUTING_KEY_FEATURE_CREATED.equals(routingKey)) {
                        createdEvents++;
                    } else if (TestRabbitMQConfig.ROUTING_KEY_FEATURE_UPDATED.equals(routingKey)) {
                        updatedEvents++;
                    } else if (TestRabbitMQConfig.ROUTING_KEY_FEATURE_DELETED.equals(routingKey)) {
                        deletedEvents++;
                    }
                }
            } while (response != null);

            assertThat(createdEvents).isEqualTo(1);
            assertThat(updatedEvents).isEqualTo(1);
            assertThat(deletedEvents).isEqualTo(1);
        });
    }
}
