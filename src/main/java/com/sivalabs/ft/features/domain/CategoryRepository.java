package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    List<Category> findByNameContainingIgnoreCase(String name);

    @Modifying
    @Query("UPDATE Category c SET c.parentCategory = null WHERE c.parentCategory.id = :categoryId")
    void clearParentCategoryReference(@Param("categoryId") Long categoryId);
}
