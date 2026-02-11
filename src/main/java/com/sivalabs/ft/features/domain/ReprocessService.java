package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.api.models.CreateUsageEventPayload;
import com.sivalabs.ft.features.domain.dtos.ErrorLogDto;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ErrorLogMapper;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReprocessService {
    private static final Logger log = LoggerFactory.getLogger(ReprocessService.class);
    private final ErrorLoggingService errorLoggingService;
    private final FeatureUsageService featureUsageService;
    private final ErrorLogMapper errorLogMapper;
    private final ObjectMapper objectMapper;

    public ReprocessService(
            ErrorLoggingService errorLoggingService,
            FeatureUsageService featureUsageService,
            ErrorLogMapper errorLogMapper,
            ObjectMapper objectMapper) {
        this.errorLoggingService = errorLoggingService;
        this.featureUsageService = featureUsageService;
        this.errorLogMapper = errorLogMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ErrorLogDto reprocessSingleError(Long errorLogId) {
        // Fetch original error log record (404 if not found)
        ErrorLog originalErrorLog = errorLoggingService
                .getErrorEntityById(errorLogId)
                .orElseThrow(() -> new ResourceNotFoundException("Error log not found with id: " + errorLogId));

        String eventPayload = originalErrorLog.getEventPayload();
        if (eventPayload == null || eventPayload.isBlank()) {
            // Create new error log for missing payload
            ErrorLog newErrorLog =
                    createReprocessingError(originalErrorLog, "Cannot reprocess: event payload is missing", null);
            return errorLogMapper.toDto(newErrorLog);
        }

        try {
            // Parse event payload
            CreateUsageEventPayload payload = objectMapper.readValue(eventPayload, CreateUsageEventPayload.class);

            // Try to reprocess via FeatureUsageService
            String userId = originalErrorLog.getUserId() != null ? originalErrorLog.getUserId() : "anonymous";
            featureUsageService.logUsage(
                    userId,
                    payload.featureCode(),
                    payload.productCode(),
                    payload.releaseCode(),
                    payload.actionType(),
                    payload.context(),
                    null,
                    null);

            // Success: update original record to resolved=true
            originalErrorLog.setResolved(true);
            ErrorLog updatedErrorLog = errorLoggingService.saveErrorLog(originalErrorLog);
            log.info("Successfully reprocessed error log id: {}", errorLogId);
            return errorLogMapper.toDto(updatedErrorLog);

        } catch (Exception e) {
            // Failure: create new error log entry
            log.warn("Failed to reprocess error log id: {}", errorLogId, e);
            ErrorLog newErrorLog = createReprocessingError(originalErrorLog, e.getMessage(), e);
            return errorLogMapper.toDto(newErrorLog);
        }
    }

    private ErrorLog createReprocessingError(ErrorLog originalErrorLog, String errorMessage, Exception exception) {
        ErrorLog newErrorLog = new ErrorLog();
        newErrorLog.setTimestamp(Instant.now());
        newErrorLog.setErrorType(ErrorType.PROCESSING_ERROR);
        newErrorLog.setErrorMessage(
                "Reprocessing failed for error log id " + originalErrorLog.getId() + ": " + errorMessage);
        newErrorLog.setEventPayload(originalErrorLog.getEventPayload());
        newErrorLog.setUserId(originalErrorLog.getUserId());
        newErrorLog.setResolved(false);

        if (exception != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            exception.printStackTrace(pw);
            newErrorLog.setStackTrace(sw.toString());
        }

        return errorLoggingService.saveErrorLog(newErrorLog);
    }
}
