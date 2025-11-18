package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

interface ReleaseRepository extends ListCrudRepository<Release, Long>, PagingAndSortingRepository<Release, Long> {
    Optional<Release> findByCode(String code);

    List<Release> findByProductCode(String productCode);

    @Modifying
    void deleteByCode(String code);

    boolean existsByCode(String code);

    // Query for overdue releases: past plannedReleaseDate but not COMPLETED/CANCELLED
    @Query(
            """
        SELECT r FROM Release r
        WHERE r.status NOT IN ('COMPLETED', 'CANCELLED')
        AND r.plannedReleaseDate < :currentTime
        """)
    Page<Release> findOverdueReleases(@Param("currentTime") Instant currentTime, Pageable pageable);

    // Query for at-risk releases: within threshold days of deadline
    @Query(
            """
        SELECT r FROM Release r
        WHERE r.status NOT IN ('COMPLETED', 'CANCELLED')
        AND r.plannedReleaseDate > :currentTime
        AND r.plannedReleaseDate <= :thresholdTime
        """)
    Page<Release> findAtRiskReleases(
            @Param("currentTime") Instant currentTime,
            @Param("thresholdTime") Instant thresholdTime,
            Pageable pageable);

    // Query by status
    Page<Release> findByStatus(ReleaseStatus status, Pageable pageable);

    // Query by release owner
    Page<Release> findByReleaseOwner(String releaseOwner, Pageable pageable);

    // Query by date range
    @Query(
            """
        SELECT r FROM Release r
        WHERE r.plannedReleaseDate BETWEEN :startDate AND :endDate
        """)
    Page<Release> findByPlannedReleaseDateBetween(
            @Param("startDate") Instant startDate, @Param("endDate") Instant endDate, Pageable pageable);

    // Query by date range with optional status filter
    @Query(
            """
        SELECT r FROM Release r
        WHERE r.plannedReleaseDate BETWEEN :startDate AND :endDate
        AND (:status IS NULL OR r.status = :status)
        """)
    Page<Release> findByPlannedReleaseDateBetweenAndStatus(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("status") ReleaseStatus status,
            Pageable pageable);

    // Enhanced query with multiple filters
    @Query(
            """
        SELECT r FROM Release r JOIN r.product p
        WHERE (:productCode IS NULL OR p.code = :productCode)
        AND (:status IS NULL OR r.status = :status)
        AND (:owner IS NULL OR r.releaseOwner = :owner)
        AND (CAST(:startDate AS java.time.Instant) IS NULL OR r.plannedReleaseDate >= :startDate)
        AND (CAST(:endDate AS java.time.Instant) IS NULL OR r.plannedReleaseDate <= :endDate)
        ORDER BY r.createdAt DESC
        """)
    Page<Release> findWithFilters(
            @Param("productCode") String productCode,
            @Param("status") ReleaseStatus status,
            @Param("owner") String owner,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);
}
