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

    @Query("select distinct f from Feature f left join fetch f.release left join f.tags t where t.id in :tagIds")
    List<Feature> findByTagIds(List<Long> tagIds);

    @Query(
            "select distinct f from Feature f left join fetch f.release left join fetch f.category c where c.id in :categoryIds")
    List<Feature> findByCategoryIds(List<Long> categoryIds);

    @Query(
            """
            select distinct f from Feature f left join fetch f.release
                left join f.tags t left join fetch f.category c
            where t.id in :tagIds or c.id in :categoryIds
            """)
    List<Feature> findByCategoryIdsOrTagIds(List<Long> categoryIds, List<Long> tagIds);

    @Modifying
    void deleteByCode(String code);

    @Modifying
    @Query("update Feature f set f.release = null where f.release.code = :code")
    void unsetRelease(String code);

    @Modifying
    @Query("update Feature f set f.category = null where f.category.id = :id")
    void unlinkCategory(Long id);

    boolean existsByCode(String code);

    @Query(value = "select nextval('feature_code_seq')", nativeQuery = true)
    long getNextFeatureId();
}
