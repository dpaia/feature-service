package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.PlanningHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PlanningHistoryRepository
        extends JpaRepository<PlanningHistory, Long>, JpaSpecificationExecutor<PlanningHistory> {}
