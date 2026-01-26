package com.sivalabs.ft.features.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.config.event.ResilientEventConfig;
import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(ResilientEventConfig.class)
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
public class FeatureEventKafkaIntegrationTest extends AbstractIT {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    static List<String> syncExecuted = new CopyOnWriteArrayList<>();
    static List<String> asyncExecuted = new CopyOnWriteArrayList<>();

    @Value("${ft.events.new-features}")
    private String newFeaturesTopic;

    @Value("${ft.events.updated-features}")
    private String updatedFeaturesTopic;

    @Value("${ft.events.deleted-features}")
    private String deletedFeaturesTopic;

    @DynamicPropertySource
    static void registerKafkaTopics(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.consumer.group-id", () -> "feature-kafka-test-group-" + System.currentTimeMillis());
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
        @KafkaListener(topics = "${ft.events.new-features}", groupId = "sync-test-group-${random.uuid}")
        public void handleCreated(FeatureCreatedEvent event) {
            syncExecuted.add("Created-" + event.id());
        }

        @KafkaListener(topics = "${ft.events.updated-features}", groupId = "sync-test-group-${random.uuid}")
        public void handleUpdated(FeatureUpdatedEvent event) {
            syncExecuted.add("Updated-" + event.id());
        }

        @KafkaListener(topics = "${ft.events.deleted-features}", groupId = "sync-test-group-${random.uuid}")
        public void handleDeleted(FeatureDeletedEvent event) {
            syncExecuted.add("Deleted-" + event.id());
        }
    }

    static class TestKafkaAsyncListener {
        @Async("asyncEventExecutor")
        @KafkaListener(topics = "${ft.events.new-features}", groupId = "async-test-group-${random.uuid}")
        public void handleCreatedAsync(FeatureCreatedEvent event) {
            if (event.id().equals(998L)) {
                throw new RuntimeException("Intentional failure for async test");
            }
            asyncExecuted.add("AsyncCreated-" + event.id());
        }

        @Async("asyncEventExecutor")
        @KafkaListener(topics = "${ft.events.updated-features}", groupId = "async-test-group-${random.uuid}")
        public void handleUpdatedAsync(FeatureUpdatedEvent event) {
            asyncExecuted.add("AsyncUpdated-" + event.id());
        }

        @Async("asyncEventExecutor")
        @KafkaListener(topics = "${ft.events.deleted-features}", groupId = "async-test-group-${random.uuid}")
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

    @Test
    void shouldHandleMultipleConsecutiveFailures() {

        kafkaTemplate.send(
                newFeaturesTopic,
                new FeatureCreatedEvent(
                        998L, "codeX", "fail-title", "fail-desc", FeatureStatus.NEW, "releaseCodeX", "admin", "admin", Instant.now()));
        kafkaTemplate.send(
                newFeaturesTopic,
                new FeatureCreatedEvent(
                        1L, "code1", "title1", "desc", FeatureStatus.NEW, "releaseCode1", "admin", "admin", Instant.now()));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(asyncExecuted).doesNotContain("AsyncCreated-998");
            assertThat(asyncExecuted).contains("AsyncCreated-1");
            assertThat(syncExecuted).contains("Created-998", "Created-1");
        });
    }

    @Test
    void shouldPreventThreadPoolExhaustion() {

        int taskCount = 300;
        for (int i = 0; i < taskCount; i++) {
            kafkaTemplate.send(
                    newFeaturesTopic,
                    new FeatureCreatedEvent(
                            (long) i,
                            "code" + i,
                            "title",
                            "desc",
                            FeatureStatus.NEW,
                            "releaseCode" + i,
                            "admin",
                            "admin",
                            Instant.now()));
        }

        Awaitility.await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(asyncExecuted.size()).isEqualTo(taskCount);
        });
    }

    @Test
    void shouldLogAllRequiredErrorDetails() {
        
        Logger logger = (Logger) LoggerFactory.getLogger(ResilientEventConfig.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        FeatureCreatedEvent failEvent = new FeatureCreatedEvent(
                998L, "codeX", "title", "desc", FeatureStatus.NEW, "releaseCodeX", "admin", "admin", Instant.now());

        kafkaTemplate.send(newFeaturesTopic, failEvent);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).isNotEmpty();

            ILoggingEvent errorLog = logs.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Error log has not been created."));

            String message = errorLog.getFormattedMessage();
            assertThat(message).contains("Event listener execution failed");
            assertThat(message).contains("Listener:");
            assertThat(message).contains("Event:");
            assertThat(message).contains("Mode:");
            assertThat(message).contains("Payload:");
            assertThat(message).contains("id=998");
        });

        logger.detachAppender(listAppender);
    }
}
