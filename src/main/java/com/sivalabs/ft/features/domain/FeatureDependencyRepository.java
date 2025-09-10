package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.FeatureDependency;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

interface FeatureDependencyRepository extends ListCrudRepository<FeatureDependency, Long> {

    List<FeatureDependency> findByFeature_Code(String featureCode);

    List<FeatureDependency> findByDependsOnFeature_Code(String dependsOnFeatureCode);

    Optional<FeatureDependency> findByFeature_CodeAndDependsOnFeature_Code(
            String featureCode, String dependsOnFeatureCode);
}
