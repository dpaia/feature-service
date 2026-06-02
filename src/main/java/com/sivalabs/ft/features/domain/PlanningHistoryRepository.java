package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.PlanningHistory;
import com.sivalabs.ft.features.domain.models.EntityType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningHistoryRepository extends JpaRepository<PlanningHistory, Long> {

    /**
     * Find planning history by entity type and entity code with pagination
     */
    Page<PlanningHistory> findByEntityTypeAndEntityCodeOrderByChangedAtDesc(
            EntityType entityType, String entityCode, Pageable pageable);

    /**
     * Find planning history by entity code (regardless of entity type) with
     * pagination
     */
    Page<PlanningHistory> findByEntityCodeOrderByChangedAtDesc(String entityCode, Pageable pageable);

    /**
     * Find all planning history ordered by changed date
     */
    Page<PlanningHistory> findAllByOrderByChangedAtDesc(Pageable pageable);

    /**
     * Find all planning history (for filtering in service layer)
     */
    List<PlanningHistory> findAllByOrderByChangedAtDesc();

    /**
     * Check if entity exists by entity type and entity code
     */
    boolean existsByEntityTypeAndEntityCode(EntityType entityType, String entityCode);

    /**
     * Find latest changes for an entity
     */
    List<PlanningHistory> findTop10ByEntityTypeAndEntityCodeOrderByChangedAtDesc(
            EntityType entityType, String entityCode);
}
