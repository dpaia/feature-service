package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.domain.events.FeatureEventListener;
import com.sivalabs.ft.features.domain.models.EventType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for API idempotency with EventListener business logic log analysis.
 * Tests that business logic in EventListener is executed only once per unique eventId
 * when called through external API endpoints.
 */
@Sql("/test-data.sql")
class FeatureControllerEventListenerLogAnalysisTest extends AbstractIT {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Uncomment this if your tests are behaving unstable. Kafka Testcontainer doesn't clear state between test
        // restarts by default unless the container is recreated.
        // This can help with rebalancing and skipping issues.
        // Wait for ALL containers to be running
        /**
         * await().atMost(Duration.ofSeconds(10))
         * .pollInterval(Duration.ofMillis(500))
         * .until(() -> kafkaListenerEndpointRegistry.getListenerContainers().stream()
         * .allMatch(container -> container.isRunning()));
         * // Wait for partition assignment to complete
         * await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofSeconds(2)).until(() -> true);
         */

        // Set up the logger and appender ONCE (don't clear between tests to capture all logs)
        if (logger == null) {
            logger = (Logger) LoggerFactory.getLogger(FeatureEventListener.class);
            listAppender = new ListAppender<>();
            listAppender.start();
            logger.addAppender(listAppender);
        }
    }

    /**
     * Helper method to count business logic execution logs for a specific eventId.
     * Uses simple contains check to find eventId in log messages.
     */
    private long countBusinessLogicLogsForEventId(String eventId) {
        return listAppender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .filter(event -> event.getFormattedMessage().startsWith("EventListener business logic"))
                .filter(event -> event.getFormattedMessage().contains(eventId))
                .count();
    }

    /**
     * Helper method to count events in database using direct SQL query.
     */
    private int countEventsInDatabase(String eventId, String eventType) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_store WHERE event_id = ? AND event_type = ?",
                    Integer.class,
                    eventId,
                    eventType);
            return count != null ? count : 0;
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    @Test
    @WithMockOAuth2User
    void shouldExecuteEventListenerBusinessLogicOnlyOnceForDuplicateCreateRequests() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload =
                new CreateFeaturePayload(eventId, "intellij", "Feature Title", "Feature Description", null, "user1");

        // When - First request
        var result1 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result1).hasStatus2xxSuccessful();
        String location1 = result1.getMvcResult().getResponse().getHeader("Location");

        // Wait for async event processing
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(countEventsInDatabase(eventId, EventType.EVENT.name()))
                        .isEqualTo(1));

        // When - Second request with same eventId (duplicate)
        var result2 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result2).hasStatus2xxSuccessful();
        String location2 = result2.getMvcResult().getResponse().getHeader("Location");

        // Wait for async event processing
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(countEventsInDatabase(eventId, EventType.EVENT.name()))
                        .isEqualTo(1));

        // Then - API should return same result (idempotent)
        assertThat(location1).isEqualTo(location2);

        // Verify EventListener business logic was executed only once through log analysis
        // In execute-first approach, we count logs containing the specific eventId
        long businessLogicExecutions = countBusinessLogicLogsForEventId(eventId);
        assertThat(businessLogicExecutions).isEqualTo(1);

        // Verify exactly one API and one EVENT record exist
        assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(eventId, EventType.EVENT.name())).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User
    void shouldExecuteEventListenerBusinessLogicOnlyOnceForDuplicateUpdateRequests() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                eventId, "Updated Title", "Updated Description", null, "user1", FeatureStatus.IN_PROGRESS);

        // When - First update request
        var result1 = mvc.put()
                .uri("/api/features/IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result1).hasStatus2xxSuccessful();

        // When - Second update request with same eventId (duplicate)
        var result2 = mvc.put()
                .uri("/api/features/IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result2).hasStatus2xxSuccessful();

        // Wait for async event processing
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(countEventsInDatabase(eventId, EventType.EVENT.name()))
                        .isEqualTo(1));

        // Then - Verify EventListener business logic was executed only once through log analysis
        long businessLogicExecutions = countBusinessLogicLogsForEventId(eventId);
        assertThat(businessLogicExecutions).isEqualTo(1);

        // Verify exactly one API and one EVENT record exist
        assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(eventId, EventType.EVENT.name())).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User
    void shouldExecuteEventListenerBusinessLogicOnlyOnceForConcurrentCreateRequests() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload = new CreateFeaturePayload(
                eventId, "intellij", "Concurrent Feature", "Concurrent Description", null, "user1");
        String payloadJson = objectMapper.writeValueAsString(payload);

        // When - Submit multiple concurrent requests with same eventId using CompletableFuture
        // Capture the security context from the main thread
        SecurityContext securityContext = SecurityContextHolder.getContext();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<CompletableFuture<String>> futures = List.of(
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    // Set security context in the worker thread
                                    SecurityContextHolder.setContext(securityContext);
                                    var result = mvc.post()
                                            .uri("/api/features")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(payloadJson)
                                            .exchange();
                                    assertThat(result).hasStatus2xxSuccessful();
                                    return result.getMvcResult().getResponse().getHeader("Location");
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            },
                            executor),
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    // Set security context in the worker thread
                                    SecurityContextHolder.setContext(securityContext);
                                    var result = mvc.post()
                                            .uri("/api/features")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(payloadJson)
                                            .exchange();
                                    assertThat(result).hasStatus2xxSuccessful();
                                    return result.getMvcResult().getResponse().getHeader("Location");
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            },
                            executor),
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    // Set security context in the worker thread
                                    SecurityContextHolder.setContext(securityContext);
                                    var result = mvc.post()
                                            .uri("/api/features")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(payloadJson)
                                            .exchange();
                                    assertThat(result).hasStatus2xxSuccessful();
                                    return result.getMvcResult().getResponse().getHeader("Location");
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            },
                            executor));

            // Wait for all concurrent requests to complete
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.get(); // This will throw if any future failed

            // Get all locations
            List<String> locations =
                    futures.stream().map(CompletableFuture::join).toList();

            // Wait for async event processing
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(countEventsInDatabase(eventId, EventType.EVENT.name()))
                            .isEqualTo(1));

            // Then - All requests should return the same location (idempotent)
            assertThat(locations.get(0)).isEqualTo(locations.get(1));
            assertThat(locations.get(1)).isEqualTo(locations.get(2));

            // Verify EventListener business logic was executed only once through log analysis
            long businessLogicExecutions = countBusinessLogicLogsForEventId(eventId);
            assertThat(businessLogicExecutions).isEqualTo(1);

            // Verify exactly one API and one EVENT record exist
            assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
            assertThat(countEventsInDatabase(eventId, EventType.EVENT.name())).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @WithMockOAuth2User
    void shouldExecuteEventListenerBusinessLogicOnlyOnceForConcurrentUpdateRequests() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                eventId, "Concurrent Update", "Concurrent Description", null, "user1", FeatureStatus.RELEASED);
        String payloadJson = objectMapper.writeValueAsString(payload);

        // When - Submit multiple concurrent update requests with same eventId using CompletableFuture
        // Capture the security context from the main thread
        SecurityContext securityContext = SecurityContextHolder.getContext();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<CompletableFuture<Void>> futures = List.of(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    // Set security context in the worker thread
                                    SecurityContextHolder.setContext(securityContext);
                                    var result = mvc.put()
                                            .uri("/api/features/IDEA-1")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(payloadJson)
                                            .exchange();
                                    assertThat(result).hasStatus2xxSuccessful();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            },
                            executor),
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    // Set security context in the worker thread
                                    SecurityContextHolder.setContext(securityContext);
                                    var result = mvc.put()
                                            .uri("/api/features/IDEA-1")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(payloadJson)
                                            .exchange();
                                    assertThat(result).hasStatus2xxSuccessful();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            },
                            executor),
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    // Set security context in the worker thread
                                    SecurityContextHolder.setContext(securityContext);
                                    var result = mvc.put()
                                            .uri("/api/features/IDEA-1")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(payloadJson)
                                            .exchange();
                                    assertThat(result).hasStatus2xxSuccessful();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            },
                            executor));

            // Wait for all concurrent requests to complete
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.get(); // This will throw if any future failed

            // Wait for async event processing
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(countEventsInDatabase(eventId, EventType.EVENT.name()))
                            .isEqualTo(1));

            // Then - Verify EventListener business logic was executed only once through log analysis
            long businessLogicExecutions = countBusinessLogicLogsForEventId(eventId);
            assertThat(businessLogicExecutions).isEqualTo(1);

            // Verify exactly one API and one EVENT record exist
            assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
            assertThat(countEventsInDatabase(eventId, EventType.EVENT.name())).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @WithMockOAuth2User
    void shouldAllowDifferentEventIdsForSameOperation() throws Exception {
        // Given
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();

        CreateFeaturePayload payload1 =
                new CreateFeaturePayload(eventId1, "intellij", "Feature 1", "Description 1", null, "user1");

        CreateFeaturePayload payload2 =
                new CreateFeaturePayload(eventId2, "intellij", "Feature 2", "Description 2", null, "user1");

        // When - Create features with different eventIds
        var result1 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1))
                .exchange();

        assertThat(result1).hasStatus2xxSuccessful();
        String location1 = result1.getMvcResult().getResponse().getHeader("Location");

        var result2 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .exchange();

        assertThat(result2).hasStatus2xxSuccessful();
        String location2 = result2.getMvcResult().getResponse().getHeader("Location");

        // Wait for async event processing
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(countEventsInDatabase(eventId1, EventType.EVENT.name())).isEqualTo(1);
            assertThat(countEventsInDatabase(eventId2, EventType.EVENT.name())).isEqualTo(1);
        });

        // Then - Different features should be created
        assertThat(location1).isNotEqualTo(location2);

        // Wait for business logic logs to be written
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            long businessLogicExecutions1 = countBusinessLogicLogsForEventId(eventId1);
            long businessLogicExecutions2 = countBusinessLogicLogsForEventId(eventId2);
            assertThat(businessLogicExecutions1)
                    .as("Business logic executions for eventId1: " + eventId1)
                    .isEqualTo(1);
            assertThat(businessLogicExecutions2)
                    .as("Business logic executions for eventId2: " + eventId2)
                    .isEqualTo(1);
        });

        // Verify exactly one processed event record exists for each eventId
        assertThat(countEventsInDatabase(eventId1, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(eventId1, EventType.EVENT.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(eventId2, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(eventId2, EventType.EVENT.name())).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User
    void shouldExecuteEventListenerBusinessLogicForMixedOperations() throws Exception {
        // Given
        String createEventId = UUID.randomUUID().toString();
        String updateEventId = UUID.randomUUID().toString();

        CreateFeaturePayload createPayload = new CreateFeaturePayload(
                createEventId, "intellij", "Mixed Feature", "Mixed Description", null, "user1");

        UpdateFeaturePayload updatePayload = new UpdateFeaturePayload(
                updateEventId,
                "Mixed Updated Title",
                "Mixed Updated Description",
                null,
                "user1",
                FeatureStatus.IN_PROGRESS);

        // When - Create feature
        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPayload))
                .exchange();

        assertThat(createResult).hasStatus2xxSuccessful();

        // When - Update feature
        var updateResult = mvc.put()
                .uri("/api/features/IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        assertThat(updateResult).hasStatus2xxSuccessful();

        // Wait for async event processing
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(countEventsInDatabase(createEventId, EventType.EVENT.name()))
                    .isEqualTo(1);
            assertThat(countEventsInDatabase(updateEventId, EventType.EVENT.name()))
                    .isEqualTo(1);
        });

        // Then - Verify EventListener business logic was executed for both operations through eventId
        long createExecutions = countBusinessLogicLogsForEventId(createEventId);
        long updateExecutions = countBusinessLogicLogsForEventId(updateEventId);

        assertThat(createExecutions).isEqualTo(1);
        assertThat(updateExecutions).isEqualTo(1);

        // Verify exactly one processed event record exists for each eventId and type
        assertThat(countEventsInDatabase(createEventId, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(createEventId, EventType.EVENT.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(updateEventId, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(updateEventId, EventType.EVENT.name())).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User
    void shouldExecuteEventListenerBusinessLogicOnlyOnceForDuplicateDeleteRequests() throws Exception {
        // Given - First create a feature to delete
        String createEventId = UUID.randomUUID().toString();
        CreateFeaturePayload createPayload = new CreateFeaturePayload(
                createEventId, "intellij", "Delete Test Feature", "Feature for delete testing", null, "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPayload))
                .exchange();

        assertThat(createResult).hasStatus2xxSuccessful();
        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = location.substring(location.lastIndexOf("/") + 1);

        // Wait for create event processing
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(countEventsInDatabase(createEventId, EventType.API.name()))
                    .isEqualTo(1);
            assertThat(countEventsInDatabase(createEventId, EventType.EVENT.name()))
                    .isEqualTo(1);
        });

        // When - First delete request
        String deleteEventId = UUID.randomUUID().toString();
        var result1 = mvc.delete()
                .uri("/api/features/{code}?eventId={eventId}", featureCode, deleteEventId)
                .exchange();

        assertThat(result1).hasStatus2xxSuccessful();

        // Wait for async event processing
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(countEventsInDatabase(deleteEventId, EventType.EVENT.name()))
                        .isEqualTo(1));

        // Verify EventListener business logic was executed for the first delete
        long businessLogicExecutionsAfterFirst = countBusinessLogicLogsForEventId(deleteEventId);
        assertThat(businessLogicExecutionsAfterFirst).isEqualTo(1);

        // When - Second delete request with same eventId (duplicate)
        // Note: Due to idempotency, this should return the same result as the first request
        // The business logic should NOT be executed again (no new Kafka event published)
        var result2 = mvc.delete()
                .uri("/api/features/{code}?eventId={eventId}", featureCode, deleteEventId)
                .exchange();

        // The second request should be idempotent - return the same result as first request
        assertThat(result2).hasStatus2xxSuccessful();

        // Wait a bit to ensure no additional events are processed
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(countEventsInDatabase(deleteEventId, EventType.EVENT.name()))
                        .isEqualTo(1)); // Should still be 1, not 2

        // Then - Verify EventListener business logic was still executed only once (no additional execution)
        long businessLogicExecutionsAfterSecond = countBusinessLogicLogsForEventId(deleteEventId);
        assertThat(businessLogicExecutionsAfterSecond).isEqualTo(1); // Still 1, not 2

        // Verify exactly one API and one EVENT record exist for delete operation
        assertThat(countEventsInDatabase(deleteEventId, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(deleteEventId, EventType.EVENT.name())).isEqualTo(1);
    }
}
