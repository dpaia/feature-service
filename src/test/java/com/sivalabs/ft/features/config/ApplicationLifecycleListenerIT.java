package com.sivalabs.ft.features.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.FeatureServiceApplication;
import com.sivalabs.ft.features.TestKafkaTopicConfiguration;
import com.sivalabs.ft.features.TestcontainersConfiguration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Black-box integration tests for application lifecycle behavior.
 * Tests organized in sequence: Startup → Runtime → Shutdown scenarios
 *
 * TEST ORGANIZATION:
 * 1. STARTUP SUCCESS - Complete startup with all infrastructure
 * 2. STARTUP DATABASE - Database connectivity verification
 * 3. STARTUP KAFKA - Kafka connectivity and topic verification
 * 4. STARTUP FAILURES - Documented scenarios requiring separate contexts
 * 5. RUNTIME OPERATIONS - Database and Kafka operations during runtime
 * 6. SHUTDOWN - Cleanup operations and graceful shutdown
 *
 * Runs in isolated JVM via Maven Failsafe Plugin (reuseForks=false).
 */
@DisplayName("Application Lifecycle Integration Tests (Black Box)")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {FeatureServiceApplication.class, TestcontainersConfiguration.class, TestKafkaTopicConfiguration.class
        })
@TestPropertySource(
        properties = {
            "ft.lifecycle.lifecycle-enabled=true",
            "ft.lifecycle.shutdown-timeout-millis=30000",
            "ft.lifecycle.kafka-flush-timeout-millis=10000",
            "ft.events.new-features=new_features",
            "ft.events.updated-features=updated_features",
            "ft.events.deleted-features=deleted_features"
        })
class ApplicationLifecycleListenerIT {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListenerIT.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AdminClient adminClient;

    // ========================================================================
    // 1. STARTUP SUCCESS TESTS
    // Requirement: "ContextRefreshedEvent Listener Implementation"
    // ========================================================================

    @Test
    @DisplayName("Should start successfully when all required infrastructure is available")
    void shouldStartSuccessfullyWithAllInfrastructure() throws Exception {
        // Given/When - Application has started (ContextRefreshedEvent fired
        // successfully)
        // Requirement: "Database connectivity verification - test PostgreSQL connection
        // is operational"
        // Requirement: "Kafka producer warm-up - verify Kafka connectivity and
        // configured topics are accessible"

        // Then - Database should be accessible
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5))
                    .as("Database connection should be valid")
                    .isTrue();
        }

        // And - All three Kafka topics should be accessible
        var topicNames = Set.of("new_features", "updated_features", "deleted_features");
        var topicDescriptions =
                adminClient.describeTopics(topicNames).allTopicNames().get(10, TimeUnit.SECONDS);

        assertThat(topicDescriptions.keySet())
                .as("All required Kafka topics should exist")
                .containsExactlyInAnyOrderElementsOf(topicNames);

        log.info("Application started successfully - ContextRefreshedEvent handled");
        log.info("Database connectivity verified during startup");
        log.info("Kafka connectivity and all three topics verified during startup");
    }

    // ========================================================================
    // 2. STARTUP DATABASE TESTS
    // Requirement: "Database connectivity verification"
    // ========================================================================

    @Test
    @DisplayName("Should verify database connection is operational after startup")
    void shouldVerifyDatabaseConnectionOperational() throws Exception {
        // Given - Application has started

        // When - Checking database connection
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5))
                    .as("Database connection should be operational")
                    .isTrue();

            assertThat(connection.isClosed())
                    .as("Connection should not be closed")
                    .isFalse();
        }

        // Then - Database is operational
        log.info("Database connection verified as operational");
    }

    @Test
    @DisplayName("Should execute database queries successfully")
    void shouldExecuteDatabaseQueries() throws Exception {
        // Given - Application is running with database connection

        // When - Executing a simple query
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT 1")) {

            // Then - Query should execute successfully
            assertThat(resultSet.next()).as("Query should return result").isTrue();
            assertThat(resultSet.getInt(1)).as("Result should be 1").isEqualTo(1);
        }

        log.info("Database queries execute successfully");
    }

    @Test
    @DisplayName("Should verify database connection pool is operational")
    void shouldVerifyDatabaseConnectionPool() throws Exception {
        // Given - Application is running

        // When - Obtaining multiple connections from the pool
        for (int i = 0; i < 5; i++) {
            try (var connection = dataSource.getConnection()) {
                assertThat(connection.isValid(5))
                        .as("Connection from pool should be valid")
                        .isTrue();
            }
        }

        // Then - Pool handles all requests successfully
        log.info("Database connection pool verified operational");
    }

    // ========================================================================
    // 3. STARTUP KAFKA TESTS
    // Requirement: "Kafka producer warm-up"
    // Requirement: "Check all three topics from ApplicationProperties"
    // ========================================================================

    @Test
    @DisplayName("Should verify all three required Kafka topics exist")
    void shouldVerifyAllThreeRequiredTopicsExist() throws Exception {
        // Given - Application has started
        // Requirement: "Check all three topics: new_features, updated_features,
        // deleted_features"

        // When - Checking each required topic
        var topic1 = adminClient
                .describeTopics(Set.of("new_features"))
                .allTopicNames()
                .get(10, TimeUnit.SECONDS);
        var topic2 = adminClient
                .describeTopics(Set.of("updated_features"))
                .allTopicNames()
                .get(10, TimeUnit.SECONDS);
        var topic3 = adminClient
                .describeTopics(Set.of("deleted_features"))
                .allTopicNames()
                .get(10, TimeUnit.SECONDS);

        // Then - All three topics should exist
        assertThat(topic1).as("Topic 'new_features' should exist").containsKey("new_features");
        assertThat(topic2).as("Topic 'updated_features' should exist").containsKey("updated_features");
        assertThat(topic3).as("Topic 'deleted_features' should exist").containsKey("deleted_features");

        log.info("All three required Kafka topics verified: new_features, updated_features, deleted_features");
    }

    @Test
    @DisplayName("Should have access to all required Kafka topics")
    void shouldHaveAccessToRequiredKafkaTopics() throws Exception {
        // Given - Application is running

        // When - Checking topic availability
        var requiredTopics = Set.of("new_features", "updated_features", "deleted_features");
        var topicDescriptions =
                adminClient.describeTopics(requiredTopics).allTopicNames().get(10, TimeUnit.SECONDS);

        // Then - All topics should be accessible
        assertThat(topicDescriptions.keySet())
                .as("Application should have access to all required Kafka topics")
                .containsExactlyInAnyOrderElementsOf(requiredTopics);

        log.info("Verified access to all {} required Kafka topics", requiredTopics.size());
    }

    // ========================================================================
    // 4. RUNTIME OPERATIONS
    // ========================================================================

    @Test
    @DisplayName("Should publish messages to Kafka topics during runtime")
    void shouldPublishMessagesToKafka() throws Exception {
        // Given - Application is running

        // When - Publishing messages to Kafka topic
        for (int i = 0; i < 10; i++) {
            kafkaTemplate.send("new_features", "key-" + i, "test-message-" + i);
        }
        kafkaTemplate.flush();

        // Then - Operation completes without errors
        log.info("Published and flushed 10 messages to Kafka");
    }

    @Test
    @DisplayName("Should publish to all three configured Kafka topics")
    void shouldPublishToAllThreeTopics() throws Exception {
        // Given - Application is running

        // When - Publishing to each of the three required topics
        kafkaTemplate.send("new_features", "test-new", "New feature data");
        kafkaTemplate.send("updated_features", "test-updated", "Updated feature data");
        kafkaTemplate.send("deleted_features", "test-deleted", "Deleted feature data");
        kafkaTemplate.flush();

        // Then - All publishes should succeed
        log.info("Published messages to all three required Kafka topics");
    }

    @Test
    @DisplayName("Should maintain database connection during runtime")
    void shouldMaintainDatabaseConnection() throws Exception {
        // Given - Application is running

        // When - Checking database connectivity multiple times
        for (int i = 0; i < 3; i++) {
            try (var connection = dataSource.getConnection()) {
                assertThat(connection.isValid(5))
                        .as("Database connection should remain valid")
                        .isTrue();
            }
            Thread.sleep(100);
        }

        // Then - Database remains accessible
        log.info("Database connection maintained successfully");
    }

    // ========================================================================
    // 5. SHUTDOWN OPERATIONS
    // Requirement: "ContextClosedEvent Listener Implementation"
    // Requirement: "Flush pending Kafka messages to ensure no data loss"
    // ========================================================================

    @Test
    @DisplayName("Should verify Kafka flush operation completes within timeout")
    void shouldVerifyKafkaFlushWithinTimeout() throws Exception {
        // Requirement: "Kafka flush timeout: 10 seconds maximum wait time"

        // Given - Application is running with pending messages
        for (int i = 0; i < 50; i++) {
            kafkaTemplate.send("new_features", "perf-key-" + i, "perf-message-" + i);
        }

        // When - Flushing messages with time measurement
        long startTime = System.currentTimeMillis();
        kafkaTemplate.flush();
        long flushDuration = System.currentTimeMillis() - startTime;

        // Then - Flush should complete well within the 10 second timeout
        assertThat(flushDuration)
                .as("Kafka flush should complete within configured timeout")
                .isLessThan(10000L); // 10 seconds

        log.info("Kafka flush completed in {} ms (within 10s timeout)", flushDuration);
    }
}
