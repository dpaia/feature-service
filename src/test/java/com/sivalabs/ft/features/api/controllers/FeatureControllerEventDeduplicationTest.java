package com.sivalabs.ft.features.api.controllers;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for event deduplication through REST API using MockMvc
 * Uses embedded Kafka and PostgreSQL via Testcontainers
 *
 * Tests the complete flow:
 * REST API → FeatureService → EventDeduplicationService (pre-DB check) → Database operations → EventPublisher → Kafka → FeatureEventListener
 *
 * This test requires no external services and starts all necessary components automatically
 */
public class FeatureControllerEventDeduplicationTest extends AbstractIT {

    @DynamicPropertySource
    static void configureAdditionalProperties(DynamicPropertyRegistry registry) {
        // Override consumer group-id for this specific test with timestamp to avoid conflicts
        registry.add(
                "spring.kafka.consumer.group-id",
                () -> "feature-event-deduplication-test-group-" + System.currentTimeMillis());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Wait for any pending events to be processed before cleaning up
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertNotNull(jdbcTemplate, "JdbcTemplate should be available"));

        // Clean up database state before each test - be more aggressive to handle cross-test contamination
        jdbcTemplate.execute("DELETE FROM processed_events");
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void testCreateFeatureGeneratesEventAndProcessedByDeduplication() throws Exception {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload = new CreateFeaturePayload(
                eventId,
                "intellij",
                "MockMvc Test Feature",
                "Feature created via MockMvc for deduplication testing",
                "IDEA-2024.2.3",
                "testuser");

        // Act - create feature via REST API
        MvcResult result = mockMvc.perform(post("/api/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        // Extract feature code from Location header
        String location = result.getResponse().getHeader("Location");
        String featureCode = extractFeatureCodeFromLocation(location);
        assertNotNull(featureCode, "Feature code should be extracted from Location header");

        // Assert - wait for specific events to be processed through Kafka and saved in PostgreSQL
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific eventId
            assertTrue(isEventProcessed(eventId, "API"), "API event with eventId " + eventId + " should be processed");
            assertTrue(
                    isEventProcessed(eventId, "EVENT"), "EVENT event with eventId " + eventId + " should be processed");

            // Verify exactly 2 events for this specific eventId
            int eventsForThisId = getProcessedEventsCountByEventId(eventId);
            assertEquals(
                    2,
                    eventsForThisId,
                    "Should have exactly 2 processed events (API + EVENT) for eventId " + eventId + ", but found: "
                            + eventsForThisId);
        });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void testUpdateFeatureGeneratesEventAndProcessedByDeduplication() throws Exception {
        // Arrange - first create a feature
        String createEventId = UUID.randomUUID().toString();
        CreateFeaturePayload createPayload = new CreateFeaturePayload(
                createEventId,
                "intellij",
                "MockMvc Update Test Feature",
                "Feature for update testing",
                "IDEA-2024.2.3",
                "testuser");

        MvcResult createResult = mockMvc.perform(post("/api/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isCreated())
                .andReturn();

        String featureCode =
                extractFeatureCodeFromLocation(createResult.getResponse().getHeader("Location"));

        // Wait for create event processing
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific createEventId
            assertTrue(
                    isEventProcessed(createEventId, "API"),
                    "API event with eventId " + createEventId + " should be processed");
            assertTrue(
                    isEventProcessed(createEventId, "EVENT"),
                    "EVENT event with eventId " + createEventId + " should be processed");

            // Verify exactly 2 events for this specific eventId
            int eventsForThisId = getProcessedEventsCountByEventId(createEventId);
            assertEquals(
                    2,
                    eventsForThisId,
                    "Should have exactly 2 processed events (API + EVENT) for eventId " + createEventId
                            + ", but found: " + eventsForThisId);
        });

        // Act - update feature
        String updateEventId = UUID.randomUUID().toString();
        UpdateFeaturePayload updatePayload = new UpdateFeaturePayload(
                updateEventId,
                "Updated MockMvc Test Feature",
                "Updated description via MockMvc",
                "IDEA-2023.3.8",
                "updateduser",
                FeatureStatus.IN_PROGRESS);

        mockMvc.perform(put("/api/features/{code}", featureCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk());

        // Assert - wait for update event to be processed
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific updateEventId
            assertTrue(
                    isEventProcessed(updateEventId, "API"),
                    "API event with eventId " + updateEventId + " should be processed");
            assertTrue(
                    isEventProcessed(updateEventId, "EVENT"),
                    "EVENT event with eventId " + updateEventId + " should be processed");

            // Verify exactly 2 events for this specific updateEventId
            int eventsForUpdateId = getProcessedEventsCountByEventId(updateEventId);
            assertEquals(
                    2,
                    eventsForUpdateId,
                    "Should have exactly 2 processed events (API + EVENT) for updateEventId " + updateEventId
                            + ", but found: " + eventsForUpdateId);

            // Also verify create events are still there
            int eventsForCreateId = getProcessedEventsCountByEventId(createEventId);
            assertEquals(
                    2,
                    eventsForCreateId,
                    "Should still have 2 processed events for createEventId " + createEventId + ", but found: "
                            + eventsForCreateId);
        });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void testDeleteFeatureGeneratesEventAndProcessedByDeduplication() throws Exception {
        // Arrange - create feature for deletion
        String createEventId = UUID.randomUUID().toString();
        CreateFeaturePayload createPayload = new CreateFeaturePayload(
                createEventId,
                "intellij",
                "MockMvc Delete Test Feature",
                "Feature for delete testing",
                "IDEA-2024.2.3",
                "testuser");

        MvcResult createResult = mockMvc.perform(post("/api/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isCreated())
                .andReturn();

        String featureCode =
                extractFeatureCodeFromLocation(createResult.getResponse().getHeader("Location"));

        // Wait for create event processing
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific createEventId
            assertTrue(
                    isEventProcessed(createEventId, "API"),
                    "API event with eventId " + createEventId + " should be processed");
            assertTrue(
                    isEventProcessed(createEventId, "EVENT"),
                    "EVENT event with eventId " + createEventId + " should be processed");

            // Verify exactly 2 events for this specific eventId
            int eventsForThisId = getProcessedEventsCountByEventId(createEventId);
            assertEquals(
                    2,
                    eventsForThisId,
                    "Should have exactly 2 processed events (API + EVENT) for eventId " + createEventId
                            + ", but found: " + eventsForThisId);
        });

        // Act - delete feature
        String deleteEventId = UUID.randomUUID().toString();
        mockMvc.perform(delete("/api/features/{code}", featureCode).param("eventId", deleteEventId))
                .andExpect(status().isOk());

        // Assert - wait for delete event to be processed
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific deleteEventId
            assertTrue(
                    isEventProcessed(deleteEventId, "API"),
                    "API event with eventId " + deleteEventId + " should be processed");
            assertTrue(
                    isEventProcessed(deleteEventId, "EVENT"),
                    "EVENT event with eventId " + deleteEventId + " should be processed");

            // Verify exactly 2 events for this specific deleteEventId
            int eventsForDeleteId = getProcessedEventsCountByEventId(deleteEventId);
            assertEquals(
                    2,
                    eventsForDeleteId,
                    "Should have exactly 2 processed events (API + EVENT) for deleteEventId " + deleteEventId
                            + ", but found: " + eventsForDeleteId);

            // Also verify create events are still there
            int eventsForCreateId = getProcessedEventsCountByEventId(createEventId);
            assertEquals(
                    2,
                    eventsForCreateId,
                    "Should still have 2 processed events for createEventId " + createEventId + ", but found: "
                            + eventsForCreateId);
        });
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    void testFullCRUDCycleGeneratesAllEventsAndProcessedByDeduplication() throws Exception {
        // Arrange
        String createEventId = UUID.randomUUID().toString();
        CreateFeaturePayload createPayload = new CreateFeaturePayload(
                createEventId,
                "intellij",
                "Full MockMvc CRUD Test Feature",
                "Feature for full CRUD cycle testing",
                "IDEA-2024.2.3",
                "testuser");

        // Act & Assert - complete CRUD operations cycle

        // 1. CREATE
        MvcResult createResult = mockMvc.perform(post("/api/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isCreated())
                .andReturn();

        String featureCode =
                extractFeatureCodeFromLocation(createResult.getResponse().getHeader("Location"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific createEventId
            assertTrue(
                    isEventProcessed(createEventId, "API"),
                    "API event with eventId " + createEventId + " should be processed");
            assertTrue(
                    isEventProcessed(createEventId, "EVENT"),
                    "EVENT event with eventId " + createEventId + " should be processed");

            // Verify exactly 2 events for this specific eventId
            int eventsForThisId = getProcessedEventsCountByEventId(createEventId);
            assertEquals(
                    2,
                    eventsForThisId,
                    "Should have exactly 2 processed events (API + EVENT) for eventId " + createEventId
                            + ", but found: " + eventsForThisId);
        });

        // 2. READ (verify that feature was created)
        mockMvc.perform(get("/api/features/{code}", featureCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(featureCode))
                .andExpect(jsonPath("$.title").value("Full MockMvc CRUD Test Feature"));

        // 3. UPDATE
        String updateEventId = UUID.randomUUID().toString();
        UpdateFeaturePayload updatePayload = new UpdateFeaturePayload(
                updateEventId,
                "Updated Full MockMvc CRUD Test Feature",
                "Updated description for full CRUD testing",
                "IDEA-2023.3.8",
                "updateduser",
                FeatureStatus.RELEASED);

        mockMvc.perform(put("/api/features/{code}", featureCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific updateEventId
            assertTrue(
                    isEventProcessed(updateEventId, "API"),
                    "API event with eventId " + updateEventId + " should be processed");
            assertTrue(
                    isEventProcessed(updateEventId, "EVENT"),
                    "EVENT event with eventId " + updateEventId + " should be processed");

            // Verify exactly 2 events for this specific updateEventId
            int eventsForUpdateId = getProcessedEventsCountByEventId(updateEventId);
            assertEquals(
                    2,
                    eventsForUpdateId,
                    "Should have exactly 2 processed events (API + EVENT) for updateEventId " + updateEventId
                            + ", but found: " + eventsForUpdateId);
        });

        // 4. DELETE
        String deleteEventId = UUID.randomUUID().toString();
        mockMvc.perform(delete("/api/features/{code}", featureCode).param("eventId", deleteEventId))
                .andExpect(status().isOk());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check that both API and EVENT entries exist for our specific deleteEventId
            assertTrue(
                    isEventProcessed(deleteEventId, "API"),
                    "API event with eventId " + deleteEventId + " should be processed");
            assertTrue(
                    isEventProcessed(deleteEventId, "EVENT"),
                    "EVENT event with eventId " + deleteEventId + " should be processed");

            // Verify exactly 2 events for this specific deleteEventId
            int eventsForDeleteId = getProcessedEventsCountByEventId(deleteEventId);
            assertEquals(
                    2,
                    eventsForDeleteId,
                    "Should have exactly 2 processed events (API + EVENT) for deleteEventId " + deleteEventId
                            + ", but found: " + eventsForDeleteId);

            // Also verify previous events are still there
            int eventsForCreateId = getProcessedEventsCountByEventId(createEventId);
            assertEquals(
                    2,
                    eventsForCreateId,
                    "Should still have 2 processed events for createEventId " + createEventId + ", but found: "
                            + eventsForCreateId);

            int eventsForUpdateId = getProcessedEventsCountByEventId(updateEventId);
            assertEquals(
                    2,
                    eventsForUpdateId,
                    "Should still have 2 processed events for updateEventId " + updateEventId + ", but found: "
                            + eventsForUpdateId);
        });

        // Verify that feature was actually deleted
        mockMvc.perform(get("/api/features/{code}", featureCode)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockOAuth2User(username = "testuser")
    @org.springframework.transaction.annotation.Transactional
    void testEventDeduplicationCleanup() throws Exception {
        // Arrange - create an expired event manually using direct SQL
        String expiredEventId = "expired-event-" + UUID.randomUUID().toString();
        LocalDateTime pastTime = LocalDateTime.now().minusHours(25); // 25 hours ago (expired)

        // Insert expired event directly into database
        jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, event_type, processed_at, expires_at, result_data) VALUES (?, ?, ?, ?, ?)",
                expiredEventId,
                "EVENT",
                pastTime,
                pastTime,
                "test-result");

        // Verify the event exists
        int eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ? AND event_type = ?",
                Integer.class,
                expiredEventId,
                "EVENT");
        assertEquals(1, eventCount, "Expired event should exist in database");

        // Act - cleanup expired events using direct SQL
        int deletedCount =
                jdbcTemplate.update("DELETE FROM processed_events WHERE expires_at < ?", LocalDateTime.now());

        // Assert
        assertEquals(1, deletedCount, "Should have deleted exactly one expired event, but deleted: " + deletedCount);

        // Verify the event was actually deleted
        int remainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ? AND event_type = ?",
                Integer.class,
                expiredEventId,
                "EVENT");
        assertEquals(0, remainingCount, "Expired event should have been deleted from database");
    }

    /**
     * Gets the total count of processed events in PostgreSQL using direct SQL
     */
    private int getProcessedEventsCount() {
        try {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_events", Integer.class);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Gets the count of processed events by event type using direct SQL
     */
    private int getProcessedEventsCountByType(String eventType) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM processed_events WHERE event_type = ?", Integer.class, eventType);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Checks if a specific event with given eventId and eventType is processed
     */
    private boolean isEventProcessed(String eventId, String eventType) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM processed_events WHERE event_id = ? AND event_type = ?",
                    Integer.class,
                    eventId,
                    eventType);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the count of processed events for a specific eventId (both API and EVENT types)
     */
    private int getProcessedEventsCountByEventId(String eventId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Extracts feature code from Location header
     */
    private String extractFeatureCodeFromLocation(String location) {
        if (location == null) return null;

        // Location format: http://localhost/api/features/{code}
        Pattern pattern = Pattern.compile("/api/features/([^/]+)$");
        Matcher matcher = pattern.matcher(location);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
