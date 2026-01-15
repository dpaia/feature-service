package com.sivalabs.ft.features.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sivalabs.ft.features.ApplicationProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for ApplicationLifecycleManager using mocks.
 * Tests mock-based exception handling and edge cases that cannot be covered by
 * integration tests.
 * Focuses on error scenarios and internal logic validation.
 *
 * Note: Successful scenarios are covered by integration tests
 * (ApplicationLifecycleManagerIT,
 * ApplicationStartupFailure*IT, ApplicationLifecycleProgrammaticShutdownIT).
 */
@DisplayName("ApplicationLifecycleManager Unit Tests - Mock-Based Edge Cases")
@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemExitExtension.class)
class ApplicationLifecycleManagerUnitTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleManagerUnitTest.class);

    @Mock
    private DataSource dataSource;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private AdminClient kafkaAdminClient;

    @Mock
    private DescribeTopicsResult describeTopicsResult;

    @Mock
    private KafkaFuture<Map<String, TopicDescription>> kafkaFuture;

    @Mock
    private Connection connection;

    @Mock
    private ContextRefreshedEvent contextRefreshedEvent;

    @Mock
    private ContextClosedEvent contextClosedEvent;

    private ApplicationProperties applicationProperties;
    private ApplicationLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties(
                new ApplicationProperties.EventsProperties("new_features", "updated_features", "deleted_features"),
                new ApplicationProperties.LifecycleProperties(true, 30000L, 10000L, false));

        lifecycleManager = new ApplicationLifecycleManager(dataSource, kafkaTemplate, kafkaAdminClient,
                applicationProperties);
    }

    @Nested
    @DisplayName("Database Exception Handling Tests")
    class DatabaseExceptionTests {

        @Test
        @DisplayName("Should handle database timeout errors")
        void shouldHandleDatabaseTimeoutError() throws SQLException {
            // Given - Database throws timeout exception
            when(dataSource.getConnection()).thenThrow(new SQLException("Connection timeout"));

            // When & Then
            RuntimeException exception = assertThrows(
                    RuntimeException.class, () -> lifecycleManager.onApplicationStartup(contextRefreshedEvent));

            assertThat(exception.getMessage()).containsAnyOf("Database unavailable", "Connection timeout");

            log.info("Database timeout error handled correctly");
        }
    }

    @Nested
    @DisplayName("Shutdown Configuration Tests")
    class ShutdownConfigurationTests {

        @Test
        @DisplayName("Should invoke Kafka flush during shutdown")
        void shouldInvokeKafkaFlushDuringShutdown() {
            // Given - successful Kafka flush
            // No exception setup means kafkaTemplate.flush() succeeds

            // When - Execute shutdown
            lifecycleManager.onApplicationShutdown(contextClosedEvent);

            // Then - Kafka flush should be called exactly once
            verify(kafkaTemplate, times(1)).flush();

            log.info("Shutdown correctly invoked Kafka flush");
        }

        @Test
        @DisplayName("Should handle NullPointerException during Kafka flush")
        void shouldHandleNullPointerExceptionDuringKafkaFlush() {
            // Given - Kafka flush throws NullPointerException
            doThrow(new NullPointerException("Kafka template is null"))
                    .when(kafkaTemplate)
                    .flush();

            // When - Execute shutdown
            lifecycleManager.onApplicationShutdown(contextClosedEvent);

            // Then - Shutdown should complete gracefully despite NPE
            verify(kafkaTemplate, times(1)).flush();

            log.info("Shutdown handled NullPointerException during Kafka flush");
        }

        @Test
        @DisplayName("Should handle IllegalStateException during Kafka flush")
        void shouldHandleIllegalStateExceptionDuringKafkaFlush() {
            // Given - Kafka flush throws IllegalStateException
            doThrow(new IllegalStateException("Kafka producer is closed"))
                    .when(kafkaTemplate)
                    .flush();

            // When - Execute shutdown
            lifecycleManager.onApplicationShutdown(contextClosedEvent);

            // Then - Shutdown should complete gracefully despite state exception
            verify(kafkaTemplate, times(1)).flush();

            log.info("Shutdown handled IllegalStateException during Kafka flush");
        }
    }
}
