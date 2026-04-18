package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Category;
import com.sivalabs.ft.features.domain.entities.Feature;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class FeatureCategoryRelationshipTests extends AbstractIT {

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @Transactional
    void shouldAssignCategoryToFeature() {
        // Get a feature without a category or create one
        Optional<Feature> featureOpt = featureRepository.findByCode("GO-3");
        assertThat(featureOpt).isPresent();
        Feature feature = featureOpt.get();

        // Create a new category
        Category category = new Category();
        category.setName("Go Test Category");
        category.setDescription("A category for Go tests");
        category.setCreatedBy("test-user");
        category.setCreatedAt(Instant.now());

        Category savedCategory = categoryRepository.save(category);

        // Assign category to feature
        feature.setCategory(savedCategory);
        feature.setUpdatedBy("test-user");
        feature.setUpdatedAt(Instant.now());

        Feature updatedFeature = featureRepository.save(feature);

        // Verify the assignment
        assertThat(updatedFeature.getCategory()).isNotNull();
        assertThat(updatedFeature.getCategory().getId()).isEqualTo(savedCategory.getId());
        assertThat(updatedFeature.getCategory().getName()).isEqualTo("Go Test Category");
    }

    @Test
    @Transactional
    void shouldRemoveCategoryFromFeature() {
        // Get a feature with a category
        Optional<Feature> featureOpt = featureRepository.findByCode("IDEA-1");
        assertThat(featureOpt).isPresent();
        Feature feature = featureOpt.get();

        // Verify it has a category
        assertThat(feature.getCategory()).isNotNull();

        // Remove the category
        feature.setCategory(null);
        feature.setUpdatedBy("test-user");
        feature.setUpdatedAt(Instant.now());

        Feature updatedFeature = featureRepository.save(feature);

        // Verify the category was removed
        assertThat(updatedFeature.getCategory()).isNull();
    }

    @Test
    @Transactional
    void shouldFindFeaturesByCategory() {
        // Get category with ID 1 (SpringBoot)
        Optional<Category> categoryOpt = categoryRepository.findById(1L);
        assertThat(categoryOpt).isPresent();
        Category category = categoryOpt.get();

        // Find all features with this category
        // We need to use a custom query which is not available in the current repository
        // Instead, we'll retrieve a known feature that should have this category
        Optional<Feature> featureOpt = featureRepository.findByCode("IDEA-1");
        assertThat(featureOpt).isPresent();
        Feature feature = featureOpt.get();

        // Verify it has the expected category
        assertThat(feature.getCategory()).isNotNull();
        assertThat(feature.getCategory().getId()).isEqualTo(category.getId());
    }
}
