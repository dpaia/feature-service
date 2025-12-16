package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureUsageRepository extends JpaRepository<FeatureUsage, Long> {

    Page<FeatureUsage> findByUserId(String userId, Pageable pageable);

    Page<FeatureUsage> findByFeatureCode(String featureCode, Pageable pageable);

    Page<FeatureUsage> findByProductCode(String productCode, Pageable pageable);

    Page<FeatureUsage> findByActionType(ActionType actionType, Pageable pageable);

    Page<FeatureUsage> findByTimestampBetween(Instant startDate, Instant endDate, Pageable pageable);

    @Query(
            """
            SELECT fu FROM FeatureUsage fu
            WHERE (CAST(:userId AS string) IS NULL OR fu.userId = :userId)
            AND (CAST(:featureCode AS string) IS NULL OR fu.featureCode = :featureCode)
            AND (CAST(:productCode AS string) IS NULL OR fu.productCode = :productCode)
            AND (CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType)
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            ORDER BY fu.timestamp DESC
            """)
    Page<FeatureUsage> findByFilters(
            @Param("userId") String userId,
            @Param("featureCode") String featureCode,
            @Param("productCode") String productCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    @Query(
            """
            SELECT fu.featureCode, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.featureCode IS NOT NULL
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.featureCode
            ORDER BY count DESC
            """)
    List<Object[]> findTopFeatures(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.userId, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.userId
            ORDER BY count DESC
            """)
    List<Object[]> findTopUsers(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.actionType, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.actionType
            ORDER BY count DESC
            """)
    List<Object[]> findActionTypeStats(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    long countByUserId(String userId);

    long countByFeatureCode(String featureCode);

    long countByProductCode(String productCode);

    long countByActionType(ActionType actionType);

    long countByTimestampBetween(Instant startDate, Instant endDate);

    @Query(
            """
            SELECT COUNT(DISTINCT fu.userId)
            FROM FeatureUsage fu
            WHERE (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countDistinctUsers(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT COUNT(DISTINCT fu.featureCode)
            FROM FeatureUsage fu
            WHERE fu.featureCode IS NOT NULL
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countDistinctFeatures(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT COUNT(DISTINCT fu.productCode)
            FROM FeatureUsage fu
            WHERE fu.productCode IS NOT NULL
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countDistinctProducts(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.productCode, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.productCode IS NOT NULL
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.productCode
            ORDER BY count DESC
            """)
    List<Object[]> findTopProducts(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    // Feature-specific queries
    @Query(
            """
            SELECT COUNT(DISTINCT fu.userId)
            FROM FeatureUsage fu
            WHERE fu.featureCode = :featureCode
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countDistinctUsersByFeatureCode(
            @Param("featureCode") String featureCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.actionType, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.featureCode = :featureCode
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.actionType
            ORDER BY count DESC
            """)
    List<Object[]> findActionTypeStatsByFeatureCode(
            @Param("featureCode") String featureCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.userId, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.featureCode = :featureCode
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.userId
            ORDER BY count DESC
            """)
    List<Object[]> findTopUsersByFeatureCode(
            @Param("featureCode") String featureCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.productCode, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.featureCode = :featureCode
            AND fu.productCode IS NOT NULL
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.productCode
            ORDER BY count DESC
            """)
    List<Object[]> findUsageByProductForFeature(
            @Param("featureCode") String featureCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    // Product-specific queries
    @Query(
            """
            SELECT COUNT(DISTINCT fu.userId)
            FROM FeatureUsage fu
            WHERE fu.productCode = :productCode
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countDistinctUsersByProductCode(
            @Param("productCode") String productCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT COUNT(DISTINCT fu.featureCode)
            FROM FeatureUsage fu
            WHERE fu.productCode = :productCode
            AND fu.featureCode IS NOT NULL
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countDistinctFeaturesByProductCode(
            @Param("productCode") String productCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.actionType, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.productCode = :productCode
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.actionType
            ORDER BY count DESC
            """)
    List<Object[]> findActionTypeStatsByProductCode(
            @Param("productCode") String productCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.featureCode, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.productCode = :productCode
            AND fu.featureCode IS NOT NULL
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.featureCode
            ORDER BY count DESC
            """)
    List<Object[]> findTopFeaturesByProductCode(
            @Param("productCode") String productCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT fu.userId, COUNT(fu) as count
            FROM FeatureUsage fu
            WHERE fu.productCode = :productCode
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            GROUP BY fu.userId
            ORDER BY count DESC
            """)
    List<Object[]> findTopUsersByProductCode(
            @Param("productCode") String productCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    long countByFeatureCodeAndTimestampBetween(String featureCode, Instant startDate, Instant endDate);

    long countByProductCodeAndTimestampBetween(String productCode, Instant startDate, Instant endDate);
}
