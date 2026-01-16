package com.sivalabs.ft.features.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.config.event.AsyncEventConfig;
import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(AsyncEventConfig.class)
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FeatureEventKafkaIntegrationTest extends AbstractIT {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    static List<String> syncExecuted = new CopyOnWriteArrayList<>();
    static List<String> asyncExecuted = new CopyOnWriteArrayList<>();

    static String newFeaturesTopic = "new_features_" + UUID.randomUUID();
    static String updatedFeaturesTopic = "updated_features_" + UUID.randomUUID();
    static String deletedFeaturesTopic = "deleted_features_" + UUID.randomUUID();

    static String syncGroupId = "test-group-" + UUID.randomUUID();
    static String asyncGroupId = "test-async-group-" + UUID.randomUUID();

    @DynamicPropertySource
    static void registerKafkaTopics(DynamicPropertyRegistry registry) {
        registry.add("ft.events.new-features", () -> newFeaturesTopic);
        registry.add("ft.events.updated-features", () -> updatedFeaturesTopic);
        registry.add("ft.events.deleted-features", () -> deletedFeaturesTopic);

        registry.add("ft.consumer.sync-group-id", () -> syncGroupId);
        registry.add("ft.consumer.async-group-id", () -> asyncGroupId);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestKafkaListener testKafkaListener() {
            return new TestKafkaListener();
        }

        @Bean
        public TestKafkaAsyncListener testKafkaAsyncListener() {
            return new TestKafkaAsyncListener();
        }
    }

    static class TestKafkaListener {
        @KafkaListener(topics = "${ft.events.new-features}", groupId = "${ft.consumer.sync-group-id}")
        public void handleCreated(FeatureCreatedEvent event) {
            syncExecuted.add("Created-" + event.id());
        }

        @KafkaListener(topics = "${ft.events.updated-features}", groupId = "${ft.consumer.sync-group-id}")
        public void handleUpdated(FeatureUpdatedEvent event) {
            syncExecuted.add("Updated-" + event.id());
        }

        @KafkaListener(topics = "${ft.events.deleted-features}", groupId = "${ft.consumer.sync-group-id}")
        public void handleDeleted(FeatureDeletedEvent event) {
            syncExecuted.add("Deleted-" + event.id());
        }
    }

    static class TestKafkaAsyncListener {
        @Async("asyncEventExecutor")
        @KafkaListener(topics = "${ft.events.new-features}", groupId = "${ft.consumer.async-group-id}")
        public void handleCreatedAsync(FeatureCreatedEvent event) {
            if (event.id().equals(998L)) {
                throw new RuntimeException("Intentional failure for async test");
            }
            asyncExecuted.add("AsyncCreated-" + event.id());
        }

        @Async("asyncEventExecutor")
        @KafkaListener(topics = "${ft.events.updated-features}", groupId = "${ft.consumer.async-group-id}")
        public void handleUpdatedAsync(FeatureUpdatedEvent event) {
            asyncExecuted.add("AsyncUpdated-" + event.id());
        }

        @Async("asyncEventExecutor")
        @KafkaListener(topics = "${ft.events.deleted-features}", groupId = "${ft.consumer.async-group-id}")
        public void handleDeletedAsync(FeatureDeletedEvent event) {
            asyncExecuted.add("AsyncDeleted-" + event.id());
        }
    }

    @BeforeEach
    void setup() throws Exception {
        syncExecuted.clear();
        asyncExecuted.clear();
    }

    @Test
    public void kafkaListenersShouldConsumeEvents() throws InterruptedException {

        kafkaTemplate.send(
                newFeaturesTopic,
                new FeatureCreatedEvent(
                        1L,
                        "code1",
                        "title1",
                        "desc1",
                        FeatureStatus.NEW,
                        "releaseCode1",
                        "admin",
                        "admin",
                        Instant.now()));

        kafkaTemplate.send(
                updatedFeaturesTopic,
                new FeatureUpdatedEvent(
                        2L,
                        "code2",
                        "title2",
                        "desc2",
                        FeatureStatus.IN_PROGRESS,
                        "releaseCode2",
                        "admin",
                        "admin",
                        Instant.now(),
                        "admin",
                        Instant.now()));

        kafkaTemplate.send(
                deletedFeaturesTopic,
                new FeatureDeletedEvent(
                        3L,
                        "code3",
                        "title3",
                        "desc3",
                        FeatureStatus.RELEASED,
                        "releaseCode3",
                        "admin",
                        "admin",
                        Instant.now(),
                        "admin",
                        Instant.now(),
                        "admin",
                        Instant.now()));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(syncExecuted).containsExactlyInAnyOrder("Created-1", "Updated-2", "Deleted-3");
                });
    }

    @Test
    public void kafkaSyncAndAsyncListenersShouldProcessEvents() throws InterruptedException {

        kafkaTemplate.send(
                newFeaturesTopic,
                new FeatureCreatedEvent(
                        1L,
                        "code1",
                        "title1",
                        "desc1",
                        FeatureStatus.NEW,
                        "releaseCode1",
                        "admin",
                        "admin",
                        Instant.now()));

        kafkaTemplate.send(
                updatedFeaturesTopic,
                new FeatureUpdatedEvent(
                        2L,
                        "code2",
                        "title2",
                        "desc2",
                        FeatureStatus.IN_PROGRESS,
                        "releaseCode2",
                        "admin",
                        "admin",
                        Instant.now(),
                        "admin",
                        Instant.now()));

        kafkaTemplate.send(
                deletedFeaturesTopic,
                new FeatureDeletedEvent(
                        3L,
                        "code3",
                        "title3",
                        "desc3",
                        FeatureStatus.RELEASED,
                        "releaseCode3",
                        "admin",
                        "admin",
                        Instant.now(),
                        "admin",
                        Instant.now(),
                        "admin",
                        Instant.now()));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(syncExecuted).containsExactlyInAnyOrder("Created-1", "Updated-2", "Deleted-3");
                    assertThat(asyncExecuted)
                            .containsExactlyInAnyOrder("AsyncCreated-1", "AsyncUpdated-2", "AsyncDeleted-3");
                });
    }

    @Test
    public void kafkaAsyncListenerShouldHandleFailures() throws InterruptedException {

        kafkaTemplate.send(
                newFeaturesTopic,
                new FeatureCreatedEvent(
                        1L,
                        "code1",
                        "title1",
                        "desc1",
                        FeatureStatus.NEW,
                        "releaseCode1",
                        "admin",
                        "admin",
                        Instant.now()));

        kafkaTemplate.send(
                newFeaturesTopic,
                new FeatureCreatedEvent(
                        998L,
                        "codeX",
                        "fail-title",
                        "fail-desc",
                        FeatureStatus.NEW,
                        "releaseCodeX",
                        "admin",
                        "admin",
                        Instant.now()));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(syncExecuted).contains("Created-1");
                    assertThat(asyncExecuted).contains("AsyncCreated-1").doesNotContain("AsyncCreated-998");
                });
    }
}
