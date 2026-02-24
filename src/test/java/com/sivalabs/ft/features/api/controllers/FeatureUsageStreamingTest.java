package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.events.FeatureUsageEvent;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * End-to-end integration tests for asynchronous FeatureUsage event streaming via Kafka.
 * <p>
 * All tests go through the full HTTP API → Service → Kafka publish → Kafka consume → DB pipeline.
 * <p>
 * Tests cover:
 * - Kafka producer publishes events successfully (via POST /api/usage)
 * - Kafka consumer processes events in batches
 * - Event deduplication prevents duplicate processing
 * - All ActionType values processed correctly through streaming
 */
@WithMockOAuth2User(username = "streaming-test-user")
class FeatureUsageStreamingTest extends FeatureUsageAbstractIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${ft.events.feature-usage}")
    private String featureUsageTopic;

    @BeforeEach
    void setUp() {
        cleanFeatureUsageTable();
    }

    @Test
    void shouldPublishAndConsumeFeatureUsageEventViaKafka() {
        // When: POST /api/usage - event is published to Kafka async
        createFeatureUsageViaAPI("FEATURE_VIEWED", "FEAT-001", "PROD-001");

        // Then: Kafka consumer processes and persists the event to DB
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("user_id")).isEqualTo("streaming-test-user");
            assertThat(e.get("feature_code")).isEqualTo("FEAT-001");
            assertThat(e.get("action_type")).isEqualTo(ActionType.FEATURE_VIEWED.name());
        });
    }

    @Test
    void shouldProcessBatchOfFeatureUsageEventsViaKafka() {
        // When: multiple POST /api/usage requests - all published to Kafka
        createFeatureUsageViaAPI("FEATURE_VIEWED", "BATCH-FEAT-1", "BATCH-PROD");
        createFeatureUsageViaAPI("FEATURE_UPDATED", "BATCH-FEAT-2", "BATCH-PROD");
        createFeatureUsageViaAPI("FEATURE_CREATED", "BATCH-FEAT-3", "BATCH-PROD");

        // Then: all events processed via batch consumer and persisted
        awaitFeatureUsageCount(3);
    }

    @Test
    void shouldDeduplicateEventsWithSameEventId() {
        // Given: a FeatureUsageEvent
        FeatureUsageEvent event = new FeatureUsageEvent(
                UUID.randomUUID().toString(),
                "streaming-test-user",
                "DEDUP-FEAT",
                "DEDUP-PROD",
                null,
                ActionType.FEATURE_VIEWED,
                Instant.now(),
                null,
                null,
                null);

        // When: publish the SAME event TWICE to Kafka (same eventId = duplicate)
        kafkaTemplate.send(featureUsageTopic, event);
        kafkaTemplate.send(featureUsageTopic, event);

        // Must have exactly 1 record despite 2 publishes
        verifyFeatureUsageCountStaysAt(1);
    }

    @ParameterizedTest
    @EnumSource(ActionType.class)
    void shouldPersistExactActionTypeViaKafkaStreaming(ActionType actionType) throws Exception {
        String featureCode = "AT-FEAT-" + actionType.name();
        String productCode = "AT-PROD-" + actionType.name();

        createFeatureUsageViaAPI(actionType.name(), featureCode, productCode);

        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("user_id")).isEqualTo("streaming-test-user");
            assertThat(e.get("action_type")).isEqualTo(actionType.name());
            assertThat(e.get("feature_code")).isEqualTo(featureCode);
            assertThat(e.get("product_code")).isEqualTo(productCode);
        });
    }

    @Test
    void shouldPersistEventIdForDeduplicationSupport() {
        // When: POST /api/usage
        createFeatureUsageViaAPI("FEATURE_VIEWED", "EVID-FEAT", "EVID-PROD");

        // Then: persisted record has a non-null event_id (UUID)
        awaitFeatureUsageCreated(e -> {
            assertThat(e.get("event_id")).isNotNull();
            assertThat(e.get("user_id")).isEqualTo("streaming-test-user");
            assertThat(e.get("feature_code")).isEqualTo("EVID-FEAT");
        });
    }
}
