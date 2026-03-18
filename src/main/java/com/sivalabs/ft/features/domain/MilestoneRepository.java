package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Milestone;
import com.sivalabs.ft.features.domain.models.MilestoneStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

interface MilestoneRepository extends ListCrudRepository<Milestone, Long> {
    Optional<Milestone> findByCode(String code);

    List<Milestone> findByProductCode(String productCode);

    List<Milestone> findByProductCodeAndStatus(String productCode, MilestoneStatus status);

    List<Milestone> findByProductCodeAndOwner(String productCode, String owner);

    List<Milestone> findByProductCodeAndStatusAndOwner(String productCode, MilestoneStatus status, String owner);

    boolean existsByCode(String code);
}
