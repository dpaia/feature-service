package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.ErrorLogDto;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ErrorLogMapper;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ErrorLoggingService {

    private final ErrorLogRepository errorLogRepository;
    private final ErrorLogMapper mapper;

    public ErrorLoggingService(ErrorLogRepository errorLogRepository, ErrorLogMapper mapper) {
        this.errorLogRepository = errorLogRepository;
        this.mapper = mapper;
    }

    @Transactional
    public void logError(ErrorType errorType, String message, Throwable exception, String eventPayload, String userId) {
        ErrorLog errorLog = new ErrorLog();
        errorLog.setTimestamp(Instant.now());
        errorLog.setErrorType(errorType);
        errorLog.setErrorMessage(message);
        errorLog.setStackTrace(getStackTraceAsString(exception));
        errorLog.setEventPayload(eventPayload);
        errorLog.setUserId(userId);
        errorLog.setResolved(false);

        errorLogRepository.save(errorLog);
    }

    public Page<ErrorLogDto> getErrors(int page, int size, ErrorType errorType, Instant startDate, Instant endDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        if (errorType != null && startDate != null && endDate != null) {
            return errorLogRepository
                    .findByErrorTypeAndTimestampBetween(errorType, startDate, endDate, pageable)
                    .map(mapper::toDto);
        } else if (errorType != null) {
            return errorLogRepository.findByErrorType(errorType, pageable).map(mapper::toDto);
        } else if (startDate != null && endDate != null) {
            return errorLogRepository
                    .findByTimestampBetween(startDate, endDate, pageable)
                    .map(mapper::toDto);
        }

        return errorLogRepository.findAll(pageable).map(mapper::toDto);
    }

    public ErrorLogDto getErrorById(Long id) {
        return errorLogRepository
                .findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Error log not found"));
    }

    private String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) return null;
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
