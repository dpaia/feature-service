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
        return categoryRepository.findAll().stream().map(categoryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CategoryDto> getCategoryById(Long id) {
        return categoryRepository.findById(id).map(categoryMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> searchCategories(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getAllCategories();
        }
        return categoryRepository.findByNameContainingIgnoreCase(name.trim()).stream()
                .map(categoryMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isCategoryExists(Long id) {
        return categoryRepository.existsById(id);
    }

    @Transactional
    public Long createCategory(CreateCategoryCommand cmd) {
        Category category = new Category();
        category.setName(cmd.name());
        category.setDescription(cmd.description());
        if (cmd.parentCategoryId() != null) {
            category.setParentCategory(categoryRepository
                    .findById(cmd.parentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with id: " + cmd.parentCategoryId())));
        }
        category.setCreatedBy(cmd.createdBy());
        category.setCreatedAt(Instant.now());
        return categoryRepository.save(category).getId();
    }

    @Transactional
    public void updateCategory(UpdateCategoryCommand cmd) {
        Category category = categoryRepository
                .findById(cmd.id())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + cmd.id()));
        category.setName(cmd.name());
        category.setDescription(cmd.description());
        if (cmd.parentCategoryId() == null) {
            category.setParentCategory(null);
        } else if (!cmd.parentCategoryId().equals(category.getId())) {
            category.setParentCategory(categoryRepository
                    .findById(cmd.parentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with id: " + cmd.parentCategoryId())));
        }
        categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(DeleteCategoryCommand cmd) {
        if (!categoryRepository.existsById(cmd.id())) {
            throw new ResourceNotFoundException("Category not found with id: " + cmd.id());
        }
        categoryRepository.clearParentCategoryReference(cmd.id());
        featureRepository.unlinkCategory(cmd.id());
        categoryRepository.deleteById(cmd.id());
    }
}
