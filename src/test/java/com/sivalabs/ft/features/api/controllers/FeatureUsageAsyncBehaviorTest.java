package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.Lifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

/**
 * Tests that verify async non-blocking behavior of FeatureUsage event processing.
 *
 * Kafka consumer is intentionally disabled via test properties to eliminate
 * race conditions and prove that HTTP 202 response is returned WITHOUT waiting
 * for DB persistence (which is done asynchronously by the Kafka consumer).
 *
 * With consumer disabled:
 *  - HTTP POST returns 202 immediately
 *  - DB count stays 0 (consumer never processes the Kafka message)
 *  - This conclusively proves the HTTP response is non-blocking
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "ft.events.feature-usage=feature-usage-async-behavior-test-${random.uuid}"
        })
@WithMockOAuth2User
class FeatureUsageAsyncBehaviorTest extends FeatureUsageAbstractIT {

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @BeforeEach
    void setUp() {
        cleanFeatureUsageTable();
    }

    @Test
    void shouldReturn202WithoutWaitingForDbPersistence() {
        // Verify consumer is truly not running (guard against auto-startup property being ignored)
        assertThat(kafkaListenerEndpointRegistry.getListenerContainers().stream()
                        .noneMatch(Lifecycle::isRunning))
                .as("All Kafka listener containers must be stopped (auto-startup=false)")
                .isTrue();

        // When: POST /api/usage with Kafka consumer disabled
        createFeatureUsageViaAPI("FEATURE_VIEWED", "NONBLOCKING-FEAT", "NONBLOCKING-PROD");

        // And: DB count is still 0 because consumer is disabled (no Kafka processing happened)
        // This conclusively proves the HTTP response did NOT block on DB persistence
        verifyFeatureUsageCountStaysAt(0);
    }
}
