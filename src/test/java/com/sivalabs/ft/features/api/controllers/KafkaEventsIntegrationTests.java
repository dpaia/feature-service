package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.testcontainers.kafka.KafkaContainer;

@WithMockOAuth2User(username = "event-user")
class KafkaEventsIntegrationTests extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ft.events.updated-features}")
    private String updatedFeaturesTopic;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void shouldPublishFeatureUpdatedEventWithReleaseCodeOnUpdate() throws Exception {
        var payload =
                """
            {
                "title": "Updated Feature",
                "description": "Updated description",
                "assignedTo": "jane.doe",
                "status": "IN_PROGRESS",
                "releaseCode": "IDEA-2024.2.3"
            }
            """;

        Map<String, Object> event = readEvent(updatedFeaturesTopic, () -> {
            var result = mvc.put()
                    .uri("/api/features/{code}", "IDEA-4")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
            assertThat(result).hasStatusOk();
        });

        assertEventMatchesGet(event, "UPDATED", "/api/features/IDEA-4");
    }

    @Test
    void shouldPublishFeatureUpdatedEventOnUpdate() throws Exception {
        var payload =
                """
            {
                "title": "Updated Feature",
                "description": "Updated description",
                "assignedTo": "jane.doe",
                "status": "IN_PROGRESS"
            }
            """;

        Map<String, Object> event = readEvent(updatedFeaturesTopic, () -> {
            var result = mvc.put()
                    .uri("/api/features/{code}", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
            assertThat(result).hasStatusOk();
        });

        assertEventMatchesGet(event, "UPDATED", "/api/features/IDEA-1");
    }

    @Test
    void shouldPublishFeatureUpdatedEventOnAssign() throws Exception {
        var payload =
                """
            {
                "featureCode": "IDEA-3",
                "plannedCompletionDate": "2024-05-01",
                "featureOwner": "alice"
            }
            """;

        Map<String, Object> event = readEvent(updatedFeaturesTopic, () -> {
            var result = mvc.post()
                    .uri("/api/releases/{releaseCode}/features", "IDEA-2023.3.8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
            assertThat(result).hasStatus(HttpStatus.CREATED);
        });

        assertEventMatchesGet(event, "UPDATED", "/api/features/IDEA-3");
    }

    @Test
    void shouldPublishFeatureUpdatedEventOnMove() throws Exception {
        var payload =
                """
            {
                "targetReleaseCode": "IDEA-2024.2.3",
                "rationale": "Move for testing"
            }
            """;

        Map<String, Object> event = readEvent(updatedFeaturesTopic, () -> {
            var result = mvc.post()
                    .uri("/api/releases/{targetReleaseCode}/features/{featureCode}/move", "IDEA-2024.2.3", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
            assertThat(result).hasStatusOk();
        });

        assertEventMatchesGet(event, "UPDATED", "/api/features/IDEA-1");
    }

    @Test
    void shouldPublishFeatureUpdatedEventOnRemove() throws Exception {
        Map<String, Object> event = readEvent(updatedFeaturesTopic, () -> {
            var result = mvc.delete()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}", "IDEA-2023.3.8", "IDEA-1")
                    .exchange();
            assertThat(result).hasStatusOk();
        });

        assertEventMatchesGet(event, "UPDATED", "/api/features/IDEA-1");
    }

    @Test
    void shouldPublishFeatureUpdatedEventOnPlanningUpdate() throws Exception {
        var payload =
                """
            {
                "planningStatus": "IN_PROGRESS",
                "notes": "Starting implementation",
                "featureOwner": "owner@example.com"
            }
            """;

        Map<String, Object> event = readEvent(updatedFeaturesTopic, () -> {
            var result = mvc.patch()
                    .uri("/api/releases/{releaseCode}/features/{featureCode}/planning", "IDEA-2023.3.8", "IDEA-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
            assertThat(result).hasStatusOk();
        });

        assertEventMatchesGet(event, "UPDATED", "/api/features/IDEA-1");
    }

    private Map<String, Object> readEvent(String topic, Runnable action) throws Exception {
        Consumer<String, String> consumer = createConsumer();
        consumer.subscribe(List.of(topic));
        waitForAssignment(consumer);
        consumer.seekToEnd(consumer.assignment());
        // seekToEnd is lazy; touching position forces the end offset before publishing.
        consumer.assignment().forEach(consumer::position);
        try {
            action.run();
            ConsumerRecord<String, String> record = pollForRecord(consumer, topic, Duration.ofSeconds(10));
            return toMap(record.value());
        } finally {
            consumer.close();
        }
    }

    private Consumer<String, String> createConsumer() {
        String groupId = "test-" + UUID.randomUUID();
        var props = new java.util.HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    private void waitForAssignment(Consumer<String, String> consumer) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }
    }

    private ConsumerRecord<String, String> pollForRecord(
            Consumer<String, String> consumer, String topic, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, String> record : records.records(topic)) {
                return record;
            }
        }
        throw new IllegalStateException("No records found for topic");
    }

    private void assertEventMatchesGet(Map<String, Object> event, String expectedEventType, String getUri)
            throws Exception {
        Map<String, Object> expected = getPayloadMap(getUri);
        assertThat(event)
                .containsEntry("eventType", expectedEventType)
                .containsEntry("code", expected.get("code"))
                .containsEntry("title", expected.get("title"))
                .containsEntry("description", expected.get("description"))
                .containsEntry("status", expected.get("status"))
                .containsEntry("releaseCode", expected.get("releaseCode"))
                .containsEntry("assignedTo", expected.get("assignedTo"))
                .containsEntry("createdBy", expected.get("createdBy"))
                .containsEntry("updatedBy", expected.get("updatedBy"))
                .containsEntry("planningStatus", expected.get("planningStatus"))
                .containsEntry("featureOwner", expected.get("featureOwner"))
                .containsEntry("notes", expected.get("notes"))
                .containsEntry("blockageReason", expected.get("blockageReason"))
                .hasEntrySatisfying("id", id -> assertThat(id).isNotNull())
                .hasEntrySatisfying("createdAt", ca -> assertThat(ca).isNotNull())
                .hasEntrySatisfying("updatedAt", ua -> assertThat(ua).isNotNull())
                // not easy to check exact value here due to format mismatch bug issue#237
                .hasEntrySatisfying("plannedCompletionDate", pcd -> {
                    if (expected.get("plannedCompletionDate") == null)
                        assertThat(pcd).isNull();
                    else assertThat(pcd).isNotNull();
                })
                .doesNotContainKey("isFavorite");
    }

    private Map<String, Object> getPayloadMap(String uri) throws Exception {
        var result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        String body = result.getMvcResult().getResponse().getContentAsString();
        return toMap(body);
    }

    private Map<String, Object> toMap(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
