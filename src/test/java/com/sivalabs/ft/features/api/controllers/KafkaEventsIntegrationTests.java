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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.testcontainers.kafka.KafkaContainer;

@WithMockOAuth2User(username = "event-user")
class KafkaEventsIntegrationTests extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ft.events.updated-milestones}")
    private String updatedMilestonesTopic;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void shouldPublishMilestoneDeletedEvent() throws Exception {
        Map<String, Object> expected = getPayloadMap("/api/milestones/Q3-2024");

        Map<String, Object> event = readEvent(updatedMilestonesTopic, () -> {
            var result = mvc.delete().uri("/api/milestones/{code}", "Q3-2024").exchange();
            assertThat(result).hasStatusOk();
        });

        assertThat(event.remove("eventType")).isEqualTo("DELETED");
        assertThat(normalizeMap(event)).isEqualTo(normalizeMap(expected));
    }

    @Test
    void shouldNotPublishMilestoneDeletedEventWhenMilestoneNotFound() throws Exception {
        Map<String, Object> event = readEventOrNull(updatedMilestonesTopic, Duration.ofSeconds(2), () -> {
            var result = mvc.delete()
                    .uri("/api/milestones/{code}", "UNKNOWN-MILESTONE")
                    .exchange();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        });

        assertThat(event).isNull();
    }

    private Map<String, Object> readEvent(String topic, Runnable action) throws Exception {
        Map<String, Object> event = readEventOrNull(topic, Duration.ofSeconds(10), action);
        if (event != null) {
            return event;
        }
        throw new IllegalStateException("No records found for topic");
    }

    private Map<String, Object> readEventOrNull(String topic, Duration timeout, Runnable action) throws Exception {
        try (Consumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(topic));
            waitForAssignment(consumer);
            consumer.seekToEnd(consumer.assignment());
            // seekToEnd is lazy; touching position forces the end offset before publishing.
            consumer.assignment().forEach(consumer::position);
            action.run();
            ConsumerRecord<String, String> record = pollForRecord(consumer, topic, timeout);
            if (record == null) {
                return null;
            }
            return objectMapper.readValue(record.value(), new TypeReference<>() {});
        }
    }

    private Map<String, Object> getPayloadMap(String uri) throws Exception {
        var result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        String body = result.getMvcResult().getResponse().getContentAsString();
        return objectMapper.readValue(body, new TypeReference<>() {});
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
        long assignmentDeadline =
                System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < assignmentDeadline) {
            consumer.poll(Duration.ofMillis(100));
        }
    }

    private ConsumerRecord<String, String> pollForRecord(
            Consumer<String, String> consumer, String topic, Duration timeout) {
        long recordDeadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < recordDeadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, String> record : records.records(topic)) {
                return record;
            }
        }
        return null;
    }

    private Map<String, Object> normalizeMap(Map<String, Object> payload) {
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
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Math.abs(numeric) >= 1_000_000_000d) {
                Instant instant = Math.abs(numeric) >= 1_000_000_000_000d
                        ? Instant.ofEpochMilli(Math.round(numeric))
                        : Instant.ofEpochMilli(Math.round(numeric * 1000));
                return instant.truncatedTo(ChronoUnit.MILLIS).toString();
            }
        }
        return value;
    }
}
