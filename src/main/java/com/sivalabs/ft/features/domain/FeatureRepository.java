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

    @Query(
            value =
                    """
            WITH RECURSIVE release_hierarchy AS (
                SELECT r.id, r.parent_id
                FROM releases r
                WHERE r.code = :releaseCode
                UNION ALL
                SELECT parent.id, parent.parent_id
                FROM releases parent
                JOIN release_hierarchy child ON parent.id = child.parent_id
            )
            SELECT f.* FROM features f
            JOIN release_hierarchy rh ON f.release_id = rh.id
            """,
            nativeQuery = true)
    List<Feature> findByReleaseCodeWithParents(String releaseCode);

    @Query("select f from Feature f left join fetch f.release where f.product.code = :productCode")
    List<Feature> findByProductCode(String productCode);

    @Modifying
    void deleteByCode(String code);

    @Modifying
    @Query("update Feature f set f.release = null where f.release.code = :code")
    void unsetRelease(String code);

    boolean existsByCode(String code);

    @Query(value = "select nextval('feature_code_seq')", nativeQuery = true)
    long getNextFeatureId();
}
