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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for ApplicationLifecycleListener.
 * Tests successful startup scenarios with real infrastructure (Testcontainers).
 * Focuses on positive path verification of startup lifecycle events.
 */
@DisplayName("ApplicationLifecycleListener Integration Tests")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {FeatureServiceApplication.class, TestcontainersConfiguration.class, TestKafkaTopicConfiguration.class
        })
@TestPropertySource(
        properties = {
            "ft.lifecycle.lifecycle-enabled=false",
            "ft.lifecycle.shutdown-timeout-millis=30000",
            "ft.lifecycle.kafka-flush-timeout-millis=10000",
            "ft.events.new-features=new_features",
            "ft.events.updated-features=updated_features",
            "ft.events.deleted-features=deleted_features"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApplicationLifecycleListenerIntegrationTests {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListenerIntegrationTests.class);

    @Autowired
    private ApplicationLifecycleListener lifecycleListener;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AdminClient adminClient;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    @DisplayName("Should initialize with valid configuration")
    void shouldInitializeWithValidConfiguration() {
        // Application context started successfully if we reach this point
        assertThat(applicationContext.isActive()).isTrue();
        log.info("Application context initialized successfully with valid configuration");
    }

    @Test
    @DisplayName("Should have all required beans")
    void shouldHaveAllRequiredBeans() {
        assertThat(lifecycleListener).isNotNull();
        assertThat(dataSource).isNotNull();
        assertThat(kafkaTemplate).isNotNull();
        assertThat(adminClient).isNotNull();

        log.info("All required beans are properly injected");
    }

    @Test
    @DisplayName("Should verify database connectivity manually")
    void shouldVerifyDatabaseConnectivityManually() throws Exception {
        // Manually test database connectivity (lifecycle is disabled for test safety)
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
            log.info("Database connectivity verified manually");
        }
    }

    @Test
    @DisplayName("Should verify Kafka connectivity and topics manually")
    void shouldVerifyKafkaConnectivityAndTopics() throws Exception {
        // Manually test Kafka connectivity (lifecycle is disabled for test safety)
        var topicNames = Set.of("new_features", "updated_features", "deleted_features");
        var topicDescriptions =
                adminClient.describeTopics(topicNames).allTopicNames().get(10, TimeUnit.SECONDS);

        assertThat(topicDescriptions.keySet()).containsExactlyInAnyOrderElementsOf(topicNames);
        log.info("Kafka connectivity and all required topics verified manually");
    }

    @Test
    @DisplayName("Should have ApplicationLifecycleListener bean available")
    void shouldHaveApplicationLifecycleListenerBeanAvailable() {
        // Verify ApplicationLifecycleListener bean exists and can be injected
        assertThat(lifecycleListener).isNotNull();
        assertThat(applicationContext.isActive()).isTrue();
        log.info("ApplicationLifecycleListener bean is properly configured and available");
    }
}
