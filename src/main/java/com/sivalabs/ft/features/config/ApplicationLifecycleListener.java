package com.sivalabs.ft.features.config;

import com.sivalabs.ft.features.ApplicationProperties;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListener.class);

    private static final long KAFKA_FLUSH_TIMEOUT_SECONDS = 10;
    private static final Duration KAFKA_ADMIN_TIMEOUT = Duration.ofSeconds(10);

    private final DataSource dataSource;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationProperties properties;

    private final AtomicBoolean startupExecuted = new AtomicBoolean(false);
    private final AtomicBoolean shutdownExecuted = new AtomicBoolean(false);

    public ApplicationLifecycleListener(
            DataSource dataSource, KafkaTemplate<String, Object> kafkaTemplate, ApplicationProperties properties) {
        this.dataSource = dataSource;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @EventListener
    public void onApplicationStartup(ContextRefreshedEvent event) {
        if (!startupExecuted.compareAndSet(false, true)) {
            log.debug("Startup tasks already executed, skipping duplicate ContextRefreshedEvent.");
            return;
        }
        log.info("Application startup detected. Running initialization tasks...");
        verifyDatabaseConnectivity();
        verifyKafkaConnectivity();
        warmUpKafkaProducer();
        log.info("Application startup initialization completed successfully.");
    }

    @EventListener
    public void onApplicationShutdown(ContextClosedEvent event) {
        if (!shutdownExecuted.compareAndSet(false, true)) {
            log.debug("Shutdown tasks already executed, skipping duplicate ContextClosedEvent.");
            return;
        }
        long totalTimeoutSeconds = properties.shutdown().timeoutSeconds();
        log.info("Application shutdown detected (total timeout: {} s). Running cleanup tasks...", totalTimeoutSeconds);

        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<?> shutdownTask = shutdownExecutor.submit(this::flushKafkaMessages);
            shutdownTask.get(totalTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Shutdown tasks exceeded total timeout of {} seconds. Continuing.", totalTimeoutSeconds);
        } catch (ExecutionException e) {
            log.error("Error during shutdown tasks: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Shutdown was interrupted: {}", e.getMessage());
        } finally {
            shutdownExecutor.shutdownNow();
        }

        log.info("Application shutdown cleanup completed. Database connections will be closed by Spring Boot.");
    }

    private void verifyDatabaseConnectivity() {
        log.info("Verifying database connectivity...");
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                log.info("Database connectivity verified successfully.");
            } else {
                throw new RuntimeException("Database connection is not valid");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Database connectivity verification failed: {}", e.getMessage());
            throw new RuntimeException("Failed to verify database connectivity on startup", e);
        }
    }

    private void verifyKafkaConnectivity() {
        log.info("Verifying Kafka connectivity...");
        List<String> requiredTopics = List.of(
                properties.events().newFeatures(),
                properties.events().updatedFeatures(),
                properties.events().deletedFeatures());

        try (AdminClient adminClient =
                AdminClient.create(kafkaTemplate.getProducerFactory().getConfigurationProperties())) {
            Set<String> existingTopics =
                    adminClient.listTopics().names().get(KAFKA_ADMIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            log.info("Available Kafka topics: {}", existingTopics);

            List<String> missingTopics = requiredTopics.stream()
                    .filter(t -> !existingTopics.contains(t))
                    .toList();
            if (!missingTopics.isEmpty()) {
                throw new RuntimeException("Required Kafka topics are missing: " + missingTopics);
            }
            log.info("All required Kafka topics are accessible: {}", requiredTopics);
        } catch (RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka connectivity verification was interrupted", e);
        } catch (ExecutionException e) {
            log.error("Kafka connectivity verification failed: {}", e.getMessage());
            throw new RuntimeException("Failed to verify Kafka connectivity on startup", e);
        } catch (TimeoutException e) {
            log.error("Kafka connectivity verification timed out: {}", e.getMessage());
            throw new RuntimeException("Kafka connectivity verification timed out on startup", e);
        }
    }

    private void warmUpKafkaProducer() {
        log.info("Warming up Kafka producer connection...");
        List<String> topics = List.of(
                properties.events().newFeatures(),
                properties.events().updatedFeatures(),
                properties.events().deletedFeatures());
        try {
            for (String topic : topics) {
                kafkaTemplate.partitionsFor(topic);
                log.info("Producer metadata fetched for topic: {}", topic);
            }
            log.info("Kafka producer warmed up successfully.");
        } catch (Exception e) {
            log.error("Failed to warm up Kafka producer: {}", e.getMessage());
            throw new RuntimeException("Failed to warm up Kafka producer on startup", e);
        }
    }

    private void flushKafkaMessages() {
        long flushTimeout =
                Math.min(KAFKA_FLUSH_TIMEOUT_SECONDS, properties.shutdown().timeoutSeconds());
        log.info("Flushing pending Kafka messages (timeout: {} seconds)...", flushTimeout);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> flushTask = executor.submit(() -> kafkaTemplate.flush());
            flushTask.get(flushTimeout, TimeUnit.SECONDS);
            log.info("Kafka messages flushed successfully.");
        } catch (TimeoutException e) {
            log.error("Kafka flush timed out after {} seconds. Continuing shutdown.", flushTimeout);
        } catch (ExecutionException e) {
            log.error("Failed to flush Kafka messages: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka flush was interrupted: {}", e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }
}
