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

    // Find by feature code (paginated)
    Page<FeatureUsage> findByFeatureCodeOrderByTimestampDesc(String featureCode, Pageable pageable);

    // Find by product code (paginated)
    Page<FeatureUsage> findByProductCodeOrderByTimestampDesc(String productCode, Pageable pageable);

    // Find by user id
    Page<FeatureUsage> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    // Find with filters (paginated)
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
    Page<FeatureUsage> findWithFiltersPaginated(
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("userId") String userId,
            @Param("featureCode") String featureCode,
            @Param("productCode") String productCode,
            Pageable pageable);
    // Find feature events with filters (paginated)
    @Query(
            """
            SELECT fu FROM FeatureUsage fu
            WHERE fu.featureCode = :featureCode
            AND (CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType)
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            ORDER BY fu.timestamp DESC
            """)
    Page<FeatureUsage> findFeatureEventsWithFiltersPaginated(
            @Param("featureCode") String featureCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    // Find product events with filters (paginated)
    @Query(
            """
            SELECT fu FROM FeatureUsage fu
            WHERE fu.productCode = :productCode
            AND (CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType)
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            ORDER BY fu.timestamp DESC
            """)
    Page<FeatureUsage> findProductEventsWithFiltersPaginated(
            @Param("productCode") String productCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    // Statistics queries
    @Query(
            """
            SELECT COUNT(fu) FROM FeatureUsage fu
            WHERE (CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType)
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countWithFilters(
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT COUNT(DISTINCT fu.userId) FROM FeatureUsage fu
            WHERE (CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType)
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countUniqueUsersWithFilters(
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT COUNT(DISTINCT fu.featureCode) FROM FeatureUsage fu
            WHERE fu.featureCode IS NOT NULL
            AND (CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType)
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countUniqueFeaturesWithFilters(
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            """
            SELECT COUNT(DISTINCT fu.productCode) FROM FeatureUsage fu
            WHERE fu.productCode IS NOT NULL
            AND (CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType)
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countUniqueProductsWithFilters(
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);
    // Feature-specific statistics
    @Query("SELECT COUNT(fu) FROM FeatureUsage fu WHERE fu.featureCode = :featureCode AND "
            + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)")
    long countByFeatureCodeWithFilters(
            @Param("featureCode") String featureCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query("SELECT COUNT(DISTINCT fu.userId) FROM FeatureUsage fu WHERE fu.featureCode = :featureCode AND "
            + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)")
    long countUniqueUsersByFeatureCodeWithFilters(
            @Param("featureCode") String featureCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    // Product-specific statistics
    @Query("SELECT COUNT(fu) FROM FeatureUsage fu WHERE fu.productCode = :productCode AND "
            + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)")
    long countByProductCodeWithFilters(
            @Param("productCode") String productCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query("SELECT COUNT(DISTINCT fu.userId) FROM FeatureUsage fu WHERE fu.productCode = :productCode AND "
            + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)")
    long countUniqueUsersByProductCodeWithFilters(
            @Param("productCode") String productCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
            "SELECT COUNT(DISTINCT fu.featureCode) FROM FeatureUsage fu WHERE fu.productCode = :productCode AND fu.featureCode IS NOT NULL AND "
                    + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
                    + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
                    + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)")
    long countUniqueFeaturesByProductCodeWithFilters(
            @Param("productCode") String productCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    // Feature usage by product for feature stats
    @Query(
            "SELECT fu.productCode, COUNT(fu) FROM FeatureUsage fu WHERE fu.featureCode = :featureCode AND fu.productCode IS NOT NULL AND "
                    + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
                    + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
                    + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
                    + "GROUP BY fu.productCode ORDER BY COUNT(fu) DESC")
    List<Object[]> findFeatureUsageByProduct(
            @Param("featureCode") String featureCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    // Top users for feature
    @Query("SELECT fu.userId, COUNT(fu) FROM FeatureUsage fu WHERE fu.featureCode = :featureCode AND "
            + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
            + "GROUP BY fu.userId ORDER BY COUNT(fu) DESC")
    List<Object[]> findTopUsersByFeatureCode(
            @Param("featureCode") String featureCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    // Top features for product
    @Query(
            "SELECT fu.featureCode, COUNT(fu) FROM FeatureUsage fu WHERE fu.productCode = :productCode AND fu.featureCode IS NOT NULL AND "
                    + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
                    + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
                    + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
                    + "GROUP BY fu.featureCode ORDER BY COUNT(fu) DESC")
    List<Object[]> findTopFeaturesByProductCode(
            @Param("productCode") String productCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    // Top users for product
    @Query("SELECT fu.userId, COUNT(fu) FROM FeatureUsage fu WHERE fu.productCode = :productCode AND "
            + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
            + "GROUP BY fu.userId ORDER BY COUNT(fu) DESC")
    List<Object[]> findTopUsersByProductCode(
            @Param("productCode") String productCode,
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    // Missing methods that were accidentally removed

    // Top features
    @Query("SELECT fu.featureCode, COUNT(fu) FROM FeatureUsage fu WHERE fu.featureCode IS NOT NULL AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
            + "GROUP BY fu.featureCode ORDER BY COUNT(fu) DESC")
    List<Object[]> findTopFeatures(
            @Param("startDate") Instant startDate, @Param("endDate") Instant endDate, Pageable pageable);

    // Top users
    @Query("SELECT fu.userId, COUNT(fu) FROM FeatureUsage fu WHERE "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
            + "GROUP BY fu.userId ORDER BY COUNT(fu) DESC")
    List<Object[]> findTopUsers(
            @Param("startDate") Instant startDate, @Param("endDate") Instant endDate, Pageable pageable);

    // Top products
    @Query("SELECT fu.productCode, COUNT(fu) FROM FeatureUsage fu WHERE fu.productCode IS NOT NULL AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
            + "GROUP BY fu.productCode ORDER BY COUNT(fu) DESC")
    List<Object[]> findTopProducts(
            @Param("startDate") Instant startDate, @Param("endDate") Instant endDate, Pageable pageable);

    // Usage by action type
    @Query("SELECT fu.actionType, COUNT(fu) FROM FeatureUsage fu WHERE "
            + "(CAST(:actionType AS string) IS NULL OR fu.actionType = :actionType) AND "
            + "(CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate) AND "
            + "(CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate) "
            + "GROUP BY fu.actionType")
    List<Object[]> findUsageByActionType(
            @Param("actionType") ActionType actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);
}
