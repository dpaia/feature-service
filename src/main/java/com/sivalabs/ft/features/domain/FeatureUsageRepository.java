package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureUsageRepository extends JpaRepository<FeatureUsage, Long> {}
