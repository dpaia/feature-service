package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.FeatureDependency;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

interface FeatureDependencyRepository extends ListCrudRepository<FeatureDependency, Long> {

    List<FeatureDependency> findByFeature_Code(String featureCode);

    List<FeatureDependency> findByDependsOnFeature_Code(String dependsOnFeatureCode);

    Optional<FeatureDependency> findByFeature_CodeAndDependsOnFeature_Code(
            String featureCode, String dependsOnFeatureCode);

    // Find all dependencies with filtering
    @Query("SELECT fd FROM FeatureDependency fd " + "JOIN FETCH fd.feature f "
            + "LEFT JOIN FETCH f.product "
            + "LEFT JOIN FETCH f.release "
            + "JOIN FETCH fd.dependsOnFeature dof "
            + "LEFT JOIN FETCH dof.product "
            + "LEFT JOIN FETCH dof.release "
            + "WHERE fd.feature.code = :featureCode "
            + "AND (:productCode IS NULL OR dof.product.code = :productCode)")
    List<FeatureDependency> findDependenciesWithFiltering(
            @Param("featureCode") String featureCode, @Param("productCode") String productCode);

    // Find all dependencies with full filtering including release and status
    @Query("SELECT fd FROM FeatureDependency fd " + "JOIN FETCH fd.feature f "
            + "LEFT JOIN FETCH f.product "
            + "LEFT JOIN FETCH f.release "
            + "JOIN FETCH fd.dependsOnFeature dof "
            + "LEFT JOIN FETCH dof.product "
            + "LEFT JOIN FETCH dof.release "
            + "WHERE fd.feature.code = :featureCode "
            + "AND (:productCode IS NULL OR dof.product.code = :productCode) "
            + "AND (:releaseCode IS NULL OR dof.release.code = :releaseCode) "
            + "AND (:status IS NULL OR dof.status = :status)")
    List<FeatureDependency> findDependenciesWithFullFiltering(
            @Param("featureCode") String featureCode,
            @Param("productCode") String productCode,
            @Param("releaseCode") String releaseCode,
            @Param("status") FeatureStatus status);

    // Find all dependents with filtering
    @Query("SELECT fd FROM FeatureDependency fd " + "JOIN FETCH fd.feature f "
            + "LEFT JOIN FETCH f.product "
            + "LEFT JOIN FETCH f.release "
            + "JOIN FETCH fd.dependsOnFeature dof "
            + "LEFT JOIN FETCH dof.product "
            + "LEFT JOIN FETCH dof.release "
            + "WHERE fd.dependsOnFeature.code = :featureCode "
            + "AND (:productCode IS NULL OR f.product.code = :productCode) "
            + "AND (:status IS NULL OR f.status = :status)")
    List<FeatureDependency> findDependentsWithFiltering(
            @Param("featureCode") String featureCode,
            @Param("productCode") String productCode,
            @Param("status") FeatureStatus status);

    // Find all dependents with filtering by release code
    @Query("SELECT fd FROM FeatureDependency fd " + "JOIN FETCH fd.feature f "
            + "LEFT JOIN FETCH f.product "
            + "LEFT JOIN FETCH f.release "
            + "JOIN FETCH fd.dependsOnFeature dof "
            + "LEFT JOIN FETCH dof.product "
            + "LEFT JOIN FETCH dof.release "
            + "WHERE fd.dependsOnFeature.code = :featureCode "
            + "AND (:releaseCode IS NULL OR f.release.code = :releaseCode)")
    List<FeatureDependency> findDependentsByRelease(
            @Param("featureCode") String featureCode, @Param("releaseCode") String releaseCode);
}
