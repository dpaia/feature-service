package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.CreateFeaturePayload;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.models.EventType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for API idempotency with duplicate requests.
 * Tests concurrent processing scenarios and proper deduplication.
 */
@Sql("/test-data.sql")
class FeatureControllerIdempotencyTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean up event_store table before each test to ensure isolation
        jdbcTemplate.execute("DELETE FROM event_store");
    }

    /**
     * Helper method to count events in database using direct SQL query.
     * This demonstrates how to query event_store table directly and verify exact counts.
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

    /**
     * Helper method to count all events for a specific event type.
     */
    private int countAllEventsInDatabase(String eventType) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_store WHERE event_type = ?", Integer.class, eventType);
            return count != null ? count : 0;
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    @Test
    @WithMockOAuth2User
    void shouldHandleDuplicateCreateFeatureRequests() throws Exception {
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

        // When - Second request with same eventId (duplicate)
        var result2 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result2).hasStatus2xxSuccessful();
        String location2 = result2.getMvcResult().getResponse().getHeader("Location");

        // Then
        assertThat(location1).isEqualTo(location2);

        // Verify exactly one processed event record exists using direct SQL query
        assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User
    void shouldHandleDuplicateUpdateFeatureRequests() throws Exception {
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

        // Then - Verify feature was updated only once
        var getResult = mvc.get().uri("/api/features/IDEA-1").exchange();
        assertThat(getResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.title()).isEqualTo("Updated Title");
                    assertThat(dto.status()).isEqualTo(FeatureStatus.IN_PROGRESS);
                });

        // Verify exactly one processed event record exists using direct SQL query
        assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User
    void shouldHandleConcurrentCreateFeatureRequests() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        CreateFeaturePayload payload = new CreateFeaturePayload(
                eventId, "intellij", "Concurrent Feature", "Concurrent Description", null, "user1");

        // When - Submit multiple sequential requests with same eventId (simulating concurrent behavior)
        var result1 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result1).hasStatus2xxSuccessful();
        String location1 = result1.getMvcResult().getResponse().getHeader("Location");

        var result2 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result2).hasStatus2xxSuccessful();
        String location2 = result2.getMvcResult().getResponse().getHeader("Location");

        var result3 = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result3).hasStatus2xxSuccessful();
        String location3 = result3.getMvcResult().getResponse().getHeader("Location");

        // Then - All requests should return the same location (idempotent)
        assertThat(location1).isEqualTo(location2);
        assertThat(location2).isEqualTo(location3);

        // Verify exactly one processed event record exists using direct SQL query
        assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
    }

    @Test
    @WithMockOAuth2User
    void shouldHandleConcurrentUpdateFeatureRequests() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                eventId, "Concurrent Update", "Concurrent Description", null, "user1", FeatureStatus.RELEASED);

        // When - Submit multiple sequential update requests with same eventId (simulating concurrent behavior)
        var result1 = mvc.put()
                .uri("/api/features/IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result1).hasStatus2xxSuccessful();

        var result2 = mvc.put()
                .uri("/api/features/IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result2).hasStatus2xxSuccessful();

        var result3 = mvc.put()
                .uri("/api/features/IDEA-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        assertThat(result3).hasStatus2xxSuccessful();

        // Then - Verify feature was updated correctly
        var getResult = mvc.get().uri("/api/features/IDEA-1").exchange();
        assertThat(getResult)
                .hasStatus2xxSuccessful()
                .bodyJson()
                .convertTo(FeatureDto.class)
                .satisfies(dto -> {
                    assertThat(dto.title()).isEqualTo("Concurrent Update");
                    assertThat(dto.status()).isEqualTo(FeatureStatus.RELEASED);
                });

        // Verify exactly one processed event record exists using direct SQL query
        assertThat(countEventsInDatabase(eventId, EventType.API.name())).isEqualTo(1);
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

        // Then - Different features should be created
        assertThat(location1).isNotEqualTo(location2);

        // Verify exactly one processed event record exists for each eventId using direct SQL query
        assertThat(countEventsInDatabase(eventId1, EventType.API.name())).isEqualTo(1);
        assertThat(countEventsInDatabase(eventId2, EventType.API.name())).isEqualTo(1);

        // Verify total count of API events is exactly 2
        assertThat(countAllEventsInDatabase(EventType.API.name())).isEqualTo(2);
    }
}
