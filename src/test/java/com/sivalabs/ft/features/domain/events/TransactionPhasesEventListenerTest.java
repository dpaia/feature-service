package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.ApplicationProperties;
import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.Commands;
import com.sivalabs.ft.features.domain.FeatureService;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Integration tests for transactional event listener behavior.
 * <p>
 * This test suite verifies that @TransactionalEventListener with AFTER_COMMIT phase
 * ensures events are only published after successful database transaction commits.
 * <p>
 * CRITICAL: These tests will FAIL if the implementation uses direct EventPublisher calls
 * instead of @TransactionalEventListener with AFTER_COMMIT phase.
 * <p>
 * NOTE: These tests only use public APIs (FeatureService) to verify behavior.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TransactionPhasesEventListenerTest.TestConfig.class})
@Sql(scripts = {"/test-data.sql"})
class TransactionPhasesEventListenerTest {

    private static final Logger logger = LoggerFactory.getLogger(TransactionPhasesEventListenerTest.class);

    @Autowired
    private FeatureService featureService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ApplicationProperties applicationProperties;

    @SpyBean
    private EventPublisher eventPublisher;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public KafkaTemplate<String, Object> kafkaTemplate() {
            return Mockito.mock(KafkaTemplate.class);
        }
    }


    /**
     * CRITICAL TEST: This test verifies that events are published AFTER transaction commits.
     * <p>
     * This test will FAIL if @TransactionalEventListener(AFTER_COMMIT) is not used properly.
     * <p>
     * Scenario:
     * - Configure EventPublisher to throw exception when publishing
     * - Create a feature (which saves to database and publishes event)
     * <p>
     * Expected behavior with @TransactionalEventListener(AFTER_COMMIT):
     * 1. Transaction commits successfully (feature saved to database)
     * 2. THEN listener is invoked AFTER commit
     * 3. Listener calls EventPublisher which throws exception
     * 4. Exception does NOT rollback transaction (already committed)
     * 5. Feature EXISTS in database
     * <p>
     * Behavior WITHOUT @TransactionalEventListener (direct call in transaction):
     * 1. Feature saved to database (within transaction)
     * 2. EventPublisher called directly (still within transaction)
     * 3. EventPublisher throws exception
     * 4. Exception rolls back ENTIRE transaction
     * 5. Feature does NOT exist in database
     * <p>
     * This test will PASS only with correct @TransactionalEventListener(AFTER_COMMIT) implementation.
     */
    @Test
    void shouldCommitTransaction_beforePublishingEvent_provingAfterCommitPhase() {
        Mockito.reset(kafkaTemplate, eventPublisher);

        // Given: Configure EventPublisher to throw exception when trying to publish
        // This simulates a failure in the event publishing infrastructure (e.g., Kafka down)
        Mockito.doThrow(new RuntimeException("Simulated Kafka failure"))
                .when(eventPublisher).publishFeatureCreatedEvent(any());

        // When: Create a feature (this will save to DB and attempt to publish event)
        Commands.CreateFeatureCommand command = new Commands.CreateFeatureCommand(
                "intellij",
                "IDEA-2024.2.3",
                "Feature to test AFTER_COMMIT phase",
                "This test proves events are published AFTER commit",
                "assignee",
                "creator"
        );

        String featureCode = null;
        try {
            featureCode = featureService.createFeature(command);
            logger.info("Feature created with code: {}", featureCode);
        } catch (Exception e) {
            // Exception from EventPublisher is expected after commit
            logger.info("Expected exception from EventPublisher: {}", e.getMessage());
        }

        // Then: Feature MUST exist in database
        // This proves the transaction committed BEFORE the EventPublisher was called
        // If EventPublisher was called within the transaction, the exception would rollback
        // the entire transaction and the feature would NOT exist

        // We need to determine the feature code - try common patterns
        // In this implementation, features get sequential IDs with product prefix
        // We can verify using isFeatureExists which is a public API
        if (featureCode != null) {
            assertThat(featureService.isFeatureExists(featureCode)).isTrue();
            logger.info("SUCCESS: Feature {} exists in database, proving transaction committed " +
                    "BEFORE EventPublisher was invoked (AFTER_COMMIT phase working correctly)", featureCode);
        } else {
            // If we caught exception, the createFeature should still have returned the code
            // before the listener threw exception (since listener runs after method returns)
            // Let's verify by checking if any IDEA feature was created recently
            // Actually, in AFTER_COMMIT mode, the exception happens AFTER the method returns
            // So we should have gotten the featureCode. Let's fail the test if we didn't.
            throw new AssertionError("Feature code should have been returned even if listener failed");
        }

        // Verify that EventPublisher was called (proving the listener was invoked)
        Mockito.verify(eventPublisher).publishFeatureCreatedEvent(any());
    }

    @Test
    void shouldPublishFeatureCreatedEvent_whenTransactionCommits() {
        Mockito.reset(kafkaTemplate);

        // Given a valid create feature command
        Commands.CreateFeatureCommand command = new Commands.CreateFeatureCommand(
                "intellij", // product code from test data
                "IDEA-2024.2.3", // release code from test data
                "Test Feature for Commit",
                "Test Description for successful commit",
                "assignee",
                "creator"
        );

        // When creating a feature (which should succeed)
        String featureCode = featureService.createFeature(command);
        logger.info("Created feature with code: {}", featureCode);

        // Then verify that KafkaTemplate was invoked with the correct event and topic
        ArgumentCaptor<FeatureCreatedEvent> eventCaptor = ArgumentCaptor.forClass(FeatureCreatedEvent.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

        // Verify the topic
        assertThat(topicCaptor.getValue()).isEqualTo(applicationProperties.events().newFeatures());

        // Verify the event contents
        FeatureCreatedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent).isNotNull();
        assertThat(capturedEvent.code()).isEqualTo(featureCode);
        assertThat(capturedEvent.title()).isEqualTo("Test Feature for Commit");
        assertThat(capturedEvent.description()).isEqualTo("Test Description for successful commit");
        assertThat(capturedEvent.assignedTo()).isEqualTo("assignee");
        assertThat(capturedEvent.createdBy()).isEqualTo("creator");
        assertThat(capturedEvent.status()).isEqualTo(FeatureStatus.NEW);
        assertThat(capturedEvent.createdAt()).isNotNull();
    }

    @Test
    void shouldNotPublishFeatureCreatedEvent_whenTransactionRollsBack() {
        Mockito.reset(kafkaTemplate);

        // Given a create command with invalid product code
        Commands.CreateFeatureCommand command = new Commands.CreateFeatureCommand(
                "nonexistent-product", // Invalid product code
                "IDEA-2024.2.3",
                "Test Feature for Rollback",
                "Test Description for rollback",
                "assignee",
                "creator"
        );

        // When attempting to create a feature (which should fail)
        // Then verify that an exception is thrown
        assertThatThrownBy(() -> featureService.createFeature(command))
                .isInstanceOf(Exception.class);

        // And verify that KafkaTemplate was not invoked (since the transaction was rolled back)
        Mockito.verifyNoInteractions(kafkaTemplate);
    }

    /**
     * CRITICAL TEST: Verifies UPDATE events are published AFTER transaction commits.
     * <p>
     * Same principle as create test: if EventPublisher throws exception,
     * the update should still be persisted (proving AFTER_COMMIT behavior).
     */
    @Test
    void shouldCommitTransaction_beforePublishingUpdateEvent_provingAfterCommitPhase() {
        Mockito.reset(kafkaTemplate, eventPublisher);

        // Given: Existing feature and EventPublisher configured to throw exception on update
        String existingFeatureCode = "IDEA-1";
        Mockito.doThrow(new RuntimeException("Simulated Kafka failure on update"))
                .when(eventPublisher).publishFeatureUpdatedEvent(any());

        // When: Update the feature
        Commands.UpdateFeatureCommand command = new Commands.UpdateFeatureCommand(
                existingFeatureCode,
                "Updated Title - Testing AFTER_COMMIT",
                "Updated description",
                FeatureStatus.IN_PROGRESS,
                "IDEA-2024.2.3",
                "new-assignee",
                "updater"
        );

        try {
            featureService.updateFeature(command);
        } catch (Exception e) {
            logger.info("Expected exception from EventPublisher: {}", e.getMessage());
        }

        // Then: Update MUST be persisted in database (proving AFTER_COMMIT)
        // Use public API to verify the update
        var updatedFeature = featureService.findFeatureByCode(null, existingFeatureCode);
        assertThat(updatedFeature).isPresent();
        assertThat(updatedFeature.get().title()).isEqualTo("Updated Title - Testing AFTER_COMMIT");
        assertThat(updatedFeature.get().status()).isEqualTo(FeatureStatus.IN_PROGRESS);

        logger.info("SUCCESS: Feature update persisted, proving AFTER_COMMIT phase for updates");
        Mockito.verify(eventPublisher).publishFeatureUpdatedEvent(any());
    }

    @Test
    void shouldPublishFeatureUpdatedEvent_whenTransactionCommits() {
        Mockito.reset(kafkaTemplate);

        // Given an existing feature code from test data
        String existingFeatureCode = "IDEA-1";

        // And an update command with valid data
        Commands.UpdateFeatureCommand command = new Commands.UpdateFeatureCommand(
                existingFeatureCode,
                "Updated Feature Title",
                "Updated feature description",
                FeatureStatus.IN_PROGRESS,
                "IDEA-2024.2.3", // Valid release code
                "new-assignee",
                "updater"
        );

        // When updating the feature (which should succeed)
        featureService.updateFeature(command);
        logger.info("Updated feature with code: {}", existingFeatureCode);

        // Then verify that KafkaTemplate was invoked with the correct event and topic
        ArgumentCaptor<FeatureUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(FeatureUpdatedEvent.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

        // Verify the topic
        assertThat(topicCaptor.getValue()).isEqualTo(applicationProperties.events().updatedFeatures());

        // Verify the event contents
        FeatureUpdatedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent).isNotNull();
        assertThat(capturedEvent.code()).isEqualTo(existingFeatureCode);
        assertThat(capturedEvent.title()).isEqualTo("Updated Feature Title");
        assertThat(capturedEvent.description()).isEqualTo("Updated feature description");
        assertThat(capturedEvent.assignedTo()).isEqualTo("new-assignee");
        assertThat(capturedEvent.updatedBy()).isEqualTo("updater");
        assertThat(capturedEvent.status()).isEqualTo(FeatureStatus.IN_PROGRESS);
        assertThat(capturedEvent.updatedAt()).isNotNull();
    }

    @Test
    void shouldNotPublishFeatureUpdatedEvent_whenTransactionRollsBack() {
        Mockito.reset(kafkaTemplate);

        // Given an update command with non-existent feature code
        Commands.UpdateFeatureCommand command = new Commands.UpdateFeatureCommand(
                "NONEXISTENT-999", // Invalid feature code
                "Updated Title",
                "Updated Description",
                FeatureStatus.IN_PROGRESS,
                "IDEA-2024.2.3",
                "assignee",
                "updater"
        );

        // When attempting to update a non-existent feature (which should fail)
        // Then verify that an exception is thrown
        assertThatThrownBy(() -> featureService.updateFeature(command))
                .isInstanceOf(Exception.class);

        // And verify that KafkaTemplate was not invoked (since the transaction was rolled back)
        Mockito.verifyNoInteractions(kafkaTemplate);
    }

    /**
     * CRITICAL TEST: Verifies DELETE events are published AFTER transaction commits.
     * <p>
     * Same principle: if EventPublisher throws exception,
     * the deletion should still be persisted (proving AFTER_COMMIT behavior).
     */
    @Test
    void shouldCommitTransaction_beforePublishingDeleteEvent_provingAfterCommitPhase() {
        Mockito.reset(kafkaTemplate, eventPublisher);

        // Given: Existing feature and EventPublisher configured to throw exception on delete
        String existingFeatureCode = "GO-3";
        Mockito.doThrow(new RuntimeException("Simulated Kafka failure on delete"))
                .when(eventPublisher).publishFeatureDeletedEvent(any(), any(), any());

        // Verify feature exists before deletion using public API
        assertThat(featureService.isFeatureExists(existingFeatureCode)).isTrue();

        // When: Delete the feature
        Commands.DeleteFeatureCommand command = new Commands.DeleteFeatureCommand(
                existingFeatureCode,
                "deleter"
        );

        try {
            featureService.deleteFeature(command);
        } catch (Exception e) {
            logger.info("Expected exception from EventPublisher: {}", e.getMessage());
        }

        // Then: Deletion MUST be persisted in database (proving AFTER_COMMIT)
        // Use public API to verify deletion
        assertThat(featureService.isFeatureExists(existingFeatureCode)).isFalse();

        logger.info("SUCCESS: Feature deletion persisted, proving AFTER_COMMIT phase for deletes");
        Mockito.verify(eventPublisher).publishFeatureDeletedEvent(any(), any(), any());
    }

    @Test
    void shouldPublishFeatureDeletedEvent_whenTransactionCommits() {
        Mockito.reset(kafkaTemplate);

        // Given an existing feature code from test data
        String existingFeatureCode = "IDEA-2";

        // And a delete command
        Commands.DeleteFeatureCommand command = new Commands.DeleteFeatureCommand(
                existingFeatureCode,
                "deleter"
        );

        // When deleting the feature (which should succeed)
        featureService.deleteFeature(command);
        logger.info("Deleted feature with code: {}", existingFeatureCode);

        // Then verify that KafkaTemplate was invoked with the correct event and topic
        ArgumentCaptor<FeatureDeletedEvent> eventCaptor = ArgumentCaptor.forClass(FeatureDeletedEvent.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

        // Verify the topic
        assertThat(topicCaptor.getValue()).isEqualTo(applicationProperties.events().deletedFeatures());

        // Verify the event contents
        FeatureDeletedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent).isNotNull();
        assertThat(capturedEvent.code()).isEqualTo(existingFeatureCode);
        assertThat(capturedEvent.deletedBy()).isEqualTo("deleter");
        assertThat(capturedEvent.deletedAt()).isNotNull();
        // Original feature data should still be present in the event
        assertThat(capturedEvent.title()).isNotNull();
        assertThat(capturedEvent.description()).isNotNull();
    }

    @Test
    void shouldNotPublishFeatureDeletedEvent_whenTransactionRollsBack() {
        Mockito.reset(kafkaTemplate);

        // Given a delete command with non-existent feature code
        Commands.DeleteFeatureCommand command = new Commands.DeleteFeatureCommand(
                "NONEXISTENT-999", // Invalid feature code
                "deleter"
        );

        // When attempting to delete a non-existent feature (which should fail)
        // Then verify that an exception is thrown
        assertThatThrownBy(() -> featureService.deleteFeature(command))
                .isInstanceOf(Exception.class);

        // And verify that KafkaTemplate was not invoked (since the transaction was rolled back)
        Mockito.verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void shouldPublishEventsOnlyForSuccessfulTransactions_inMixedScenario() {
        Mockito.reset(kafkaTemplate);

        // Given: Create a feature successfully
        Commands.CreateFeatureCommand createCommand = new Commands.CreateFeatureCommand(
                "intellij",
                "IDEA-2024.2.3",
                "Feature for Mixed Test",
                "Description",
                "assignee",
                "creator"
        );
        String createdFeatureCode = featureService.createFeature(createCommand);

        // When: Update the same feature successfully
        Commands.UpdateFeatureCommand updateCommand = new Commands.UpdateFeatureCommand(
                createdFeatureCode,
                "Updated Title",
                "Updated Description",
                FeatureStatus.IN_PROGRESS,
                "IDEA-2024.2.3",
                "assignee",
                "updater"
        );
        featureService.updateFeature(updateCommand);

        // And: Attempt to create another feature with invalid data (should fail)
        Commands.CreateFeatureCommand failingCommand = new Commands.CreateFeatureCommand(
                "nonexistent-product",
                "IDEA-2024.2.3",
                "Failing Feature",
                "Description",
                "assignee",
                "creator"
        );

        try {
            featureService.createFeature(failingCommand);
        } catch (Exception e) {
            // Expected to fail
            logger.info("Expected failure occurred: {}", e.getMessage());
        }

        // Then: Verify that only 2 events were published (create + update)
        // The third operation (failing create) should not publish an event
        Mockito.verify(kafkaTemplate, Mockito.times(2)).send(Mockito.anyString(), Mockito.any());

        // Verify the first was a create event
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(kafkaTemplate, Mockito.times(2)).send(Mockito.anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getAllValues()).hasSize(2);
        assertThat(eventCaptor.getAllValues().get(0)).isInstanceOf(FeatureCreatedEvent.class);
        assertThat(eventCaptor.getAllValues().get(1)).isInstanceOf(FeatureUpdatedEvent.class);
    }
}