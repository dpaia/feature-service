package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.List;

public class Commands {
    private Commands() {}

    /* Product Commands */
    public record CreateProductCommand(
            String code, String prefix, String name, String description, String imageUrl, String createdBy) {}

    public record UpdateProductCommand(
            String code, String prefix, String name, String description, String imageUrl, String updatedBy) {}

    /* Release Commands */
    public record CreateReleaseCommand(String productCode, String code, String description, String createdBy) {}

    public record UpdateReleaseCommand(
            String code, String description, ReleaseStatus status, Instant releasedAt, String updatedBy) {}

    /* Feature Commands */
    public record CreateFeatureCommand(
            String productCode,
            String releaseCode,
            String title,
            String description,
            String assignedTo,
            String createdBy) {}

    public record UpdateFeatureCommand(
            String code,
            String title,
            String description,
            FeatureStatus status,
            String releaseCode,
            String assignedTo,
            String updatedBy) {}

    public record DeleteFeatureCommand(String code, String deletedBy) {}

    /* Comment Commands */
    public record CreateCommentCommand(String featureCode, String content, String createdBy) {}

    /* Tag Commands */
    public record CreateTagCommand(String name, String description, String createdBy) {}

    public record UpdateTagCommand(Long id, String name, String description, String updatedBy) {}

    public record DeleteTagCommand(Long id, String deletedBy) {}

    /* Feature Tag Commands */
    public record AssignTagsToFeaturesCommand(List<String> featureCodes, List<Long> tagIds, String updatedBy) {}

    public record RemoveTagsFromFeaturesCommand(List<String> featureCodes, List<Long> tagIds, String updatedBy) {}

    /* Category Commands */
    public record CreateCategoryCommand(String name, String description, Long parentCategoryId, String createdBy) {}

    public record UpdateCategoryCommand(
            Long id, String name, String description, Long parentCategoryId, String updatedBy) {}

    public record DeleteCategoryCommand(Long id, String deletedBy) {}

    public record AssignCategoryToFeaturesCommand(List<String> featureCodes, Long categoryId, String updatedBy) {}

    public record RemoveCategoryFromFeaturesCommand(List<String> featureCodes, String updatedBy) {}
}
