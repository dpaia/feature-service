package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.testsupport.MockJavaMailSenderConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;

@Import(MockJavaMailSenderConfig.class)
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
class NotificationRealtimeIntegrationTest extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${ft.events.unread-count}")
    private String unreadCountTopic;

    private KafkaConsumer<String, String> unreadCountConsumer;

    @BeforeEach
    void setupConsumer() throws InterruptedException {
        assertThat(unreadCountTopic).isNotNull();

        unreadCountConsumer = createConsumer();

        List<TopicPartition> partitions = waitForPartitions(unreadCountConsumer, unreadCountTopic);
        unreadCountConsumer.assign(partitions);
        unreadCountConsumer.seekToEnd(partitions);

        // Materialize the seek position before producing events
        unreadCountConsumer.poll(Duration.ofMillis(100));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    void shouldPublishUnreadCountEventsToKafka() throws Exception {
        // Given - create a unique recipient user to avoid cross-test event collisions
        String testRecipientUserId = "assignee_" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (username, email) VALUES (?, ?)",
                testRecipientUserId,
                testRecipientUserId + "@company.com");

        // Given - create a notification (unread)
        Map<String, Object> payload = new HashMap<>();
        payload.put("productCode", "intellij");
        payload.put("title", "Realtime Feature");
        payload.put("description", "Test realtime");
        payload.put("releaseCode", null);
        payload.put("assignedTo", testRecipientUserId);
        var createResult = mvc.post()
                .uri("/api/features")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "creator")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payload))
                .exchange();
        assertThat(createResult).hasStatus2xxSuccessful();

        // First event: unread count becomes 1
        JsonNode first = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(first.path("type").asText()).isEqualTo("UnreadCountChanged");
        assertThat(first.path("recipientUserId").asText()).isEqualTo(testRecipientUserId);
        assertThat(first.path("unreadCount").asInt()).isEqualTo(1);

        // When - mark as read
        var notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 1",
                java.util.UUID.class,
                testRecipientUserId);

        mvc.put()
                .uri("/api/notifications/{id}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", testRecipientUserId)))
                .exchange();

        // Then - unread count becomes 0
        JsonNode second = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(second.path("type").asText()).isEqualTo("UnreadCountChanged");
        assertThat(second.path("unreadCount").asInt()).isEqualTo(0);

        // When - mark as read again (no change)
        mvc.put()
                .uri("/api/notifications/{id}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", testRecipientUserId)))
                .exchange();

        // No new events expected (idempotent read should not change unread count)
        verifyNoMoreNotificationsForRecipient(testRecipientUserId);
    }

    @Test
    void shouldPublishUnreadCountEventWhenTrackingPixelMarksAsRead() throws Exception {
        // Given - create a unique recipient user
        String testRecipientUserId = "tracking_user_" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (username, email) VALUES (?, ?)",
                testRecipientUserId,
                testRecipientUserId + "@company.com");

        // Create a notification
        Map<String, Object> payload = new HashMap<>();
        payload.put("productCode", "intellij");
        payload.put("title", "Tracking Pixel Test");
        payload.put("description", "Test tracking pixel");
        payload.put("releaseCode", null);
        payload.put("assignedTo", testRecipientUserId);
        mvc.post()
                .uri("/api/features")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "creator")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        // Consume the creation event
        JsonNode createEvent = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(createEvent.path("unreadCount").asInt()).isEqualTo(1);

        // When - mark as read via tracking pixel (public endpoint, no auth)
        var notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 1",
                java.util.UUID.class,
                testRecipientUserId);

        var trackingResult =
                mvc.get().uri("/notifications/{id}/read", notificationId).exchange();
        assertThat(trackingResult).hasStatus2xxSuccessful();

        // Then - unread count event should be published (1 -> 0)
        JsonNode trackingEvent = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(trackingEvent.path("type").asText()).isEqualTo("UnreadCountChanged");
        assertThat(trackingEvent.path("unreadCount").asInt()).isEqualTo(0);

        // Verify idempotency: second call should not produce another event
        mvc.get().uri("/notifications/{id}/read", notificationId).exchange();
        verifyNoMoreNotificationsForRecipient(testRecipientUserId);
    }

    @Test
    void shouldPublishUnreadCountEventWhenMarkedUnread() throws Exception {
        String testRecipientUserId = "unread_user_" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (username, email) VALUES (?, ?)",
                testRecipientUserId,
                testRecipientUserId + "@company.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("productCode", "intellij");
        payload.put("title", "Unread Test Feature");
        payload.put("description", "Test unread transition");
        payload.put("releaseCode", null);
        payload.put("assignedTo", testRecipientUserId);
        var createResult = mvc.post()
                .uri("/api/features")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "creator")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payload))
                .exchange();
        assertThat(createResult).hasStatus2xxSuccessful();

        JsonNode event1 = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(event1.get("unreadCount").asInt()).isEqualTo(1);

        var notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC LIMIT 1",
                java.util.UUID.class,
                testRecipientUserId);

        mvc.put()
                .uri("/api/notifications/{id}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", testRecipientUserId)))
                .exchange();
        // Expect unread count to drop to 0 after marking as read.
        JsonNode event2 = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(event2.get("unreadCount").asInt()).isEqualTo(0);

        mvc.put()
                .uri("/api/notifications/{id}/unread", notificationId)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", testRecipientUserId)))
                .exchange();
        // Expect unread count to go back to 1 after marking as unread.
        JsonNode event3 = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(event3.get("unreadCount").asInt()).isEqualTo(1);
    }

    @Test
    void shouldPublishUnreadCountEventWhenMarkAllRead() throws Exception {
        String testRecipientUserId = "markall_user_" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (username, email) VALUES (?, ?)",
                testRecipientUserId,
                testRecipientUserId + "@company.com");

        for (int i = 0; i < 2; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("productCode", "intellij");
            payload.put("title", "MarkAll Test Feature " + i);
            payload.put("description", "Test mark-all-read");
            payload.put("releaseCode", null);
            payload.put("assignedTo", testRecipientUserId);
            var createResult = mvc.post()
                    .uri("/api/features")
                    .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "creator")))
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(payload))
                    .exchange();
            assertThat(createResult).hasStatus2xxSuccessful();
        }

        JsonNode count1 = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(count1.get("unreadCount").asInt()).isEqualTo(1);

        JsonNode count2 = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(count2.get("unreadCount").asInt()).isEqualTo(2);

        mvc.patch()
                .uri("/api/notifications/mark-all-read")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", testRecipientUserId)))
                .exchange();

        // Final event after mark-all-read should set unread count to 0.
        JsonNode finalEvent = pollNextNotificationForRecipient(testRecipientUserId);
        assertThat(finalEvent.get("unreadCount").asInt()).isEqualTo(0);
    }

    @Test
    void shouldPublishSingleEventForBatchCreation() throws Exception {
        String recipient = "batch_user_" + UUID.randomUUID();
        String creator = "creator_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (username, email) VALUES (?, ?)", recipient, recipient + "@company.com");
        jdbcTemplate.update("INSERT INTO users (username, email) VALUES (?, ?)", creator, creator + "@company.com");

        // Create a release via API to trigger batch notifications on status change.
        Map<String, Object> releasePayload = new HashMap<>();
        releasePayload.put("productCode", "intellij");
        releasePayload.put("code", "R" + UUID.randomUUID());
        releasePayload.put("description", "Batch notification test");
        var createRelease = mvc.post()
                .uri("/api/releases")
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", creator)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(releasePayload))
                .exchange();
        assertThat(createRelease).hasStatus2xxSuccessful();

        String location = createRelease.getMvcResult().getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        String releaseCode = location.substring(location.lastIndexOf('/') + 1);

        // Create two features in the same release assigned to the same recipient.
        for (int i = 0; i < 2; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("productCode", "intellij");
            payload.put("title", "Batch Feature " + i);
            payload.put("description", "Feature for batch notification");
            payload.put("releaseCode", releaseCode);
            payload.put("assignedTo", recipient);
            var createResult = mvc.post()
                    .uri("/api/features")
                    .with(jwt().jwt(jwt -> jwt.claim("preferred_username", creator)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))
                    .exchange();
            assertThat(createResult).hasStatus2xxSuccessful();
        }

        // Drain/skip unread-count events caused by feature creation.
        unreadCountConsumer.poll(Duration.ofMillis(100));
        unreadCountConsumer.commitSync();
        unreadCountConsumer.poll(Duration.ofMillis(100));

        long currentUnread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ? AND read = false",
                Long.class,
                recipient);

        // Move release through valid transitions to RELEASED to trigger batch notifications.
        updateReleaseStatus(releaseCode, "PLANNED");
        updateReleaseStatus(releaseCode, "IN_PROGRESS");
        updateReleaseStatus(releaseCode, "RELEASED");

        // Only one unread-count event should be emitted for the recipient for this batch.
        JsonNode first = pollNextNotificationForRecipient(recipient);
        assertThat(first.get("unreadCount").asInt()).isEqualTo(currentUnread + 1);

        verifyNoMoreNotificationsForRecipient(recipient);
    }

    private void updateReleaseStatus(String releaseCode, String status) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("description", "Status update to " + status);
        payload.put("status", status);
        payload.put("releasedAt", Instant.now().toString());
        var result = mvc.put()
                .uri("/api/releases/{code}", releaseCode)
                .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "releaseManager")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .exchange();
        assertThat(result).hasStatus2xxSuccessful();
    }

    private KafkaConsumer<String, String> createConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "unread-count-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Increase timeouts for Testcontainers environment
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 60000);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        return new KafkaConsumer<>(props);
    }

    private List<TopicPartition> waitForPartitions(KafkaConsumer<String, String> consumer, String topic)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            List<PartitionInfo> infos = consumer.partitionsFor(topic);
            if (infos != null && !infos.isEmpty()) {
                List<TopicPartition> partitions = new java.util.ArrayList<>();
                for (PartitionInfo info : infos) {
                    partitions.add(new TopicPartition(info.topic(), info.partition()));
                }
                return partitions;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Kafka topic has no partitions: " + topic);
    }

    private JsonNode pollNextNotificationForRecipient(String recipientUserId) throws Exception {
        JsonNode event = pollForRecipient(recipientUserId, 10000);
        assertThat(event).isNotNull();
        return event;
    }

    private void verifyNoMoreNotificationsForRecipient(String recipientUserId) throws Exception {
        JsonNode event = pollForRecipient(recipientUserId, 2000);
        assertThat(event).isNull();
    }

    private JsonNode pollForRecipient(String recipientUserId, int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var records = unreadCountConsumer.poll(Duration.ofMillis(500));
            for (var record : records) {
                JsonNode event = objectMapper.readTree(record.value());
                // Guard against double-serialization bug (JSON wrapped in a JSON string)
                assertThat(event.isTextual())
                        .as("Kafka message should be a JSON object, not a JSON-encoded string")
                        .isFalse();
                if (recipientUserId.equals(event.path("recipientUserId").asText())) {
                    return event;
                }
            }
        }
        return null;
    }
}
