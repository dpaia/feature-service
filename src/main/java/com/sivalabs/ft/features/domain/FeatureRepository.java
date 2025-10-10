package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Feature;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

interface FeatureRepository extends ListCrudRepository<Feature, Long> {
    @Query("select f from Feature f left join fetch f.release where f.code = :code")
    Optional<Feature> findByCode(String code);

    @Query("select f from Feature f left join fetch f.release where f.release.code = :releaseCode")
    List<Feature> findByReleaseCode(String releaseCode);

    @Query("select f from Feature f left join fetch f.release where f.product.code = :productCode")
    List<Feature> findByProductCode(String productCode);

    @Modifying
    void deleteByCode(String code);

    @Modifying
    @Query("delete from Feature f where f.release.code = :code")
    void deleteByReleaseCode(String code);

    boolean existsByCode(String code);

    @Query(value = "select nextval('feature_code_seq')", nativeQuery = true)
    long getNextFeatureId();

    @Query(
            value =
                    """
            WITH RECURSIVE release_hierarchy AS (
                SELECT id, code, parent_id
                FROM releases
                WHERE code = :releaseCode
                UNION ALL
                SELECT r.id, r.code, r.parent_id
                FROM releases r
                INNER JOIN release_hierarchy rh ON r.id = rh.parent_id
            )
            SELECT f.*
            FROM features f
            INNER JOIN release_hierarchy rh ON f.release_id = rh.id
            ORDER BY f.id
            """,
            nativeQuery = true)
    List<Feature> findByReleaseCodeWithParents(String releaseCode);
}
