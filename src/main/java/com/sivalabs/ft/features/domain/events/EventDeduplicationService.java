package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.ProcessedEventRepository;
import com.sivalabs.ft.features.domain.models.EventType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simplified database-only event deduplication service
 * Uses PostgreSQL constraints for thread-safe deduplication with result storage
 * Supports both API-level idempotency and Event-level deduplication
 */
@Service
public class EventDeduplicationService {

    private static final Logger logger = LoggerFactory.getLogger(EventDeduplicationService.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24); // Keep processed events for 24 hours

    private final ProcessedEventRepository processedEventRepository;

    public EventDeduplicationService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Execute idempotent operation with database-first deduplication and result storage
     * This method uses database constraints for thread-safe deduplication
     * Uses unified database-first approach for both API and EVENT types
     *
     * @param eventId the unique event identifier
     * @param eventType the event type (API or EVENT)
     * @param processor the processing logic to execute if event is not duplicate
     * @param <T> the type of result returned by the processor
     * @return the result of the processor if event was processed, or stored result if it was a duplicate
     */
    @Transactional
    public <T> T executeIdempotent(String eventId, EventType eventType, Supplier<T> processor) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(DEFAULT_TTL);

        // Use unified database-first approach for both API and EVENT types
        return executeDatabaseFirst(eventId, eventType, processor, now, expiresAt);
    }

    /**
     * Unified database-first approach for both API and EVENT types
     * This prevents duplicate business logic execution and ensures thread safety
     */
    private <T> T executeDatabaseFirst(
            String eventId, EventType eventType, Supplier<T> processor, LocalDateTime now, LocalDateTime expiresAt) {
        // First, try to claim the event by inserting a "PROCESSING" marker
        int inserted = processedEventRepository.insertIfNotExistsWithResult(
                eventId, eventType.name(), now, expiresAt, "PROCESSING");

        if (inserted > 0) {
            // We successfully claimed the event - execute business logic
            logger.debug("Successfully claimed event {} with type {} for processing", eventId, eventType);

            try {
                T result = processor.get();
                String resultData = result != null ? result.toString() : null;

                // Update the result in the database (replace "PROCESSING" with actual result)
                updateProcessedEventResult(eventId, eventType.name(), resultData);

                logger.info("Successfully processed event {} with type {} and stored result", eventId, eventType);
                return result;
            } catch (Exception e) {
                // If processing fails, we should clean up the "PROCESSING" marker
                // For now, we'll leave it as is - the TTL will clean it up eventually
                logger.error("Failed to process event {} with type {}", eventId, eventType, e);
                throw e;
            }
        } else {
            // Event is already being processed or was processed - skip it
            logger.info(
                    "Skipping duplicate event {} with type {} - already processed or being processed",
                    eventId,
                    eventType);

            // Try to get the stored result (might be "PROCESSING" if still in progress)
            String storedResult = processedEventRepository.getResultData(eventId, eventType.name(), now);
            if (storedResult != null && !"PROCESSING".equals(storedResult)) {
                @SuppressWarnings("unchecked")
                T typedResult = (T) storedResult;
                return typedResult;
            }

            // If result is still "PROCESSING" or null, return null (event is being processed by another thread)
            return null;
        }
    }

    /**
     * Update the result data for an already processed event.
     * This replaces the "PROCESSING" marker with the actual result.
     */
    private void updateProcessedEventResult(String eventId, String eventType, String resultData) {
        try {
            // Use a simple update query to replace the result_data
            int updated = processedEventRepository.updateResultData(eventId, eventType, resultData);
            if (updated == 0) {
                logger.warn("Failed to update result for event {} with type {} - record not found", eventId, eventType);
            }
        } catch (Exception e) {
            logger.error("Failed to update result for event {} with type {}", eventId, eventType, e);
            // Don't throw - the processing was successful, just the result update failed
        }
    }

    /**
     * Clean up expired processed events
     * This method should be called periodically to maintain database performance
     * @return number of deleted expired events
     */
    @Transactional
    public int cleanupExpiredEvents() {
        int deletedCount = processedEventRepository.deleteExpiredEvents(LocalDateTime.now());
        if (deletedCount > 0) {
            logger.info("Cleaned up {} expired processed events", deletedCount);
        }
        return deletedCount;
    }
}
