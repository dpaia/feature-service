package com.sivalabs.ft.features.api.controllers;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.domain.EventStoreRepository;
import com.sivalabs.ft.features.domain.entities.EventStore;
import com.sivalabs.ft.features.domain.models.EventType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Integration tests for Event Replay functionality
 * Tests the complete replay system including REST API, security, validation, and chronological order
 */
class EventReplayIntegrationTest extends AbstractIT {

    @Autowired
    private EventStoreRepository eventStoreRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean up event store before each test
        eventStoreRepository.deleteAll();
    }

    /**
     * Helper method to verify chronological order using direct SQL queries
     */
    private void verifyChronologicalOrderViaSql(String featureCode) {
        // Query events for feature in chronological order using direct SQL
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                """
                SELECT event_id, operation_type, event_timestamp, replay_count, last_replayed_at
                FROM event_store
                WHERE feature_code = ? AND event_type = 'EVENT'
                ORDER BY event_timestamp ASC
                """,
                featureCode);

        assertThat(events).isNotEmpty();

        // Verify chronological order
        for (int i = 0; i < events.size() - 1; i++) {
            Timestamp currentTime = (Timestamp) events.get(i).get("event_timestamp");
            Timestamp nextTime = (Timestamp) events.get(i + 1).get("event_timestamp");
            assertThat(currentTime.toLocalDateTime()).isBeforeOrEqualTo(nextTime.toLocalDateTime());
        }
    }

    /**
     * Helper method to verify replay tracking using direct SQL queries
     */
    private void verifyReplayTrackingViaSql(String eventId, int expectedReplayCount, LocalDateTime replayStartTime) {
        // Query replay tracking using direct SQL
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                """
                SELECT replay_count, last_replayed_at
                FROM event_store
                WHERE event_id = ? AND event_type = 'EVENT'
                """,
                eventId);

        assertThat(results).hasSize(1);
        Map<String, Object> result = results.get(0);

        Integer replayCount = (Integer) result.get("replay_count");
        Timestamp lastReplayedAtTimestamp = (Timestamp) result.get("last_replayed_at");

        assertThat(replayCount).isEqualTo(expectedReplayCount);
        if (expectedReplayCount > 0) {
            assertThat(lastReplayedAtTimestamp.toLocalDateTime()).isAfter(replayStartTime);
        } else {
            assertThat(lastReplayedAtTimestamp).isNull();
        }
    }

    /**
     * Helper method to verify single event detail in API response
     */
    private void verifySingleEventDetail(
            MvcTestResult response,
            String expectedEventId,
            String expectedFeatureCode,
            String expectedOperationType,
            String expectedStatus) {
        // Verify detailed event information in response
        assertThat(response)
                .bodyJson()
                .extractingPath("$.eventDetails")
                .asList()
                .hasSize(1); // Should have 1 event detail

        // Verify event detail contains correct information
        assertThat(response)
                .bodyJson()
                .extractingPath("$.eventDetails[0].eventId")
                .isEqualTo(expectedEventId);

        assertThat(response)
                .bodyJson()
                .extractingPath("$.eventDetails[0].featureCode")
                .isEqualTo(expectedFeatureCode);

        assertThat(response)
                .bodyJson()
                .extractingPath("$.eventDetails[0].operationType")
                .isEqualTo(expectedOperationType);

        assertThat(response)
                .bodyJson()
                .extractingPath("$.eventDetails[0].status")
                .isEqualTo(expectedStatus);

        assertThat(response)
                .bodyJson()
                .extractingPath("$.eventDetails[0].eventTimestamp")
                .isNotNull();
    }

    /**
     * Helper method to verify event counts in API response
     */
    private void verifyEventCounts(
            MvcTestResult response, int expectedTotal, int expectedReplayed, int expectedFailed) {
        assertThat(response).bodyJson().extractingPath("$.totalEvents").isEqualTo(expectedTotal);
        assertThat(response).bodyJson().extractingPath("$.replayedEvents").isEqualTo(expectedReplayed);
        assertThat(response).bodyJson().extractingPath("$.failedEvents").isEqualTo(expectedFailed);
    }

    /**
     * Helper method to verify replay tracking for event by eventId
     */
    private void verifyReplayTrackingByEventId(String eventId, int expectedReplayCount, LocalDateTime replayStartTime) {
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                """
                SELECT replay_count, last_replayed_at
                FROM event_store
                WHERE event_id = ? AND event_type = 'EVENT'
                """,
                eventId);

        assertThat(events).hasSize(1);
        Map<String, Object> event = events.get(0);

        Integer replayCount = (Integer) event.get("replay_count");
        Timestamp lastReplayedAt = (Timestamp) event.get("last_replayed_at");

        assertThat(replayCount).isEqualTo(expectedReplayCount);
        assertThat(lastReplayedAt.toLocalDateTime()).isAfter(replayStartTime);
    }

    /**
     * Helper method to verify replay tracking for event by featureCode
     */
    private void verifyReplayTrackingByFeatureCode(
            String featureCode, int expectedReplayCount, LocalDateTime replayStartTime) {
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                """
                SELECT replay_count, last_replayed_at
                FROM event_store
                WHERE feature_code = ? AND event_type = 'EVENT'
                """,
                featureCode);

        assertThat(events).hasSize(1);
        Map<String, Object> event = events.get(0);

        Integer replayCount = (Integer) event.get("replay_count");
        Timestamp lastReplayedAt = (Timestamp) event.get("last_replayed_at");

        assertThat(replayCount).isEqualTo(expectedReplayCount);
        assertThat(lastReplayedAt.toLocalDateTime()).isAfter(replayStartTime);
    }

    @Test
    @WithMockOAuth2User
    void shouldStoreEventMetadataInEventStore() throws Exception {
        // Given: Create a feature via REST API
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload =
                new CreateFeaturePayload(eventId, "intellij", "Test Feature", "Description", null, "user1");

        // When: Create feature via REST API
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");
        String featureCode = extractFeatureCodeFromLocation(location);

        // Wait for events to be stored
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        // Then: Verify event metadata is stored
        List<EventStore> events = eventStoreRepository.findAll();
        assertThat(events).isNotEmpty();

        EventStore apiEvent = events.stream()
                .filter(e -> e.getEventType() == EventType.API && e.getEventId().equals(eventId))
                .findFirst()
                .orElseThrow();

        assertThat(apiEvent.getOperationType()).isEqualTo("CREATED");
        assertThat(apiEvent.getFeatureCode()).isEqualTo(featureCode);
        assertThat(apiEvent.getEventPayload()).isNotNull();
        assertThat(apiEvent.getEventTimestamp()).isNotNull();
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldReplayEventsByTimeRange() throws Exception {
        // Given: Create test events via REST API
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload =
                new CreateFeaturePayload(eventId, "intellij", "Test Feature", "Description", null, "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Wait for events to be stored
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Count events in time range via API
        var countResult = mvc.get()
                .uri("/api/events/replay/count?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should find exactly 1 event (only EVENT type events are counted for replay)
        assertThat(countResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(1);

        // Store initial replay tracking state
        LocalDateTime replayStartTime = LocalDateTime.now();

        // When: Replay events by time range via API
        var replayResult = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should complete successfully
        assertThat(replayResult).hasStatus2xxSuccessful();

        // STRICT VERIFICATION: Check that replay actually happened using helper method
        await().atMost(5, SECONDS).untilAsserted(() -> {
            verifyReplayTrackingByEventId(eventId, 1, replayStartTime);
        });
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldReplayEventsByFeatureCode() throws Exception {
        // Given: Create test event via REST API
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload =
                new CreateFeaturePayload(eventId, "intellij", "Test Feature", "Description", null, "user1");

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");
        String featureCode = extractFeatureCodeFromLocation(location);

        // Wait for events to be stored
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        // Store initial replay tracking state
        LocalDateTime replayStartTime = LocalDateTime.now();

        // When: Replay events for specific feature via API
        var replayResult =
                mvc.post().uri("/api/events/replay/feature/" + featureCode).exchange();

        // Then: Should complete successfully with detailed event information
        assertThat(replayResult).hasStatus2xxSuccessful();

        // Verify detailed event information using helper method
        verifySingleEventDetail(replayResult, eventId, featureCode, "CREATED", "SUCCESS");

        // STRICT VERIFICATION: Check that replay actually happened using helper method
        await().atMost(5, SECONDS).untilAsserted(() -> {
            verifyReplayTrackingByFeatureCode(featureCode, 1, replayStartTime);
        });
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldPreventDuplicateReplayProcessing() throws Exception {
        // Given: Create a feature via REST API
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload =
                new CreateFeaturePayload(eventId, "intellij", "Test Feature", "Description", null, "user1");

        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");
        String featureCode = extractFeatureCodeFromLocation(location);

        // Wait for events to be stored
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        // Store initial replay tracking state
        LocalDateTime replayStartTime = LocalDateTime.now();

        // When: Replay the same events multiple times via API
        var firstReplayResult =
                mvc.post().uri("/api/events/replay/feature/" + featureCode).exchange();

        var secondReplayResult =
                mvc.post().uri("/api/events/replay/feature/" + featureCode).exchange();

        // Then: Both replays should complete successfully (replay always publishes, deduplication happens in
        // EventListener)
        assertThat(firstReplayResult).hasStatus2xxSuccessful();
        assertThat(secondReplayResult).hasStatus2xxSuccessful();

        // STRICT VERIFICATION: Check replay tracking shows multiple replays
        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<EventStore> events = eventStoreRepository.findAll();
            EventStore eventEntry = events.stream()
                    .filter(e -> e.getEventType() == EventType.EVENT
                            && e.getEventId().equals(eventId))
                    .findFirst()
                    .orElseThrow();

            // Should have exactly one EVENT type entry (deduplicated by EventListener)
            assertThat(eventEntry).isNotNull();

            // But replay count should be 2 (both replays were executed)
            assertThat(eventEntry.getReplayCount()).isEqualTo(2);
            assertThat(eventEntry.getLastReplayedAt()).isAfter(replayStartTime);
        });
    }

    @Test
    @WithMockOAuth2User
    void shouldStoreFullEventPayloadForReplay() throws Exception {
        // Given: Create and update a feature via REST API
        String createEventId = UUID.randomUUID().toString();
        CreateFeaturePayload createPayload =
                new CreateFeaturePayload(createEventId, "intellij", "Test Feature", "Description", null, "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPayload))
                .exchange();

        assertThat(createResult).hasStatus(HttpStatus.CREATED);
        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = extractFeatureCodeFromLocation(location);

        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // 1 API + 1 EVENT

        String updateEventId = UUID.randomUUID().toString();
        UpdateFeaturePayload updatePayload = new UpdateFeaturePayload(
                updateEventId, "Updated Title", "Updated Description", null, "user2", FeatureStatus.IN_PROGRESS);

        mvc.put()
                .uri("/api/features/" + featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        await().atMost(10, SECONDS)
                .until(() -> eventStoreRepository
                                .findEventsByFeatureCode(featureCode, EventType.API)
                                .size()
                        == 2);

        // When: Check event store via direct SQL
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                """
                SELECT operation_type, event_payload
                FROM event_store
                WHERE feature_code = ? AND event_type = 'API'
                """,
                featureCode);

        // Then: Verify both events are stored with full payload
        assertThat(events).hasSize(2);
        assertThat(events).extracting(e -> e.get("operation_type")).containsExactlyInAnyOrder("CREATED", "UPDATED");
        assertThat(events).allMatch(e -> {
            String payload = (String) e.get("event_payload");
            return payload != null && !payload.isEmpty();
        });
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldCountEventsInTimeRangeViaAPI() throws Exception {
        // Given: Create some test events via REST API
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();

        CreateFeaturePayload payload1 =
                new CreateFeaturePayload(eventId1, "intellij", "Test Feature 1", "Description 1", null, "user1");
        CreateFeaturePayload payload2 =
                new CreateFeaturePayload(eventId2, "intellij", "Test Feature 2", "Description 2", null, "user1");

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1))
                .exchange();

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .exchange();

        // Wait for events to be stored
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 4); // Wait for 2 API + 2 EVENT

        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Count events in time range via API
        var result = mvc.get()
                .uri("/api/events/replay/count?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return count of EVENT type events (2)
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(2);
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldReplayEventsByTimeRangeViaAPI() throws Exception {
        // Given: Create test events via REST API
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload =
                new CreateFeaturePayload(eventId, "intellij", "Test Feature", "Description", null, "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        String featureCode = extractFeatureCodeFromLocation(
                createResult.getMvcResult().getResponse().getHeader("Location"));

        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Replay events by time range via API
        var result = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should complete successfully with detailed event information
        assertThat(result).hasStatus2xxSuccessful();

        // Verify detailed event information and counts using helper methods
        verifySingleEventDetail(result, eventId, featureCode, "CREATED", "SUCCESS");
        verifyEventCounts(result, 1, 1, 0);
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldReplayEventsByOperationTypeViaAPI() throws Exception {
        // Given: Create test events via REST API
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload =
                new CreateFeaturePayload(eventId, "intellij", "Test Feature", "Description", null, "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        String featureCode = extractFeatureCodeFromLocation(
                createResult.getMvcResult().getResponse().getHeader("Location"));

        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Replay events by operation type via API
        var result = mvc.post()
                .uri("/api/events/replay/operation/CREATED?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should complete successfully with detailed event information
        assertThat(result)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.operationType")
                .isEqualTo("CREATED");

        // Verify detailed event information and counts using helper methods
        verifySingleEventDetail(result, eventId, featureCode, "CREATED", "SUCCESS");
        verifyEventCounts(result, 1, 1, 0);
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldReplayEventsByFeaturesViaAPI() throws Exception {
        // Given: Create test events for multiple features via REST API
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();

        CreateFeaturePayload payload1 =
                new CreateFeaturePayload(eventId1, "intellij", "Test Feature 1", "Description 1", null, "user1");
        CreateFeaturePayload payload2 =
                new CreateFeaturePayload(eventId2, "intellij", "Test Feature 2", "Description 2", null, "user1");

        var result1 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1))
                .exchange();

        var result2 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .exchange();

        String featureCode1 = extractFeatureCodeFromLocation(
                result1.getMvcResult().getResponse().getHeader("Location"));
        String featureCode2 = extractFeatureCodeFromLocation(
                result2.getMvcResult().getResponse().getHeader("Location"));

        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 4); // Wait for 2 API + 2 EVENT

        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // Store initial replay tracking state
        List<EventStore> eventsBeforeReplay = eventStoreRepository.findAll();
        LocalDateTime replayStartTime = LocalDateTime.now();

        // When: Replay events for specific features via API
        var result = mvc.post()
                .uri("/api/events/replay/features?startTime={start}&endTime={end}", startTime, endTime)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(featureCode1, featureCode2)))
                .exchange();

        // Then: Should complete successfully with detailed event information
        assertThat(result).hasStatus2xxSuccessful();

        // Verify detailed event information in response
        assertThat(result)
                .bodyJson()
                .extractingPath("$.eventDetails")
                .asList()
                .hasSize(2); // Should have 2 event details

        // Verify both feature codes are present in event details
        assertThat(result)
                .bodyJson()
                .extractingPath("$.eventDetails[*].featureCode")
                .asList()
                .containsExactlyInAnyOrder(featureCode1, featureCode2);

        // Verify both event IDs are present in event details
        assertThat(result)
                .bodyJson()
                .extractingPath("$.eventDetails[*].eventId")
                .asList()
                .containsExactlyInAnyOrder(eventId1, eventId2);

        // Verify all events have CREATED operation type
        assertThat(result)
                .bodyJson()
                .extractingPath("$.eventDetails[*].operationType")
                .asList()
                .allMatch(operationType -> "CREATED".equals(operationType));

        // Verify all events have SUCCESS status
        assertThat(result)
                .bodyJson()
                .extractingPath("$.eventDetails[*].status")
                .asList()
                .allMatch(status -> "SUCCESS".equals(status));

        // Verify counts match event details using helper method
        verifyEventCounts(result, 2, 2, 0);

        // STRICT VERIFICATION: Check that replay actually happened using helper methods
        await().atMost(5, SECONDS).untilAsserted(() -> {
            verifyReplayTrackingByFeatureCode(featureCode1, 1, replayStartTime);
            verifyReplayTrackingByFeatureCode(featureCode2, 1, replayStartTime);
        });

        // Verify that original timestamps are preserved
        for (EventStore eventBefore : eventsBeforeReplay) {
            EventStore eventAfter = eventStoreRepository.findAll().stream()
                    .filter(e -> e.getEventId().equals(eventBefore.getEventId())
                            && e.getEventType() == eventBefore.getEventType())
                    .findFirst()
                    .orElseThrow();

            // Original timestamps should be preserved
            assertThat(eventAfter.getProcessedAt()).isEqualTo(eventBefore.getProcessedAt());
            assertThat(eventAfter.getEventTimestamp()).isEqualTo(eventBefore.getEventTimestamp());
        }
    }

    // ========== Security Tests ==========

    @Test
    @WithMockOAuth2User(roles = "USER") // Non-ADMIN user
    void shouldBlockNonAdminFromCountEndpoint() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Try to access count endpoint without ADMIN role
        var result = mvc.get()
                .uri("/api/events/replay/count?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return 403 Forbidden
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @WithMockOAuth2User(roles = "USER") // Non-ADMIN user
    void shouldBlockNonAdminFromTimeRangeReplay() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Try to replay by time range without ADMIN role
        var result = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return 403 Forbidden
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @WithMockOAuth2User(roles = "USER") // Non-ADMIN user
    void shouldBlockNonAdminFromFeaturesReplay() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Try to replay by features without ADMIN role
        var result = mvc.post()
                .uri("/api/events/replay/features?startTime={start}&endTime={end}", startTime, endTime)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of("IDEA-1")))
                .exchange();

        // Then: Should return 403 Forbidden
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @WithMockOAuth2User(roles = "USER") // Non-ADMIN user
    void shouldBlockNonAdminFromOperationReplay() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Try to replay by operation type without ADMIN role
        var result = mvc.post()
                .uri("/api/events/replay/operation/CREATED?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return 403 Forbidden
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @WithMockOAuth2User(roles = "USER") // Non-ADMIN user
    void shouldBlockNonAdminFromFeatureCodeReplay() throws Exception {
        // When: Try to replay by feature code without ADMIN role
        var result = mvc.post().uri("/api/events/replay/feature/IDEA-1").exchange();

        // Then: Should return 403 Forbidden
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    void shouldBlockUnauthenticatedAccess() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Try to access without any authentication
        var result = mvc.get()
                .uri("/api/events/replay/count?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return 401 Unauthorized
        assertThat(result).hasStatus4xxClientError();
    }

    // ========== Validation Tests ==========

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldValidateInvalidDateRange() throws Exception {
        // Given: Invalid date range (start after end)
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.minusHours(1); // End before start

        // When: Try to replay with invalid date range
        var result = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return 400 Bad Request
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldValidateTooLargeDateRange() throws Exception {
        // Given: Too large date range (more than 365 days)
        LocalDateTime startTime = LocalDateTime.now().minusDays(400);
        LocalDateTime endTime = LocalDateTime.now();

        // When: Try to replay with too large date range
        var result = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return 400 Bad Request
        assertThat(result).hasStatus4xxClientError();
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldValidateEmptyFeatureCodesList() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);

        // When: Try to replay with empty feature codes list
        var result = mvc.post()
                .uri("/api/events/replay/features?startTime={start}&endTime={end}", startTime, endTime)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of()))
                .exchange();

        // Then: Should return 400 Bad Request
        assertThat(result).hasStatus4xxClientError();
    }

    // ========== Chronological Order Tests ==========

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldReplayEventsInChronologicalOrder() throws Exception {
        // Given: Create a sequence of events
        String createEventId = UUID.randomUUID().toString();
        String updateEventId1 = UUID.randomUUID().toString();
        String updateEventId2 = UUID.randomUUID().toString();

        // Step 1: Create feature
        CreateFeaturePayload createPayload = new CreateFeaturePayload(
                createEventId, "intellij", "Test Feature", "Initial Description", null, "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPayload))
                .exchange();

        assertThat(createResult).hasStatus(HttpStatus.CREATED);
        String location = createResult.getMvcResult().getResponse().getHeader("Location");
        String featureCode = extractFeatureCodeFromLocation(location);

        // Wait for create event to be stored
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        // Step 2: First update
        UpdateFeaturePayload updatePayload1 = new UpdateFeaturePayload(
                updateEventId1, "Updated Title 1", "Updated Description 1", null, "user2", FeatureStatus.IN_PROGRESS);

        mvc.put()
                .uri("/api/features/" + featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload1))
                .exchange();

        // Wait for first update event
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() == 4); // 2 API + 2 EVENT (create, update1)

        // Step 3: Second update
        UpdateFeaturePayload updatePayload2 = new UpdateFeaturePayload(
                updateEventId2, "Updated Title 2", "Updated Description 2", null, "user3", FeatureStatus.RELEASED);

        mvc.put()
                .uri("/api/features/" + featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload2))
                .exchange();

        // Wait for all events to be stored
        await().atMost(10, SECONDS)
                .until(() -> eventStoreRepository.count() == 6); // 3 API + 3 EVENT (create, update1, update2)

        // When: Verify chronological order using direct SQL queries (no repository methods)
        List<Map<String, Object>> eventsInOrder = jdbcTemplate.queryForList(
                """
                SELECT event_id, operation_type, event_timestamp, replay_count
                FROM event_store
                WHERE feature_code = ? AND event_type = 'EVENT'
                ORDER BY event_timestamp ASC
                """,
                featureCode);

        // Then: Verify chronological order via SQL results
        assertThat(eventsInOrder).hasSize(3); // CREATE, UPDATE, UPDATE

        // Verify order by operation type and eventId from SQL results
        assertThat(eventsInOrder.get(0).get("operation_type")).isEqualTo("CREATED");
        assertThat(eventsInOrder.get(0).get("event_id")).isEqualTo(createEventId);

        assertThat(eventsInOrder.get(1).get("operation_type")).isEqualTo("UPDATED");
        assertThat(eventsInOrder.get(1).get("event_id")).isEqualTo(updateEventId1);

        assertThat(eventsInOrder.get(2).get("operation_type")).isEqualTo("UPDATED");
        assertThat(eventsInOrder.get(2).get("event_id")).isEqualTo(updateEventId2);

        // Verify timestamps are in ascending order using SQL results
        Timestamp firstTimestamp = (Timestamp) eventsInOrder.get(0).get("event_timestamp");
        Timestamp secondTimestamp = (Timestamp) eventsInOrder.get(1).get("event_timestamp");
        Timestamp thirdTimestamp = (Timestamp) eventsInOrder.get(2).get("event_timestamp");

        assertThat(firstTimestamp.toLocalDateTime()).isBefore(secondTimestamp.toLocalDateTime());
        assertThat(secondTimestamp.toLocalDateTime()).isBefore(thirdTimestamp.toLocalDateTime());

        // Store replay start time for tracking verification
        LocalDateTime chronologicalReplayStart = LocalDateTime.now();

        // When: Replay events via API (should maintain chronological order)
        var replayResult =
                mvc.post().uri("/api/events/replay/feature/" + featureCode).exchange();

        // Then: Replay should complete successfully
        assertThat(replayResult).hasStatus2xxSuccessful();

        // Verify that replay maintains idempotency by checking event count
        long finalEventCount = eventStoreRepository.count();
        assertThat(finalEventCount).isEqualTo(6); // 3 API + 3 EVENT

        // When: Replay again to test idempotency
        var secondReplayResult =
                mvc.post().uri("/api/events/replay/feature/" + featureCode).exchange();

        // Then: Second replay should also succeed without creating duplicates
        assertThat(secondReplayResult).hasStatus2xxSuccessful();
        assertThat(eventStoreRepository.count()).isEqualTo(finalEventCount); // Should remain same count

        // STRICT VERIFICATION: Check replay tracking via SQL for all events
        verifyReplayTrackingViaSql(createEventId, 2, chronologicalReplayStart); // Replayed twice
        verifyReplayTrackingViaSql(updateEventId1, 2, chronologicalReplayStart); // Replayed twice
        verifyReplayTrackingViaSql(updateEventId2, 2, chronologicalReplayStart); // Replayed twice
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldReplayTimeRangeEventsInChronologicalOrder() throws Exception {
        // Given: Create events across different features
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();
        String eventId3 = UUID.randomUUID().toString();

        LocalDateTime startTime = LocalDateTime.now();

        // Create first feature
        CreateFeaturePayload payload1 =
                new CreateFeaturePayload(eventId1, "intellij", "Feature 1", "Description 1", null, "user1");

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1))
                .exchange();

        // Create second feature
        CreateFeaturePayload payload2 =
                new CreateFeaturePayload(eventId2, "intellij", "Feature 2", "Description 2", null, "user1");

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .exchange();

        // Create third feature
        CreateFeaturePayload payload3 =
                new CreateFeaturePayload(eventId3, "intellij", "Feature 3", "Description 3", null, "user1");

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload3))
                .exchange();

        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);

        // Wait for all events to be stored
        await().atMost(10, SECONDS).until(() -> eventStoreRepository.count() >= 6); // Wait for 3 API + 3 EVENT

        // When: Verify chronological order across features using direct SQL queries
        List<Map<String, Object>> eventsInTimeRange = jdbcTemplate.queryForList(
                """
                SELECT event_id, feature_code, event_timestamp, replay_count
                FROM event_store
                WHERE event_timestamp >= ? AND event_timestamp <= ? AND event_type = 'EVENT'
                ORDER BY event_timestamp ASC
                """,
                startTime,
                endTime);

        // Then: Verify chronological order across different features via SQL
        assertThat(eventsInTimeRange).hasSize(3);

        // Verify timestamps are in ascending order using SQL results
        for (int i = 0; i < eventsInTimeRange.size() - 1; i++) {
            Timestamp currentTimestamp = (Timestamp) eventsInTimeRange.get(i).get("event_timestamp");
            Timestamp nextTimestamp = (Timestamp) eventsInTimeRange.get(i + 1).get("event_timestamp");
            assertThat(currentTimestamp.toLocalDateTime()).isBeforeOrEqualTo(nextTimestamp.toLocalDateTime());
        }

        // When: Count events via API to verify they exist
        var countResult = mvc.get()
                .uri("/api/events/replay/count?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Should return count of events for replay
        assertThat(countResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(3);

        // Store replay start time for tracking verification
        LocalDateTime timeRangeReplayStart = LocalDateTime.now();

        // When: Replay events by time range via API
        var replayResult = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Replay should complete successfully
        assertThat(replayResult).hasStatus2xxSuccessful();

        // Verify idempotency by replaying again
        var secondReplayResult = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        assertThat(secondReplayResult).hasStatus2xxSuccessful();

        // STRICT VERIFICATION: Check replay tracking via SQL for time range events
        String timeRangeEventId1 = (String) eventsInTimeRange.get(0).get("event_id");
        String timeRangeEventId2 = (String) eventsInTimeRange.get(1).get("event_id");
        String timeRangeEventId3 = (String) eventsInTimeRange.get(2).get("event_id");

        verifyReplayTrackingViaSql(timeRangeEventId1, 2, timeRangeReplayStart); // Replayed twice
        verifyReplayTrackingViaSql(timeRangeEventId2, 2, timeRangeReplayStart); // Replayed twice
        verifyReplayTrackingViaSql(timeRangeEventId3, 2, timeRangeReplayStart); // Replayed twice
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldVerifyRepositoryOrderByClause() throws Exception {
        // Given: Create multiple events for the same feature
        String createEventId = UUID.randomUUID().toString();
        String updateEventId = UUID.randomUUID().toString();

        CreateFeaturePayload createPayload =
                new CreateFeaturePayload(createEventId, "intellij", "Test Feature", "Description", null, "user1");

        var createResult = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPayload))
                .exchange();

        String featureCode = extractFeatureCodeFromLocation(
                createResult.getMvcResult().getResponse().getHeader("Location"));

        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 2); // Wait for 1 API + 1 EVENT

        UpdateFeaturePayload updatePayload = new UpdateFeaturePayload(
                updateEventId, "Updated Title", "Updated Description", null, "user2", FeatureStatus.IN_PROGRESS);

        mvc.put()
                .uri("/api/features/" + featureCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
                .exchange();

        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 4); // Wait for 2 API + 2 EVENT

        // When: Verify ORDER BY event_timestamp ASC using helper method
        verifyChronologicalOrderViaSql(featureCode);

        // Additional verification of operation types order
        List<Map<String, Object>> eventsByFeatureSql = jdbcTemplate.queryForList(
                """
                SELECT operation_type
                FROM event_store
                WHERE feature_code = ? AND event_type = 'EVENT'
                ORDER BY event_timestamp ASC
                """,
                featureCode);

        assertThat(eventsByFeatureSql).hasSize(2);
        assertThat(eventsByFeatureSql.get(0).get("operation_type")).isEqualTo("CREATED");
        assertThat(eventsByFeatureSql.get(1).get("operation_type")).isEqualTo("UPDATED");

        LocalDateTime timeStart = LocalDateTime.now().minusHours(1);
        LocalDateTime timeEnd = LocalDateTime.now().plusHours(1);

        // When: Count events via API to verify they exist
        var countResult = mvc.get()
                .uri("/api/events/replay/count?startTime={start}&endTime={end}", timeStart, timeEnd)
                .exchange();

        // Then: Should find exactly 2 events (CREATE and UPDATE)
        assertThat(countResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(2);

        // When: Replay events by operation type (CREATED first)
        var createdReplayResult = mvc.post()
                .uri("/api/events/replay/operation/CREATED?startTime={start}&endTime={end}", timeStart, timeEnd)
                .exchange();

        // Then: Should replay CREATED events successfully
        assertThat(createdReplayResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.operationType")
                .isEqualTo("CREATED");

        // When: Replay events by operation type (UPDATED)
        var updatedReplayResult = mvc.post()
                .uri("/api/events/replay/operation/UPDATED?startTime={start}&endTime={end}", timeStart, timeEnd)
                .exchange();

        // Then: Should replay UPDATED events successfully
        assertThat(updatedReplayResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .extractingPath("$.operationType")
                .isEqualTo("UPDATED");

        // Verify that all replays maintain idempotency (no duplicate events created)
        long finalEventCount = eventStoreRepository.count();
        assertThat(finalEventCount).isEqualTo(4); // 2 API + 2 EVENT
    }

    @Test
    @WithMockOAuth2User(roles = "ADMIN")
    void shouldHandleReplayErrorsGracefully() throws Exception {
        // Given: Create valid events and one corrupted event
        String validEventId1 = UUID.randomUUID().toString();
        String validEventId2 = UUID.randomUUID().toString();
        String corruptedEventId = UUID.randomUUID().toString();

        // Create valid events via REST API
        CreateFeaturePayload payload1 =
                new CreateFeaturePayload(validEventId1, "intellij", "Valid Feature 1", "Description 1", null, "user1");
        CreateFeaturePayload payload2 =
                new CreateFeaturePayload(validEventId2, "intellij", "Valid Feature 2", "Description 2", null, "user1");

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1))
                .exchange();

        mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .exchange();

        // Wait for valid events to be stored
        await().atMost(5, SECONDS).until(() -> eventStoreRepository.count() >= 4); // 2 API + 2 EVENT

        // Insert corrupted event directly into database with invalid JSON payload
        jdbcTemplate.update(
                """
                INSERT INTO event_store (event_id, event_type, operation_type, feature_id, feature_code,
                                         event_payload, event_timestamp, processed_at, expires_at, result_data,
                                         replay_count, last_replayed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)
                """,
                corruptedEventId,
                "EVENT",
                "CREATED",
                999L,
                "CORRUPTED-FEATURE",
                "{ invalid json payload }", // Corrupted JSON
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24),
                "test-result");

        // Verify corrupted event exists
        long totalEventsBeforeReplay = eventStoreRepository.count();
        assertThat(totalEventsBeforeReplay).isEqualTo(5); // 2 API + 2 EVENT + 1 corrupted

        LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(5);

        // When: Replay events by time range (should handle corrupted event gracefully)
        var replayResult = mvc.post()
                .uri("/api/events/replay/time-range?startTime={start}&endTime={end}", startTime, endTime)
                .exchange();

        // Then: Replay should complete successfully despite corrupted event
        assertThat(replayResult).hasStatus2xxSuccessful();

        // Verify API response contains correct success/failure counts
        assertThat(replayResult)
                .bodyJson()
                .extractingPath("$.replayedEvents")
                .isEqualTo(2); // 2 valid events successfully replayed

        assertThat(replayResult).bodyJson().extractingPath("$.failedEvents").isEqualTo(1); // 1 corrupted event failed

        assertThat(replayResult)
                .bodyJson()
                .extractingPath("$.totalEvents")
                .isEqualTo(3); // Total 3 events found for replay

        // Verify event details are returned with correct statuses
        assertThat(replayResult)
                .bodyJson()
                .extractingPath("$.eventDetails")
                .asList()
                .hasSize(3); // Total 3 events processed

        // Verify successful events
        assertThat(replayResult)
                .bodyJson()
                .extractingPath("$.eventDetails[?(@.status == 'SUCCESS')]")
                .asList()
                .hasSize(2); // 2 events successfully replayed

        // Verify failed events
        assertThat(replayResult)
                .bodyJson()
                .extractingPath("$.eventDetails[?(@.status == 'FAILED')]")
                .asList()
                .hasSize(1); // 1 event failed

        // Verify failed event details
        assertThat(replayResult)
                .bodyJson()
                .extractingPath("$.eventDetails[?(@.status == 'FAILED')].featureCode")
                .asList()
                .contains("CORRUPTED-FEATURE");

        // STRICT VERIFICATION: Check that valid events were replayed but corrupted event was skipped
        await().atMost(5, SECONDS).untilAsserted(() -> {
            // Verify valid events have updated replay tracking
            List<Map<String, Object>> validEvents = jdbcTemplate.queryForList(
                    """
                    SELECT event_id, replay_count, last_replayed_at, replay_status
                    FROM event_store
                    WHERE event_id IN (?, ?) AND event_type = 'EVENT'
                    """,
                    validEventId1,
                    validEventId2);

            assertThat(validEvents).hasSize(2);
            for (Map<String, Object> event : validEvents) {
                Integer replayCount = (Integer) event.get("replay_count");
                Timestamp lastReplayedAt = (Timestamp) event.get("last_replayed_at");
                String replayStatus = (String) event.get("replay_status");

                assertThat(replayCount).isEqualTo(1); // Successfully replayed
                assertThat(lastReplayedAt).isNotNull(); // Tracking updated
                assertThat(replayStatus).isEqualTo("SUCCESS"); // Marked as successful
            }

            // Verify corrupted event still exists but was not replayed (replay_count = 0)
            List<Map<String, Object>> corruptedEvents = jdbcTemplate.queryForList(
                    """
                    SELECT replay_count, last_replayed_at, replay_status
                    FROM event_store
                    WHERE event_id = ? AND event_type = 'EVENT'
                    """,
                    corruptedEventId);

            assertThat(corruptedEvents).hasSize(1);
            Map<String, Object> corruptedEvent = corruptedEvents.get(0);

            Integer corruptedReplayCount = (Integer) corruptedEvent.get("replay_count");
            Timestamp corruptedLastReplayedAt = (Timestamp) corruptedEvent.get("last_replayed_at");
            String corruptedReplayStatus = (String) corruptedEvent.get("replay_status");

            assertThat(corruptedReplayCount).isEqualTo(0); // Not successfully replayed
            assertThat(corruptedLastReplayedAt).isNotNull(); // But attempt was tracked
            assertThat(corruptedReplayStatus).isEqualTo("FAILED"); // Marked as failed
        });

        // Verify total event count remains the same (no new events created)
        long totalEventsAfterReplay = eventStoreRepository.count();
        assertThat(totalEventsAfterReplay).isEqualTo(totalEventsBeforeReplay);
    }

    /**
     * Helper method to extract feature code from Location header
     */
    private String extractFeatureCodeFromLocation(String location) {
        if (location == null) return null;
        Pattern pattern = Pattern.compile("/api/features/([^/]+)$");
        Matcher matcher = pattern.matcher(location);
        return matcher.find() ? matcher.group(1) : null;
    }
}
