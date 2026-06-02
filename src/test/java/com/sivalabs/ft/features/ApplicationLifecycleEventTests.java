package com.sivalabs.ft.features;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Universal integration tests for application startup lifecycle.
 * Verifies that the application starts successfully with proper DB and Kafka connectivity,
 * that the ContextRefreshedEvent is fired exactly once during startup,
 * and that the Kafka producer was warmed up (i.e., a producer was created during startup).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ApplicationLifecycleEventTests.EventCaptureConfig.class})
class ApplicationLifecycleEventTests {

    @DynamicPropertySource
    static void ensureKafkaTopics(DynamicPropertyRegistry registry) {
        TestcontainersConfiguration.postgres.start();
        TestcontainersConfiguration.kafka.start();
        TestcontainersConfiguration.ensureKafkaTopics();
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "4000");
    }

    @TestConfiguration
    static class EventCaptureConfig {
        static final AtomicInteger refreshCount = new AtomicInteger(0);

        @EventListener
        public void onRefresh(ContextRefreshedEvent event) {
            if (event.getApplicationContext().getParent() == null) {
                refreshCount.incrementAndGet();
            }
        }
    }

    @BeforeAll
    static void resetCounters() {
        EventCaptureConfig.refreshCount.set(0);
    }

    @Autowired
    ConfigurableApplicationContext applicationContext;

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void contextLoadsSuccessfully() {
        // If the context loaded, it means:
        // - Database connectivity was verified successfully
        // - Kafka connectivity and topic availability were verified
        assertThat(applicationContext.isRunning()).isTrue();
    }

    @Test
    void contextRefreshedEventFiredExactlyOnce() {
        assertThat(EventCaptureConfig.refreshCount.get()).isEqualTo(1);
    }

    @Test
    void warmUpProducesKafkaRequests() {
        // Micrometer exposes Kafka client metrics only after the producer performs a request.
        boolean hasAnyRequest = false;
        for (var meter : meterRegistry.getMeters()) {
            if (!meter.getId().getName().equals("kafka.producer.request.total")) {
                continue;
            }
            for (var sample : meter.measure()) {
                if (sample.getStatistic() == Statistic.COUNT && sample.getValue() > 0.0) {
                    hasAnyRequest = true;
                    break;
                }
            }
        }
        assertThat(hasAnyRequest)
                .as("Warm-up should create at least one Kafka producer request " + "(kafka.producer.request.total > 0)")
                .isTrue();

        if (LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) instanceof Logger root) {
            @SuppressWarnings("unchecked")
            ListAppender<ILoggingEvent> appender = (ListAppender<ILoggingEvent>) root.getAppender("KAFKA_GUARD");
            assertThat(appender)
                    .as("Expected KAFKA_GUARD ListAppender in logback-test.xml")
                    .isNotNull();
            // If warm-up hits a non-existent topic, Kafka returns UNKNOWN_TOPIC_OR_PARTITION.
            // We treat that as a failed warm-up (metadata error).
            boolean hasUnknownTopic = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m != null)
                    .anyMatch(m -> m.toLowerCase().contains("unknown_topic_or_partition"));
            assertThat(hasUnknownTopic)
                    .as("Warm-up metadata lookup should succeed; UNKNOWN_TOPIC_OR_PARTITION means the "
                            + "requested topic does not exist")
                    .isFalse();
        }
    }
}
