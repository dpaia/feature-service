package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.api.models.CreateUsageEventPayload;
import com.sivalabs.ft.features.domain.dtos.ReprocessResultDto;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReprocessService {

    private final ErrorLogRepository errorLogRepository;
    private final FeatureUsageService featureUsageService;
    private final ObjectMapper objectMapper;

    public ReprocessService(
            ErrorLogRepository errorLogRepository, FeatureUsageService featureUsageService, ObjectMapper objectMapper) {
        this.errorLogRepository = errorLogRepository;
        this.featureUsageService = featureUsageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReprocessResultDto reprocessErrors(
            List<Long> errorLogIds, Instant startDate, Instant endDate, boolean dryRun) {
        List<ErrorLog> errorLogs = fetchErrorLogs(errorLogIds, startDate, endDate);

        int totalProcessed = errorLogs.size();
        int successCount = 0;
        int failedCount = 0;
        List<ReprocessResultDto.ErrorDetail> errors = new ArrayList<>();

        for (ErrorLog errorLog : errorLogs) {
            try {
                // Parse original event payload
                CreateUsageEventPayload payload =
                        objectMapper.readValue(errorLog.getEventPayload(), CreateUsageEventPayload.class);

                // Retry event creation
                if (!dryRun) {
                    featureUsageService.createUsageEvent(
                            errorLog.getUserId(),
                            payload.featureCode(),
                            payload.productCode(),
                            payload.releaseCode(),
                            payload.actionType(),
                            payload.context(),
                            null, // ipAddress not stored in error_log
                            null // userAgent not stored in error_log
                            );

                    // Mark as resolved
                    errorLog.setResolved(true);
                    errorLogRepository.save(errorLog);
                }

                successCount++;
            } catch (Exception e) {
                failedCount++;
                errors.add(new ReprocessResultDto.ErrorDetail(errorLog.getId(), e.getMessage()));
            }
        }

        return new ReprocessResultDto(totalProcessed, successCount, failedCount, errors);
    }

    private List<ErrorLog> fetchErrorLogs(List<Long> errorLogIds, Instant startDate, Instant endDate) {
        if (errorLogIds != null && !errorLogIds.isEmpty()) {
            // Process all specified IDs (ignore resolved status - allow manual retry)
            return errorLogRepository.findAllById(errorLogIds);
        } else if (startDate != null && endDate != null) {
            // Process only unresolved errors in date range
            return errorLogRepository
                    .findByResolvedAndTimestampBetween(false, startDate, endDate, Pageable.unpaged())
                    .getContent();
        }
        throw new BadRequestException("Either errorLogIds or dateRange must be provided");
    }
}
