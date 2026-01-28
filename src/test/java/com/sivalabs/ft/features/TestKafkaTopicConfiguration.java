package com.sivalabs.ft.features;

import com.sivalabs.ft.features.config.ApplicationLifecycleListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Test configuration that creates required Kafka topics during Spring Boot
 * context startup.
 * This ensures topics are available when ApplicationLifecycleListener performs
 * startup verification.
 * Also ensures KafkaTemplate and AdminClient beans are available for
 * ApplicationLifecycleListener's @ConditionalOnBean.
 *
 * Uses @Order(Integer.MIN_VALUE) to run before ApplicationLifecycleListener.
 */
@TestConfiguration
public class TestKafkaTopicConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TestKafkaTopicConfiguration.class);

    @Autowired
    private KafkaAdmin kafkaAdmin;

    private volatile boolean topicsCreated = false;

    /**
     * Create a test KafkaTemplate to satisfy
     * ApplicationLifecycleListener's @ConditionalOnBean requirement.
     * This ensures ApplicationLifecycleListener bean is created in test
     * environments.
     * NOT marked as @Lazy to ensure it's available during @ConditionalOnBean
     * evaluation.
     */
    @Bean
    @Primary
    public KafkaTemplate<String, Object> testKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);

        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(props);

        log.info("TEST-SETUP: Created test KafkaTemplate for ApplicationLifecycleListener");
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Create a test AdminClient to satisfy
     * ApplicationLifecycleListener's @ConditionalOnBean requirement.
     * This ensures both KafkaTemplate and AdminClient are available in test
     * context.
     * NOT marked as @Lazy to ensure it's available during @ConditionalOnBean
     * evaluation.
     */
    @Bean
    @Primary
    public AdminClient testAdminClient() {
        Map<String, Object> props = new HashMap<>();
        props.put(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 30000);

        log.info("TEST-SETUP: Created test AdminClient for ApplicationLifecycleListener");
        return AdminClient.create(props);
    }

    /**
     * Create Kafka topics immediately when Spring context is refreshed.
     * Uses highest priority (@Order(Integer.MIN_VALUE)) to run before
     * ApplicationLifecycleListener.
     */
    @EventListener(ContextRefreshedEvent.class)
    @Order(Integer.MIN_VALUE)
    public void createKafkaTopics() {
        if (topicsCreated) {
            log.debug("Kafka topics already created, skipping");
            return;
        }

        log.info("TEST-SETUP: Creating Kafka topics before ApplicationLifecycleListener verification");

        try {
            AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());

            // Create required topics that ApplicationLifecycleListener will verify
            var requiredTopics = Set.of(
                    new NewTopic("new_features", 1, (short) 1),
                    new NewTopic("updated_features", 1, (short) 1),
                    new NewTopic("deleted_features", 1, (short) 1));

            log.info(
                    "TEST-SETUP: Creating topics: {}",
                    requiredTopics.stream().map(NewTopic::name).toList());

            try {
                adminClient.createTopics(requiredTopics).all().get(15, TimeUnit.SECONDS);
                log.info("TEST-SETUP: Successfully created all Kafka topics");
            } catch (Exception e) {
                // Check if topics already exist (which is fine)
                if (e.getCause() instanceof TopicExistsException
                        || e.getMessage().contains("already exists")
                        || e.getMessage().contains("TopicExistsException")) {
                    log.info("TEST-SETUP: Topics already exist (acceptable)");
                } else {
                    log.error("TEST-SETUP: Failed to create topics", e);
                    throw new RuntimeException("Failed to create required Kafka topics for tests", e);
                }
            }

            // Wait for topic metadata to propagate to ensure they're discoverable
            Thread.sleep(2000);

            // Verify topics are accessible
            var topicNames = Set.of("new_features", "updated_features", "deleted_features");
            adminClient.describeTopics(topicNames).allTopicNames().get(10, TimeUnit.SECONDS);

            adminClient.close();
            topicsCreated = true;

            log.info("TEST-SETUP: Kafka topics are ready and verified");

        } catch (Exception e) {
            log.error("TEST-SETUP: Critical error creating Kafka topics", e);
            throw new RuntimeException("Test setup failed: Unable to create required Kafka topics", e);
        }
    }

    /**
     * Create ApplicationLifecycleListener bean explicitly for integration tests.
     * This bypasses the @ConditionalOnBean issue where Spring can't find the
     * required beans
     * during the conditional evaluation phase.
     */
    @Bean
    @Primary
    public ApplicationLifecycleListener testApplicationLifecycleListener(
            DataSource dataSource,
            KafkaTemplate<String, Object> kafkaTemplate,
            AdminClient adminClient,
            ApplicationProperties applicationProperties) {

        log.info("TEST-SETUP: Creating test ApplicationLifecycleListener bean explicitly");
        return new ApplicationLifecycleListener(dataSource, kafkaTemplate, adminClient, applicationProperties);
    }
}
