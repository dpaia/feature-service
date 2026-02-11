package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.ErrorLog;
import com.sivalabs.ft.features.domain.models.ErrorType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    Page<ErrorLog> findByErrorType(ErrorType errorType, Pageable pageable);

    Page<ErrorLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);

    Page<ErrorLog> findByErrorTypeAndTimestampBetween(
            ErrorType errorType, Instant start, Instant end, Pageable pageable);

    long countByTimestampBetween(Instant start, Instant end);

    @Query("SELECT COUNT(e) FROM ErrorLog e WHERE e.errorType = :errorType AND e.timestamp BETWEEN :start AND :end")
    long countByErrorTypeAndTimestampBetween(
            @Param("errorType") ErrorType errorType, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT e FROM ErrorLog e ORDER BY e.timestamp DESC LIMIT 1")
    ErrorLog findLatestErrorLog();
}
