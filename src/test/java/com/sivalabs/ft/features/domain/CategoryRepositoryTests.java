package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.entities.Category;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CategoryRepositoryTests extends AbstractIT {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldCreateCategory() {
        Category category = new Category();
        category.setName("JUnit");
        category.setDescription("JUnit testing framework");
        category.setCreatedBy("test-user");
        category.setCreatedAt(Instant.now());

        Category saved = categoryRepository.save(category);
        assertThat(saved.getId()).isNotNull();

        Optional<Category> found = categoryRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("JUnit");
    }

    @Test
    void shouldCreateCategoryWithParent() {
        // Get existing category to use as parent
        Optional<Category> parentOpt = categoryRepository.findById(1L);
        assertThat(parentOpt).isPresent();
        Category parent = parentOpt.get();

        Category category = new Category();
        category.setName("Spring Data JPA");
        category.setDescription("Spring Data JPA module");
        category.setParentCategory(parent);
        category.setCreatedBy("test-user");
        category.setCreatedAt(Instant.now());

        Category saved = categoryRepository.save(category);
        assertThat(saved.getId()).isNotNull();

        Optional<Category> found = categoryRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Spring Data JPA");
        assertThat(found.get().getParentCategory()).isNotNull();
        assertThat(found.get().getParentCategory().getId()).isEqualTo(parent.getId());
    }
}
