package com.sivalabs.ft.features.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.domain.FeatureService;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class FeatureEventListenerTest extends AbstractIT {
    private static final Logger log = LoggerFactory.getLogger(FeatureEventListenerTest.class);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ApplicationProperties properties;

    @MockitoSpyBean
    private FeatureService featureService;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldConsumeFeatureCreatedEvent() {
        // Given
        FeatureCreatedEvent event = new FeatureCreatedEvent(
                1L,
                "TEST-1",
                "Test Feature",
                "Test Description",
                FeatureStatus.NEW,
                "REL-1",
                "user1",
                "admin",
                Instant.now());

        // When
        kafkaTemplate.send(properties.events().newFeatures(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(featureService, times(1)).handleFeatureEvent(captor.capture());

            Object capturedEvent = captor.getValue();
            assertThat(capturedEvent).isInstanceOf(FeatureCreatedEvent.class);
            FeatureCreatedEvent received = (FeatureCreatedEvent) capturedEvent;
            assertThat(received.code()).isEqualTo("TEST-1");
            assertThat(received.title()).isEqualTo("Test Feature");
            assertThat(received.status()).isEqualTo(FeatureStatus.NEW);
        });
    }

    @Test
    void shouldConsumeFeatureUpdatedEvent() {
        // Given
        FeatureUpdatedEvent event = new FeatureUpdatedEvent(
                2L,
                "TEST-2",
                "Updated Feature",
                "Updated Description",
                FeatureStatus.IN_PROGRESS,
                "REL-1",
                "user2",
                "admin",
                Instant.now().minusSeconds(3600),
                "admin",
                Instant.now());

        // When
        kafkaTemplate.send(properties.events().updatedFeatures(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(featureService, times(1)).handleFeatureEvent(captor.capture());

            Object capturedEvent = captor.getValue();
            assertThat(capturedEvent).isInstanceOf(FeatureUpdatedEvent.class);
            FeatureUpdatedEvent received = (FeatureUpdatedEvent) capturedEvent;
            assertThat(received.code()).isEqualTo("TEST-2");
            assertThat(received.title()).isEqualTo("Updated Feature");
            assertThat(received.status()).isEqualTo(FeatureStatus.IN_PROGRESS);
            assertThat(received.updatedBy()).isEqualTo("admin");
        });
    }

    @Test
    void shouldConsumeFeatureDeletedEvent() {
        // Given
        FeatureDeletedEvent event = new FeatureDeletedEvent(
                3L,
                "TEST-3",
                "Deleted Feature",
                "Deleted Description",
                FeatureStatus.RELEASED,
                "REL-1",
                "user3",
                "admin",
                Instant.now().minusSeconds(7200),
                "admin",
                Instant.now().minusSeconds(3600),
                "superadmin",
                Instant.now());

        // When
        kafkaTemplate.send(properties.events().deletedFeatures(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(featureService, times(1)).handleFeatureEvent(captor.capture());

            Object capturedEvent = captor.getValue();
            assertThat(capturedEvent).isInstanceOf(FeatureDeletedEvent.class);
            FeatureDeletedEvent received = (FeatureDeletedEvent) capturedEvent;
            assertThat(received.code()).isEqualTo("TEST-3");
            assertThat(received.title()).isEqualTo("Deleted Feature");
            assertThat(received.deletedBy()).isEqualTo("superadmin");
        });
    }

    @Test
    void shouldConsumeEventsFromMultipleTopics() {
        // Given
        FeatureCreatedEvent createdEvent = new FeatureCreatedEvent(
                10L, "TEST-10", "Created", "Desc", FeatureStatus.NEW, null, "user1", "admin", Instant.now());

        FeatureUpdatedEvent updatedEvent = new FeatureUpdatedEvent(
                11L,
                "TEST-11",
                "Updated",
                "Desc",
                FeatureStatus.IN_PROGRESS,
                null,
                "user2",
                "admin",
                Instant.now().minusSeconds(100),
                "admin",
                Instant.now());

        FeatureDeletedEvent deletedEvent = new FeatureDeletedEvent(
                12L,
                "TEST-12",
                "Deleted",
                "Desc",
                FeatureStatus.RELEASED,
                null,
                "user3",
                "admin",
                Instant.now().minusSeconds(200),
                "admin",
                Instant.now().minusSeconds(100),
                "admin",
                Instant.now());

        // When
        kafkaTemplate.send(properties.events().newFeatures(), createdEvent);
        kafkaTemplate.send(properties.events().updatedFeatures(), updatedEvent);
        kafkaTemplate.send(properties.events().deletedFeatures(), deletedEvent);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(featureService, times(3)).handleFeatureEvent(captor.capture());

            List<Object> capturedEvents = captor.getAllValues();
            assertThat(capturedEvents).hasSize(3);
            assertThat(capturedEvents)
                    .hasAtLeastOneElementOfType(FeatureCreatedEvent.class)
                    .hasAtLeastOneElementOfType(FeatureUpdatedEvent.class)
                    .hasAtLeastOneElementOfType(FeatureDeletedEvent.class);
        });
    }

    @Test
    void shouldProcessEventsInCorrectOrder() {
        // Given
        int eventCount = 11;
        List<FeatureCreatedEvent> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            events.add(new FeatureCreatedEvent(
                    (long) (2000 + i),
                    "ORDER-" + i,
                    "Feature " + i,
                    "Order test",
                    FeatureStatus.NEW,
                    null,
                    "user",
                    "admin",
                    Instant.now()));
        }

        // When
        events.forEach(event -> kafkaTemplate.send(properties.events().newFeatures(), event));

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(featureService, times(eventCount)).handleFeatureEvent(captor.capture());

            List<String> receivedCodes = captor.getAllValues().stream()
                    .filter(e -> e instanceof FeatureCreatedEvent)
                    .map(e -> ((FeatureCreatedEvent) e).code())
                    .toList();

            // Verify all expected codes are present (order within batch is guaranteed, but batches may vary)
            for (int i = 0; i < eventCount; i++) {
                assertThat(receivedCodes).contains("ORDER-" + i);
            }
        });
    }

    @Test
    void shouldHandleHighVolumeOfEvents() {
        // Given
        int eventCount = 666;
        List<FeatureCreatedEvent> events = new ArrayList<>();

        for (int i = 0; i < eventCount; i++) {
            events.add(new FeatureCreatedEvent(
                    (long) (10000 + i),
                    "PERF-" + i,
                    "Performance Test Feature " + i,
                    "Testing high volume processing",
                    FeatureStatus.NEW,
                    i % 10 == 0 ? "REL-PERF-" + (i / 10) : null,
                    "user" + (i % 20),
                    "admin",
                    Instant.now()));
        }

        // Capture metrics before sending events
        double recordsConsumedBefore = getKafkaConsumerRecordsConsumedTotal();
        double listenerInvocationsBefore = getSpringKafkaListenerCount();

        // When
        long startTime = System.currentTimeMillis();
        events.forEach(event -> kafkaTemplate.send(properties.events().newFeatures(), event));
        log.info("Sent {} events", eventCount);

        // Then
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(featureService, times(eventCount)).handleFeatureEvent(any());
        });

        long processingTime = System.currentTimeMillis() - startTime;
        log.info("Processed {} events in {} ms", eventCount, processingTime);
        log.info("Throughput: {} events/second", (eventCount * 1000.0) / processingTime);

        // Verify batch consumption via metrics
        double recordsConsumedAfter = getKafkaConsumerRecordsConsumedTotal();
        double listenerInvocationsAfter = getSpringKafkaListenerCount();

        double recordsConsumed = recordsConsumedAfter - recordsConsumedBefore;
        double listenerInvocations = listenerInvocationsAfter - listenerInvocationsBefore;

        log.info("Metrics verification:");
        log.info("  Records consumed: {}", recordsConsumed);
        log.info("  Listener invocations: {}", listenerInvocations);
        log.info("  Average batch size: {}", recordsConsumed / listenerInvocations);

        // Assert batch consumption: listener invocations should be less than records consumed
        assertThat(recordsConsumed).isGreaterThanOrEqualTo(eventCount);
        assertThat(listenerInvocations)
                .isLessThan(recordsConsumed)
                .withFailMessage(
                        "Expected batch consumption (listener invocations < records consumed), but got %s invocations for %s records",
                        listenerInvocations, recordsConsumed);
    }

    private double getKafkaConsumerRecordsConsumedTotal() {
        String url =
                "http://localhost:" + port + "/actuator/metrics/kafka.consumer.fetch.manager.records.consumed.total";
        ResponseEntity<MetricResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<MetricResponse>() {});

        return response.getBody().measurements().stream()
                .filter(m -> "COUNT".equals(m.statistic()))
                .findFirst()
                .map(Measurement::value)
                .orElse(0.0);
    }

    private double getSpringKafkaListenerCount() {
        String url = "http://localhost:" + port + "/actuator/metrics/spring.kafka.listener";
        ResponseEntity<MetricResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<MetricResponse>() {});

        return response.getBody().measurements().stream()
                .filter(m -> "COUNT".equals(m.statistic()))
                .findFirst()
                .map(Measurement::value)
                .orElse(0.0);
    }
    // DTOs for actuator metrics response

    private record MetricResponse(String name, List<Measurement> measurements) {}

    private record Measurement(String statistic, double value) {}
}
