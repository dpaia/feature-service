package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.events.EventReplayService;
import com.sivalabs.ft.features.domain.events.EventReplayService.EventReplayDetail;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for event replay functionality
 * Allows replaying events from the event store with various filtering options
 */
@RestController
@RequestMapping("/api/events/replay")
@Tag(name = "Event Replay", description = "APIs for replaying events from event store")
public class EventReplayController {

    private static final Logger logger = LoggerFactory.getLogger(EventReplayController.class);

    private final EventReplayService eventReplayService;

    public EventReplayController(EventReplayService eventReplayService) {
        this.eventReplayService = eventReplayService;
    }

    /**
     * Replay all events within a time range
     * Validates: startTime < endTime, max 365 days range
     */
    @PostMapping("/time-range")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Replay events by time range",
            description = "Replay all events within specified time range. Validates date range (max 365 days).")
    public ResponseEntity<ReplayResponse> replayByTimeRange(
            @Parameter(description = "Start time (ISO format)", example = "2024-01-01T00:00:00")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startTime,
            @Parameter(description = "End time (ISO format)", example = "2024-12-31T23:59:59")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endTime) {

        logger.info("Received request to replay events from {} to {}", startTime, endTime);

        // Validate date range
        validateDateRange(startTime, endTime);

        // Get count for preview
        long totalEvents = eventReplayService.countEventsInTimeRange(startTime, endTime);
        logger.info("Found {} events in time range", totalEvents);

        // Replay events
        var replayResult = eventReplayService.replayEventsByTimeRange(startTime, endTime);

        ReplayResponse response = new ReplayResponse(
                "Time range replay completed",
                totalEvents,
                replayResult.successCount(),
                replayResult.failedCount(),
                startTime.toString(),
                endTime.toString(),
                replayResult.eventDetails(),
                null);

        return ResponseEntity.ok(response);
    }

    /**
     * Replay events for specific features within a time range
     * Validates: date range + non-empty feature codes list
     */
    @PostMapping("/features")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Replay events by features",
            description =
                    "Replay events for specific features within time range. Validates date range and non-empty feature codes list.")
    public ResponseEntity<ReplayResponse> replayByFeatures(
            @Parameter(description = "Start time (ISO format)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startTime,
            @Parameter(description = "End time (ISO format)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endTime,
            @Parameter(description = "List of feature codes", example = "[\"PROD-1\", \"PROD-2\"]") @RequestBody
                    List<String> featureCodes) {

        logger.info(
                "Received request to replay events for features {} from {} to {}", featureCodes, startTime, endTime);

        // Validate date range
        validateDateRange(startTime, endTime);

        // Validate feature codes
        if (featureCodes == null || featureCodes.isEmpty()) {
            throw new BadRequestException("Feature codes list cannot be empty");
        }

        // Replay events
        var replayResult = eventReplayService.replayEventsByFeatures(startTime, endTime, featureCodes);

        ReplayResponse response = new ReplayResponse(
                "Feature-specific replay completed",
                (long) (replayResult.successCount() + replayResult.failedCount()),
                replayResult.successCount(),
                replayResult.failedCount(),
                startTime.toString(),
                endTime.toString(),
                replayResult.eventDetails(),
                null);

        return ResponseEntity.ok(response);
    }

    /**
     * Replay events by operation type within a time range
     * Validates: date range (max 365 days)
     */
    @PostMapping("/operation/{operationType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Replay events by operation type",
            description =
                    "Replay events of specific operation type within time range. Validates date range (max 365 days).")
    public ResponseEntity<ReplayResponse> replayByOperation(
            @Parameter(description = "Operation type", example = "CREATED") @PathVariable String operationType,
            @Parameter(description = "Start time (ISO format)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startTime,
            @Parameter(description = "End time (ISO format)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endTime) {

        logger.info(
                "Received request to replay {} events from {} to {}", operationType.toUpperCase(), startTime, endTime);

        // Validate date range
        validateDateRange(startTime, endTime);

        // Replay events
        var replayResult = eventReplayService.replayEventsByOperation(startTime, endTime, operationType.toUpperCase());

        ReplayResponse response = new ReplayResponse(
                "Operation-specific replay completed",
                (long) (replayResult.successCount() + replayResult.failedCount()),
                replayResult.successCount(),
                replayResult.failedCount(),
                startTime.toString(),
                endTime.toString(),
                replayResult.eventDetails(),
                operationType.toUpperCase());

        return ResponseEntity.ok(response);
    }

    /**
     * Replay all events for a specific feature
     */
    @PostMapping("/feature/{featureCode}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replay events by feature code", description = "Replay all events for a specific feature")
    public ResponseEntity<ReplayResponse> replayByFeatureCode(
            @Parameter(description = "Feature code", example = "PROD-123") @PathVariable String featureCode) {

        logger.info("Received request to replay all events for feature {}", featureCode);

        // Replay events
        var replayResult = eventReplayService.replayEventsByFeatureCode(featureCode);

        ReplayResponse response = new ReplayResponse(
                "Feature replay completed",
                (long) (replayResult.successCount() + replayResult.failedCount()),
                replayResult.successCount(),
                replayResult.failedCount(),
                null,
                null,
                replayResult.eventDetails(),
                null);

        return ResponseEntity.ok(response);
    }

    /**
     * Get count of events in time range (preview before replay)
     * Validates: date range (max 365 days)
     */
    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Count events in time range",
            description = "Get count of events for preview before replay. Validates date range (max 365 days).")
    public ResponseEntity<CountResponse> countEvents(
            @Parameter(description = "Start time (ISO format)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startTime,
            @Parameter(description = "End time (ISO format)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endTime) {

        // Validate date range
        validateDateRange(startTime, endTime);

        long count = eventReplayService.countEventsInTimeRange(startTime, endTime);

        CountResponse response = new CountResponse(count, startTime.toString(), endTime.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Response DTO for replay operations
     */
    public record ReplayResponse(
            String message,
            long totalEvents,
            int replayedEvents,
            int failedEvents,
            String startTime,
            String endTime,
            List<EventReplayDetail> eventDetails,
            String operationType) {}

    /**
     * Response DTO for count operations
     */
    public record CountResponse(long count, String startTime, String endTime) {}

    /**
     * Validate date range parameters
     */
    private void validateDateRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Start time and end time are required");
        }

        if (startTime.isAfter(endTime)) {
            throw new BadRequestException("Start time must be before end time");
        }

        // Prevent too large time ranges (more than 1 year)
        Duration duration = Duration.between(startTime, endTime);
        if (duration.toDays() > 365) {
            throw new BadRequestException("Time range cannot exceed 365 days");
        }

        // Warn about future dates
        LocalDateTime now = LocalDateTime.now();
        if (startTime.isAfter(now)) {
            logger.warn("Start time {} is in the future", startTime);
        }
    }
}
