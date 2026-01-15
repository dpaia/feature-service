package com.sivalabs.ft.features.config;

import com.sivalabs.ft.features.ApplicationProperties;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * ApplicationLifecycleListener handles Spring ApplicationContext lifecycle
 * events
 * for ContextRefreshedEvent (startup) and ContextClosedEvent (shutdown).
 *
 * On startup:
 * - Verifies database connectivity
 * - Verifies Kafka connectivity and topic availability
 * - Fails fast if critical resources are unavailable
 *
 * On shutdown:
 * - Flushes pending Kafka messages with timeout
 * - Performs graceful cleanup
 */
@Component
@ConditionalOnProperty(value = "ft.lifecycle.lifecycle-enabled", havingValue = "true", matchIfMissing = true)
public class ApplicationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListener.class);

    private final DataSource dataSource;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AdminClient kafkaAdminClient;
    private final ApplicationProperties applicationProperties;

    public ApplicationLifecycleListener(
            DataSource dataSource,
            KafkaTemplate<String, Object> kafkaTemplate,
            AdminClient kafkaAdminClient,
            ApplicationProperties applicationProperties) {
        this.dataSource = dataSource;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdminClient = kafkaAdminClient;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Handles ContextRefreshedEvent to perform startup tasks.
     * Verifies database and Kafka connectivity.
     * Fails fast if critical resources are unavailable.
     */
    @EventListener
    public void onApplicationStartup(ContextRefreshedEvent event) {
        var lifecycle = applicationProperties.lifecycle();

        if (!lifecycle.lifecycleEnabled()) {
            log.info("Startup resource verification is disabled");
            return;
        }

        log.info("Application startup - performing resource initialization checks");

        try {
            verifyDatabaseConnectivity();
            verifyKafkaConnectivity();
            log.info("Application startup completed successfully - all resources initialized");
        } catch (Exception e) {
            log.error("Application startup failed - critical resource unavailable", e);
            throw new RuntimeException("Application startup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handles ContextClosedEvent to perform shutdown tasks.
     * Flushes pending Kafka messages with timeout.
     * Enforces total shutdown timeout (30s).
     * Database connections are closed by Spring Boot automatically.
     */
    @EventListener
    public void onApplicationShutdown(ContextClosedEvent event) {
        log.info("Application shutdown initiated - starting graceful cleanup");

        long shutdownStartTime = System.currentTimeMillis();
        var lifecycle = applicationProperties.lifecycle();
        long shutdownTimeoutMillis = lifecycle.shutdownTimeoutMillis();
        long kafkaFlushTimeoutMillis = lifecycle.kafkaFlushTimeoutMillis();

        if (!lifecycle.lifecycleEnabled()) {
            log.info("Shutdown cleanup is disabled");
            return;
        }

        // PHASE 1: Kafka Flush (max 10 seconds)
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("shutdown-kafka-flush");
            return t;
        });

        Future<?> shutdownFuture = executor.submit(this::flushKafkaMessages);

        try {
            // Wait for Kafka flush with timeout
            shutdownFuture.get(kafkaFlushTimeoutMillis, TimeUnit.MILLISECONDS);
            log.info("Kafka flush completed successfully within {} ms", kafkaFlushTimeoutMillis);
        } catch (TimeoutException e) {
            log.warn(
                    "Kafka flush timeout after {} ms - killing Kafka flush task and moving to database cleanup",
                    kafkaFlushTimeoutMillis);
            shutdownFuture.cancel(true);
        } catch (Exception exception) {
            log.error("Error during Kafka flush - continuing shutdown", exception);
            shutdownFuture.cancel(true);
        } finally {
            // Shutdown executor immediately (don't wait for graceful termination)
            executor.shutdownNow();
        }

        // PHASE 2: Database Cleanup via Spring (remaining time until 30 seconds)
        long elapsedTime = System.currentTimeMillis() - shutdownStartTime;
        long remainingTimeMillis = shutdownTimeoutMillis - elapsedTime;

        if (remainingTimeMillis > 0) {
            log.info(
                    "Kafka flush done. Database cleanup has {} ms remaining (from {} ms total timeout)",
                    remainingTimeMillis,
                    shutdownTimeoutMillis);
        } else {
            log.warn(
                    "Kafka phase already exceeded {} ms - database cleanup will be force-terminated",
                    shutdownTimeoutMillis);
        }

        // Database connections are automatically closed by Spring Boot's DisposableBean
        // mechanism
        log.info("Database connections will be closed by Spring Boot shutdown hooks");

        // Schedule a forced shutdown to ensure 30-second total limit is enforced
        ScheduledExecutorService shutdownForcer = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("shutdown-forcer");
            return t;
        });

        long elapsedTotal = System.currentTimeMillis() - shutdownStartTime;
        long remainingAbsolute = shutdownTimeoutMillis - elapsedTotal;

        // Only schedule forced exit if enabled
        if (lifecycle.forceExitEnabled()) {
            if (remainingAbsolute > 0) {
                // Schedule forced termination if we exceed total timeout
                shutdownForcer.schedule(
                        () -> {
                            long finalElapsed = System.currentTimeMillis() - shutdownStartTime;
                            log.error(
                                    "CRITICAL: Forced shutdown enforced - total shutdown exceeded {} ms limit (actual: {} ms). JVM terminating.",
                                    shutdownTimeoutMillis,
                                    finalElapsed);
                            try {
                                Thread.sleep(500); // Allow logs to flush
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                            System.exit(1); // Force JVM termination
                        },
                        remainingAbsolute,
                        TimeUnit.MILLISECONDS);
            } else {
                log.error(
                        "CRITICAL: Total shutdown timeout already exceeded ({} ms limit) - forcing immediate termination",
                        shutdownTimeoutMillis);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.exit(1);
            }
        } else {
            log.info(
                    "Forced System.exit disabled - shutdown will rely on Spring lifecycle (remaining time: {} ms)",
                    remainingAbsolute);
            shutdownForcer.shutdown();
        }
    }

    /**
     * Verifies database connectivity by attempting to get a connection.
     * Throws RuntimeException if database is unavailable.
     */
    private void verifyDatabaseConnectivity() {
        log.info("Verifying database connectivity");

        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) { // 5 second timeout
                throw new SQLException("Database connection validation failed");
            }
            log.info("Database connectivity verified successfully");
        } catch (SQLException e) {
            log.error("Database connectivity verification failed", e);
            throw new RuntimeException("Database unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies Kafka connectivity and checks that all required topics are
     * accessible.
     * Does not create topics automatically.
     * Throws RuntimeException if Kafka is unavailable or topics are missing.
     */
    private void verifyKafkaConnectivity() {
        log.info("Verifying Kafka connectivity and topics");

        try {
            // Verify all required topics exist - this will also verify Kafka connectivity
            verifyRequiredTopics();

            log.info("Kafka connectivity and topics verified successfully");
        } catch (Exception e) {
            log.error("Kafka connectivity verification failed", e);
            throw new RuntimeException("Kafka unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies that all required Kafka topics are accessible.
     * Checks new_features, updated_features, and deleted_features topics.
     */
    private void verifyRequiredTopics() throws ExecutionException, InterruptedException, TimeoutException {
        var events = applicationProperties.events();
        var requiredTopics = Set.of(events.newFeatures(), events.updatedFeatures(), events.deletedFeatures());

        log.info("Checking required topics: {}", requiredTopics);

        DescribeTopicsResult describeResult = kafkaAdminClient.describeTopics(requiredTopics);

        // This will throw ExecutionException if any topic doesn't exist
        Map<String, TopicDescription> topicDescriptions =
                describeResult.allTopicNames().get(10, TimeUnit.SECONDS);

        for (String topic : requiredTopics) {
            if (!topicDescriptions.containsKey(topic)) {
                throw new RuntimeException("Required Kafka topic not found: " + topic);
            }
        }

        log.info("All required topics are accessible: {}", requiredTopics);
    }

    /**
     * Flushes pending Kafka messages with a timeout.
     * Logs error and continues if flush fails within timeout.
     */
    private void flushKafkaMessages() {
        log.info(
                "Flushing pending Kafka messages with timeout: {}",
                applicationProperties.lifecycle().kafkaFlushTimeoutMillis());

        try {
            kafkaTemplate.flush();
            log.info("Kafka messages flushed successfully");
        } catch (Exception e) {
            log.error("Failed to flush Kafka messages within timeout", e);
            // Don't re-throw - continue shutdown process
        }
    }
}
