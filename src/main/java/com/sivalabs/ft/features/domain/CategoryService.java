package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateCategoryCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteCategoryCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateCategoryCommand;
import com.sivalabs.ft.features.domain.dtos.CategoryDto;
import com.sivalabs.ft.features.domain.entities.Category;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.CategoryMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final FeatureRepository featureRepository;

    public CategoryService(
            CategoryRepository categoryRepository, CategoryMapper categoryMapper, FeatureRepository featureRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.featureRepository = featureRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        return categories.stream().map(categoryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CategoryDto> getCategoryById(Long id) {
        return categoryRepository.findById(id).map(categoryMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<CategoryDto> getCategoryByName(String name) {
        return categoryRepository.findByName(name).map(categoryMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isCategoryExists(Long id) {
        return categoryRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public boolean isCategoryExistsByName(String name) {
        return categoryRepository.existsByName(name);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> searchCategories(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getAllCategories();
        }
        List<Category> categories = categoryRepository.findByNameContainingIgnoreCase(name.trim());
        return categories.stream().map(categoryMapper::toDto).toList();
    }

    @Transactional
    public Long createCategory(CreateCategoryCommand cmd) {
        Category category = new Category();
        category.setName(cmd.name());
        category.setDescription(cmd.description());
        if (cmd.parentCategoryId() != null) {
            Category parentCategory = categoryRepository
                    .findById(cmd.parentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with id: " + cmd.parentCategoryId()));
            category.setParentCategory(parentCategory);
        }
        category.setCreatedBy(cmd.createdBy());
        category.setCreatedAt(Instant.now());
        category = categoryRepository.save(category);
        return category.getId();
    }

    @Transactional
    public void updateCategory(UpdateCategoryCommand cmd) {
        Category category = categoryRepository
                .findById(cmd.id())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + cmd.id()));
        category.setName(cmd.name());
        category.setDescription(cmd.description());

        if (cmd.parentCategoryId() != null) {
            if (!cmd.parentCategoryId().equals(category.getId())) { // Prevent self-reference
                Category parentCategory = categoryRepository
                        .findById(cmd.parentCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Parent category not found with id: " + cmd.parentCategoryId()));
                category.setParentCategory(parentCategory);
            }
        } else {
            category.setParentCategory(null);
        }

        categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(DeleteCategoryCommand cmd) {
        if (!categoryRepository.existsById(cmd.id())) {
            throw new ResourceNotFoundException("Category not found with id: " + cmd.id());
        }
        // First, set parent_category_id to null for all child categories
        categoryRepository.clearParentCategoryReference(cmd.id());
        featureRepository.unlinkCategory(cmd.id());
        categoryRepository.deleteById(cmd.id());
    }
}
