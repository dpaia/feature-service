package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.ErrorLogDto;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.mappers.ErrorLogMapper;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErrorLoggingService {
    private static final Logger log = LoggerFactory.getLogger(ErrorLoggingService.class);
    private final ErrorLogRepository errorLogRepository;
    private final ErrorLogMapper errorLogMapper;

    public ErrorLoggingService(ErrorLogRepository errorLogRepository, ErrorLogMapper errorLogMapper) {
        this.errorLogRepository = errorLogRepository;
        this.errorLogMapper = errorLogMapper;
    }

    @Transactional
    public void logError(ErrorType errorType, String message, Exception exception, String eventPayload, String userId) {
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setTimestamp(Instant.now());
            errorLog.setErrorType(errorType);
            errorLog.setErrorMessage(message);
            errorLog.setEventPayload(eventPayload);
            errorLog.setUserId(userId);

            if (exception != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                exception.printStackTrace(pw);
                errorLog.setStackTrace(sw.toString());
            }

            errorLogRepository.save(errorLog);
            log.debug("Logged error: type={}, message={}", errorType, message);
        } catch (Exception e) {
            log.error("Failed to log error to database", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ErrorLogDto> getErrors(Pageable pageable, ErrorType errorType, Instant startDate, Instant endDate) {
        Page<ErrorLog> errorLogs;

        if (errorType != null && startDate != null && endDate != null) {
            errorLogs = errorLogRepository.findByErrorTypeAndTimestampBetween(errorType, startDate, endDate, pageable);
        } else if (errorType != null) {
            errorLogs = errorLogRepository.findByErrorType(errorType, pageable);
        } else if (startDate != null && endDate != null) {
            errorLogs = errorLogRepository.findByTimestampBetween(startDate, endDate, pageable);
        } else {
            errorLogs = errorLogRepository.findAll(pageable);
        }

        return errorLogs.map(errorLogMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ErrorLogDto> getErrorById(Long id) {
        return errorLogRepository.findById(id).map(errorLogMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ErrorLog> getErrorEntityById(Long id) {
        return errorLogRepository.findById(id);
    }

    @Transactional
    public ErrorLog saveErrorLog(ErrorLog errorLog) {
        return errorLogRepository.save(errorLog);
    }
}
