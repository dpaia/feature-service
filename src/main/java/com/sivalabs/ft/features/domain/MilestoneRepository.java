package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Milestone;
import com.sivalabs.ft.features.domain.models.MilestoneStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

interface MilestoneRepository extends ListCrudRepository<Milestone, Long> {
    Optional<Milestone> findByCode(String code);

    List<Milestone> findByProductCode(String productCode);

    List<Milestone> findByProductCodeAndStatus(String productCode, MilestoneStatus status);

    List<Milestone> findByProductCodeAndOwner(String productCode, String owner);

    List<Milestone> findByProductCodeAndStatusAndOwner(String productCode, MilestoneStatus status, String owner);

    @Modifying
    void deleteByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT m FROM Milestone m LEFT JOIN FETCH m.releases WHERE m.code = :code")
    Optional<Milestone> findByCodeWithReleases(@Param("code") String code);
}
