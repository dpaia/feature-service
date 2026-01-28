package com.sivalabs.ft.features.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sivalabs.ft.features.ApplicationProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * Unit tests for ApplicationLifecycleListener using mocks.
 * Tests mock-based exception handling and edge cases that cannot be covered by
 * integration tests.
 * Focuses on error scenarios and internal logic validation.
 *
 * Note: Successful scenarios are covered by integration tests
 * (ApplicationLifecycleListenerIT,
 * ApplicationStartupFailure*IT, ApplicationLifecycleProgrammaticShutdownIT).
 */
@DisplayName("ApplicationLifecycleListener Unit Tests - Mock-Based Edge Cases")
@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemExitExtension.class)
class ApplicationLifecycleListenerUnitTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListenerUnitTest.class);

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
    private ApplicationLifecycleListener lifecycleListener;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties(
                new ApplicationProperties.EventsProperties("new_features", "updated_features", "deleted_features"),
                new ApplicationProperties.LifecycleProperties(true, 30000L, 10000L, false));

        lifecycleListener =
                new ApplicationLifecycleListener(dataSource, kafkaTemplate, kafkaAdminClient, applicationProperties);
    }

    @Nested
    @DisplayName("Database Exception Handling Tests")
    class DatabaseExceptionTests {

        @Test
        @DisplayName("Should fail startup when database connection throws SQLException")
        void shouldFailOnDatabaseConnectivityIssue() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

            // When & Then
            RuntimeException exception = assertThrows(
                    RuntimeException.class, () -> lifecycleListener.onApplicationStartup(contextRefreshedEvent));

            assertEquals(
                    "Application startup failed: Database unavailable: Connection refused", exception.getMessage());
            verify(dataSource, times(1)).getConnection();

            log.info("Correctly failed on database unavailability");
        }

        @Test
        @DisplayName("Should handle database timeout errors")
        void shouldHandleDatabaseTimeoutError() throws SQLException {
            // Given - Database throws timeout exception
            when(dataSource.getConnection()).thenThrow(new SQLException("Connection timeout"));

            // When & Then
            RuntimeException exception = assertThrows(
                    RuntimeException.class, () -> lifecycleListener.onApplicationStartup(contextRefreshedEvent));

            assertThat(exception.getMessage()).contains("Database unavailable", "Connection timeout");

            log.info("Database timeout error handled correctly");
        }
    }

    @Nested
    @DisplayName("Kafka Exception Handling Tests")
    class KafkaExceptionTests {

        @Test
        @DisplayName("Should fail startup when Kafka connectivity fails with TimeoutException")
        void shouldFailOnKafkaTimeoutException()
                throws SQLException, ExecutionException, InterruptedException, TimeoutException {
            // Given - valid database
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isValid(anyInt())).thenReturn(true);

            // Kafka connectivity test fails - mock AdminClient to throw TimeoutException
            when(kafkaAdminClient.describeTopics(anyCollection())).thenReturn(describeTopicsResult);
            when(describeTopicsResult.allTopicNames()).thenReturn(kafkaFuture);
            when(kafkaFuture.get(eq(10L), eq(TimeUnit.SECONDS))).thenThrow(new TimeoutException("Connection timeout"));

            // When & Then
            RuntimeException exception = assertThrows(
                    RuntimeException.class, () -> lifecycleListener.onApplicationStartup(contextRefreshedEvent));

            assertEquals("Application startup failed: Kafka unavailable: Connection timeout", exception.getMessage());
            verify(kafkaAdminClient, times(1)).describeTopics(anyCollection());

            log.info("Correctly failed on Kafka timeout");
        }

        @Test
        @DisplayName("Should handle InterruptedException during Kafka operations")
        void shouldHandleKafkaInterruptedException()
                throws SQLException, ExecutionException, InterruptedException, TimeoutException {
            // Given - Database valid but Kafka throws InterruptedException
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isValid(anyInt())).thenReturn(true);

            when(kafkaAdminClient.describeTopics(anyCollection())).thenReturn(describeTopicsResult);
            when(describeTopicsResult.allTopicNames()).thenReturn(kafkaFuture);
            when(kafkaFuture.get(eq(10L), eq(TimeUnit.SECONDS)))
                    .thenThrow(new InterruptedException("Thread interrupted"));

            // When & Then
            RuntimeException exception = assertThrows(
                    RuntimeException.class, () -> lifecycleListener.onApplicationStartup(contextRefreshedEvent));

            assertThat(exception.getMessage()).contains("Kafka unavailable");

            log.info("Kafka InterruptedException handled correctly");
        }
    }

    @Nested
    @DisplayName("Shutdown Configuration Tests")
    class ShutdownConfigurationTests {

        @Test
        @DisplayName("Should complete shutdown successfully when lifecycle is disabled")
        void shouldCompleteShutdownWhenLifecycleDisabled() {
            // Given - lifecycle disabled
            ApplicationProperties disabledProperties = new ApplicationProperties(
                    new ApplicationProperties.EventsProperties("new_features", "updated_features", "deleted_features"),
                    new ApplicationProperties.LifecycleProperties(false, 30000L, 10000L, false));
            ApplicationLifecycleListener disabledListener =
                    new ApplicationLifecycleListener(dataSource, kafkaTemplate, kafkaAdminClient, disabledProperties);

            // When - Execute shutdown with lifecycle disabled
            disabledListener.onApplicationShutdown(contextClosedEvent);

            // Then - No Kafka operations should occur
            verify(kafkaTemplate, times(0)).flush();

            log.info("Shutdown correctly skipped when lifecycle is disabled");
        }

        @Test
        @DisplayName("Should invoke Kafka flush during shutdown")
        void shouldInvokeKafkaFlushDuringShutdown() {
            // Given - successful Kafka flush
            // No exception setup means kafkaTemplate.flush() succeeds

            // When - Execute shutdown
            lifecycleListener.onApplicationShutdown(contextClosedEvent);

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
            lifecycleListener.onApplicationShutdown(contextClosedEvent);

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
            lifecycleListener.onApplicationShutdown(contextClosedEvent);

            // Then - Shutdown should complete gracefully despite state exception
            verify(kafkaTemplate, times(1)).flush();

            log.info("Shutdown handled IllegalStateException during Kafka flush");
        }
    }
}
