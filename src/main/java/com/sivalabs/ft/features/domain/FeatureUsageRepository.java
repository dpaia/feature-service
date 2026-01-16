package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureUsageRepository
        extends JpaRepository<FeatureUsage, Long>, JpaSpecificationExecutor<FeatureUsage> {

    // Deduplication support - can be used as idempotency key
    boolean existsByEventHash(String eventHash);

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

    // ========== NEW METHODS FOR TASK 3.1: USAGE TRENDS ANALYTICS ==========

    /**
     * Find usage trends grouped by time periods using DATE_TRUNC.
     * Returns period, usage count, and unique user count for trend analysis.
     *
     * @param periodType PostgreSQL period type: 'day', 'week', 'month'
     * @param featureCode optional feature filter
     * @param productCode optional product filter
     * @param actionType optional action type filter
     * @param startDate optional start date filter
     * @param endDate optional end date filter
     * @return List of Object[] containing [period_timestamp, usage_count, unique_user_count]
     */
    @Query(
            value =
                    """
        SELECT
            DATE_TRUNC(CAST(:periodType AS TEXT), fu.timestamp) as period,
            COUNT(fu.id) as usageCount,
            COUNT(DISTINCT fu.user_id) as uniqueUserCount
        FROM feature_usage fu
        WHERE (CAST(:featureCode AS VARCHAR) IS NULL OR fu.feature_code = CAST(:featureCode AS VARCHAR))
        AND (CAST(:productCode AS VARCHAR) IS NULL OR fu.product_code = CAST(:productCode AS VARCHAR))
        AND (CAST(:actionType AS VARCHAR) IS NULL OR fu.action_type = CAST(:actionType AS VARCHAR))
        AND (CAST(:startDate AS TIMESTAMP) IS NULL OR fu.timestamp >= CAST(:startDate AS TIMESTAMP))
        AND (CAST(:endDate AS TIMESTAMP) IS NULL OR fu.timestamp <= CAST(:endDate AS TIMESTAMP))
        GROUP BY 1
        ORDER BY 1 DESC
        """,
            nativeQuery = true)
    List<Object[]> findUsageTrends(
            @Param("periodType") String periodType,
            @Param("featureCode") String featureCode,
            @Param("productCode") String productCode,
            @Param("actionType") String actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Find overall usage trends without feature/product filtering.
     * Optimized query for overall system trends.
     */
    @Query(
            value =
                    """
        SELECT
            DATE_TRUNC(CAST(:periodType AS TEXT), fu.timestamp) as period,
            COUNT(fu.id) as usageCount,
            COUNT(DISTINCT fu.user_id) as uniqueUserCount
        FROM feature_usage fu
        WHERE (CAST(:actionType AS VARCHAR) IS NULL OR fu.action_type = CAST(:actionType AS VARCHAR))
        AND (CAST(:startDate AS TIMESTAMP) IS NULL OR fu.timestamp >= CAST(:startDate AS TIMESTAMP))
        AND (CAST(:endDate AS TIMESTAMP) IS NULL OR fu.timestamp <= CAST(:endDate AS TIMESTAMP))
        GROUP BY 1
        ORDER BY 1 DESC
        """,
            nativeQuery = true)
    List<Object[]> findOverallUsageTrends(
            @Param("periodType") String periodType,
            @Param("actionType") String actionType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    // ========== NEW METHODS FOR TASK 3.2: ADOPTION RATE ANALYTICS ==========

    /**
     * Count unique users who adopted a feature after its release date.
     * Used for calculating adoption rate metrics.
     *
     * @param featureCode Feature code to analyze
     * @param releaseDate Release date to measure adoption from
     * @param endDate Optional end date for the adoption window
     * @return Number of unique users who used the feature after release
     */
    @Query(
            """
            SELECT COUNT(DISTINCT fu.userId) FROM FeatureUsage fu
            WHERE fu.featureCode = :featureCode
            AND fu.timestamp >= :releaseDate
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countUniqueUsersAfterRelease(
            @Param("featureCode") String featureCode,
            @Param("releaseDate") Instant releaseDate,
            @Param("endDate") Instant endDate);

    /**
     * Count total usage events for a feature after its release date.
     *
     * @param featureCode Feature code to analyze
     * @param releaseDate Release date to measure adoption from
     * @param endDate Optional end date for the adoption window
     * @return Total usage count after release
     */
    @Query(
            """
            SELECT COUNT(fu) FROM FeatureUsage fu
            WHERE fu.featureCode = :featureCode
            AND fu.timestamp >= :releaseDate
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            """)
    long countUsageAfterRelease(
            @Param("featureCode") String featureCode,
            @Param("releaseDate") Instant releaseDate,
            @Param("endDate") Instant endDate);

    /**
     * Find all feature usage events for features in a specific release.
     * Used for release statistics and aggregated adoption metrics.
     *
     * @param releaseCode Release code to filter by
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return List of feature usage events for the release
     */
    @Query(
            """
            SELECT fu FROM FeatureUsage fu
            JOIN Feature f ON fu.featureCode = f.code
            WHERE f.release.code = :releaseCode
            AND (CAST(:startDate AS timestamp) IS NULL OR fu.timestamp >= :startDate)
            AND (CAST(:endDate AS timestamp) IS NULL OR fu.timestamp <= :endDate)
            ORDER BY fu.timestamp DESC
            """)
    List<FeatureUsage> findByReleaseCode(
            @Param("releaseCode") String releaseCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Get aggregated usage statistics grouped by feature code for a release.
     * Returns [featureCode, uniqueUsers, totalUsage] for each feature.
     *
     * @param releaseCode Release code to analyze
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return List of Object[] with [featureCode, uniqueUserCount, totalUsageCount]
     */
    @Query(
            value =
                    """
            SELECT
                fu.feature_code as featureCode,
                COUNT(DISTINCT fu.user_id) as uniqueUsers,
                COUNT(fu.id) as totalUsage
            FROM feature_usage fu
            INNER JOIN features f ON fu.feature_code = f.code
            INNER JOIN releases r ON f.release_id = r.id
            WHERE r.code = CAST(:releaseCode AS VARCHAR)
            AND (CAST(:startDate AS TIMESTAMP) IS NULL OR fu.timestamp >= CAST(:startDate AS TIMESTAMP))
            AND (CAST(:endDate AS TIMESTAMP) IS NULL OR fu.timestamp <= CAST(:endDate AS TIMESTAMP))
            GROUP BY fu.feature_code
            ORDER BY totalUsage DESC
            """,
            nativeQuery = true)
    List<Object[]> findAggregatedStatsByReleaseCode(
            @Param("releaseCode") String releaseCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    // ========== NEW METHODS FOR ADMIN MONITORING ==========

    /**
     * Count total events within a date range for health metrics calculation
     */
    Long countByTimestampBetween(Instant start, Instant end);

    /**
     * Find the most recent event for last event timestamp in health dashboard
     */
    Optional<FeatureUsage> findFirstByOrderByTimestampDesc();
}
