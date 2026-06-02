package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.WithMockOAuth2User;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Tests that verify Kafka topic receives messages directly.
 *
 * Reads from Kafka topic after publishing to prove the implementation uses Kafka.
 * Uses assign() + offsetsForTimes() to reliably read only messages published during the test.
 */
@WithMockOAuth2User(username = "kafka-topic-test-user")
class FeatureUsageKafkaTopicTest extends FeatureUsageAbstractIT {

    private static final Logger log = LoggerFactory.getLogger(FeatureUsageKafkaTopicTest.class);

    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${ft.events.feature-usage}")
    private String featureUsageTopic;

    private KafkaConsumer<String, String> createConsumer() {
        // Get bootstrap servers from KafkaContainer bean (Testcontainers dynamic port)
        String bootstrapServers = kafkaContainer.getBootstrapServers();
        log.info("Using Kafka bootstrap servers: {}", bootstrapServers);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-topic-verifier-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    /**
     * Polls Kafka topic using assign() + timestamp-based seek for reliability.
     * Searches for a message containing expectedText, published after startTimestamp.
     */
    private boolean pollUntilFound(String expectedText, long startTimestamp, long timeoutMs) {
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            List<PartitionInfo> partitions = consumer.partitionsFor(featureUsageTopic, Duration.ofSeconds(5));
            if (partitions == null || partitions.isEmpty()) {
                log.warn("No partitions for topic '{}'", featureUsageTopic);
                return false;
            }

            List<TopicPartition> topicPartitions = partitions.stream()
                    .map(p -> new TopicPartition(featureUsageTopic, p.partition()))
                    .toList();

            consumer.assign(topicPartitions);

            // Seek to the position matching startTimestamp for each partition
            Map<TopicPartition, Long> timestampsToSearch = new HashMap<>();
            topicPartitions.forEach(tp -> timestampsToSearch.put(tp, startTimestamp));

            Map<TopicPartition, OffsetAndTimestamp> offsets =
                    consumer.offsetsForTimes(timestampsToSearch, Duration.ofSeconds(5));

            for (TopicPartition tp : topicPartitions) {
                OffsetAndTimestamp ots = offsets.get(tp);
                if (ots != null) {
                    consumer.seek(tp, ots.offset());
                    log.info("Partition {} seeking to offset {} (ts={})", tp, ots.offset(), startTimestamp);
                } else {
                    consumer.seekToEnd(Collections.singletonList(tp));
                    log.info("Partition {} seeking to end (no offset for ts={})", tp, startTimestamp);
                }
            }

            long deadline = System.currentTimeMillis() + timeoutMs;
            int totalRecords = 0;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                totalRecords += records.count();
                for (var record : records) {
                    log.info("Kafka record offset={} value={}", record.offset(), record.value());
                    if (record.value() != null && record.value().contains(expectedText)) {
                        log.info("Found matching record for '{}'", expectedText);
                        return true;
                    }
                }
            }
            log.warn(
                    "Did not find '{}' after {} records from topic '{}'",
                    expectedText,
                    totalRecords,
                    featureUsageTopic);
            return false;
        }
    }

    @Test
    void shouldPublishMessageToKafkaTopicWhenUsageApiIsCalled() {
        String uniqueFeatureCode =
                "KAFKA-VERIFY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long before = System.currentTimeMillis();

        // When: HTTP POST /api/usage - event published to Kafka asynchronously
        createFeatureUsageViaAPI("FEATURE_VIEWED", uniqueFeatureCode, "KAFKA-PROD");

        // Then: message is present in Kafka topic (proving Kafka was used, not sync DB write)
        boolean found = pollUntilFound(uniqueFeatureCode, before, 15_000);
        assertThat(found)
                .as("Expected Kafka message for featureCode '%s' in topic '%s'", uniqueFeatureCode, featureUsageTopic)
                .isTrue();
    }

    @Test
    void shouldIncludeEventIdInKafkaMessage() {
        String uniqueFeatureCode =
                "KAFKA-EVID-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long before = System.currentTimeMillis();

        // When: HTTP POST /api/usage
        createFeatureUsageViaAPI("FEATURE_CREATED", uniqueFeatureCode, "KAFKA-EVID-PROD");

        // Then: Kafka message contains eventId for deduplication
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            List<PartitionInfo> partitions = consumer.partitionsFor(featureUsageTopic, Duration.ofSeconds(5));
            List<TopicPartition> topicPartitions = partitions.stream()
                    .map(p -> new TopicPartition(featureUsageTopic, p.partition()))
                    .toList();
            consumer.assign(topicPartitions);

            Map<TopicPartition, Long> ts = new HashMap<>();
            topicPartitions.forEach(tp -> ts.put(tp, before));
            Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(ts, Duration.ofSeconds(5));
            for (TopicPartition tp : topicPartitions) {
                OffsetAndTimestamp ots = offsets.get(tp);
                if (ots != null) consumer.seek(tp, ots.offset());
                else consumer.seekToEnd(Collections.singletonList(tp));
            }

            String matchingValue = null;
            long deadline = System.currentTimeMillis() + 15_000;
            while (matchingValue == null && System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value() != null && record.value().contains(uniqueFeatureCode)) {
                        matchingValue = record.value();
                        break;
                    }
                }
            }

            assertThat(matchingValue)
                    .as("Expected Kafka message for featureCode '%s'", uniqueFeatureCode)
                    .isNotNull();
            assertThat(matchingValue).contains("eventId");
            assertThat(matchingValue).contains("FEATURE_CREATED");
            assertThat(matchingValue).contains("kafka-topic-test-user");
            assertThat(matchingValue).contains("KAFKA-EVID-PROD");
        }
    }

    @Test
    void shouldPublishKafkaMessageWhenViewingProductViaApi() {
        long before = System.currentTimeMillis();

        // When: GET /api/products/intellij - real product API that triggers PRODUCT_VIEWED logUsage
        var result = mvc.get().uri("/api/products/intellij").exchange();
        assertThat(result).hasStatusOk();

        // Then: PRODUCT_VIEWED event for 'intellij' is published to Kafka topic
        // (proves that GET /api/products/ uses Kafka, not sync DB)
        boolean found = pollUntilFound("intellij", before, 15_000);
        assertThat(found)
                .as(
                        "Expected Kafka message with productCode 'intellij' (PRODUCT_VIEWED) in topic '%s'",
                        featureUsageTopic)
                .isTrue();
    }

    @Test
    void shouldPublishKafkaMessageWhenViewingFeatureViaApi() {
        long before = System.currentTimeMillis();

        // When: GET /api/features/IDEA-1 - real feature API that triggers FEATURE_VIEWED logUsage
        var result = mvc.get().uri("/api/features/IDEA-1").exchange();
        assertThat(result).hasStatusOk();

        // Then: FEATURE_VIEWED event for 'IDEA-1' is published to Kafka topic
        // (proves that GET /api/features/ uses Kafka, not sync DB)
        boolean found = pollUntilFound("IDEA-1", before, 15_000);
        assertThat(found)
                .as(
                        "Expected Kafka message with featureCode 'IDEA-1' (FEATURE_VIEWED) in topic '%s'",
                        featureUsageTopic)
                .isTrue();
    }

    @Test
    void shouldPublishKafkaMessageWhenCreatingProductViaApi() {
        long before = System.currentTimeMillis();
        String uniqueProductCode =
                "KT-PROD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // When: POST /api/products - real product creation API triggers PRODUCT_CREATED logUsage
        var result = mvc.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                        """
                        {
                            "code": "%s",
                            "prefix": "KTP",
                            "name": "Kafka Test Product %s",
                            "description": "Test",
                            "imageUrl": "https://example.com/img.png"
                        }
                        """,
                        uniqueProductCode, uniqueProductCode))
                .exchange();
        assertThat(result).hasStatus(201);

        // Then: PRODUCT_CREATED event is published to Kafka topic
        boolean found = pollUntilFound(uniqueProductCode, before, 15_000);
        assertThat(found)
                .as(
                        "Expected Kafka message with productCode '%s' (PRODUCT_CREATED) in topic '%s'",
                        uniqueProductCode, featureUsageTopic)
                .isTrue();
    }
}
