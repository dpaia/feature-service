package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Value("${ft.events.updated-releases}")
    private String updatedReleasesTopic;

    @Value("${ft.events.updated-features}")
    private String updatedFeaturesTopic;

    @Value("${ft.events.updated-milestones}")
    private String updatedMilestonesTopic;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void shouldPublishReleaseUpdatedEvent() throws Exception {
        var payload =
                """
            {
                "description": "Updated release description",
                "status": "RELEASED",
                "releasedAt": "2024-03-30T00:00:00Z",
                "milestoneCode": "Q1-2024"
            }
            """;

        Map<String, Object> event = readEvent(updatedReleasesTopic, () -> {
            var result = mvc.put()
                    .uri("/api/releases/{code}", "IDEA-2023.3.8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
            assertThat(result).hasStatusOk();
        });

        assertEventMatchesGet(event, "UPDATED", "/api/releases/IDEA-2023.3.8");
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
    void shouldPublishFeatureUpdatedEventWithIsoTemporalFields() throws Exception {
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

        Map<String, Object> expected = getPayloadMap("/api/features/IDEA-1");
        assertTemporalFieldMatchesExpected(event, expected, "createdAt");
        assertTemporalFieldMatchesExpected(event, expected, "updatedAt");
    }

    @Test
    void shouldPublishMilestoneUpdatedEvent() throws Exception {
        var payload =
                """
            {
                "name": "Q2 2024 Release Updated",
                "description": "Updated milestone",
                "targetDate": "2024-06-30T23:59:59Z",
                "actualDate": "2024-06-20T10:00:00Z",
                "status": "COMPLETED",
                "owner": "bob@example.com",
                "notes": "Updated for test"
            }
            """;

        Map<String, Object> event = readEvent(updatedMilestonesTopic, () -> {
            var result = mvc.put()
                    .uri("/api/milestones/{code}", "Q2-2024")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .exchange();
            assertThat(result).hasStatusOk();
        });

        assertEventMatchesGet(event, "UPDATED", "/api/milestones/Q2-2024");
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
        assertThat(event.get("eventType")).isEqualTo(expectedEventType);
        event.remove("eventType");
        Map<String, Object> expected = getPayloadMap(getUri);
        assertThat(normalizePayload(event)).isEqualTo(normalizePayload(expected));
    }

    private void assertTemporalFieldMatchesExpected(
            Map<String, Object> event, Map<String, Object> expected, String temporalField) {
        assertThat(event.get(temporalField))
                .as("Kafka event field %s should be serialized as a string", temporalField)
                .isInstanceOf(String.class);
        assertThat(normalizeValue(event.get(temporalField)))
                .as("Kafka event field %s should match GET payload", temporalField)
                .isEqualTo(normalizeValue(expected.get(temporalField)));
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

    private Map<String, Object> normalizePayload(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) normalizeValue(payload);
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new java.util.LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalizeValue).toList();
        }
        if (value instanceof String str) {
            try {
                Instant instant = Instant.parse(str);
                return instant.truncatedTo(ChronoUnit.MILLIS).toString();
            } catch (Exception ignored) {
                return str;
            }
        }
        return value;
    }
}
