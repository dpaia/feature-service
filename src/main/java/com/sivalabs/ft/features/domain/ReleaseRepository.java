package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReleaseRepository extends JpaRepository<Release, Long>, JpaSpecificationExecutor<Release> {
    Optional<Release> findByCode(String code);

    List<Release> findByProductCode(String productCode);

    @Modifying
    void deleteByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT r FROM Release r WHERE r.plannedReleaseDate < :now AND r.status NOT IN :excludedStatuses")
    Page<Release> findOverdue(
            @Param("now") Instant now,
            @Param("excludedStatuses") List<ReleaseStatus> excludedStatuses,
            Pageable pageable);

    @Query(
            "SELECT r FROM Release r WHERE r.plannedReleaseDate BETWEEN :now AND :deadline AND r.status NOT IN :excludedStatuses")
    Page<Release> findAtRisk(
            @Param("now") Instant now,
            @Param("deadline") Instant deadline,
            @Param("excludedStatuses") List<ReleaseStatus> excludedStatuses,
            Pageable pageable);

    Page<Release> findByStatus(ReleaseStatus status, Pageable pageable);

    Page<Release> findByOwner(String owner, Pageable pageable);

    Page<Release> findByPlannedReleaseDateBetween(Instant startDate, Instant endDate, Pageable pageable);
}
