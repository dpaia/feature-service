package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    Page<ErrorLog> findByErrorType(ErrorType errorType, Pageable pageable);

    Page<ErrorLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);

    Page<ErrorLog> findByErrorTypeAndTimestampBetween(
            ErrorType errorType, Instant start, Instant end, Pageable pageable);

    Page<ErrorLog> findByResolvedAndTimestampBetween(Boolean resolved, Instant start, Instant end, Pageable pageable);

    Long countByTimestampBetween(Instant start, Instant end);

    Long countByErrorType(ErrorType errorType);
}
