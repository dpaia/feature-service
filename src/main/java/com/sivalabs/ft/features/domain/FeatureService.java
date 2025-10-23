package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.AssignTagsToFeaturesCommand;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.RemoveCategoryFromFeaturesCommand;
import com.sivalabs.ft.features.domain.Commands.RemoveTagsFromFeaturesCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.*;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureService {
    public static final String FEATURE_SEPARATOR = "-";
    private final FavoriteFeatureService favoriteFeatureService;
    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ProductRepository productRepository;
    private final TagRepository tagRepository;
    private final FavoriteFeatureRepository favoriteFeatureRepository;
    private final EventPublisher eventPublisher;
    private final FeatureMapper featureMapper;
    private final CategoryRepository categoryRepository;

    FeatureService(
            FavoriteFeatureService favoriteFeatureService,
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ProductRepository productRepository,
            TagRepository tagRepository,
            FavoriteFeatureRepository favoriteFeatureRepository,
            EventPublisher eventPublisher,
            FeatureMapper featureMapper,
            CategoryRepository categoryRepository) {
        this.favoriteFeatureService = favoriteFeatureService;
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.productRepository = productRepository;
        this.tagRepository = tagRepository;
        this.eventPublisher = eventPublisher;
        this.favoriteFeatureRepository = favoriteFeatureRepository;
        this.featureMapper = featureMapper;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<FeatureDto> findFeatureByCode(String username, String code) {
        Optional<Feature> optionalFeature = featureRepository.findByCode(code);
        if (optionalFeature.isEmpty()) {
            return Optional.empty();
        }
        List<FeatureDto> featureDtos = updateFavoriteStatus(List.of(optionalFeature.get()), username);
        return Optional.ofNullable(featureDtos.getFirst());
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findFeaturesByRelease(String username, String releaseCode) {
        List<Feature> features = featureRepository.findByReleaseCode(releaseCode);
        return updateFavoriteStatus(features, username);
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findFeaturesByProduct(String username, String productCode) {
        List<Feature> features = featureRepository.findByProductCode(productCode);
        return updateFavoriteStatus(features, username);
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findFeaturesByTags(String username, List<Long> tagIds) {
        List<Feature> features = featureRepository.findByTagIds(tagIds);
        return updateFavoriteStatus(features, username);
    }

    private List<FeatureDto> updateFavoriteStatus(List<Feature> features, String username) {
        if (username == null || features.isEmpty()) {
            return features.stream().map(featureMapper::toDto).toList();
        }
        Set<String> featureCodes = features.stream().map(Feature::getCode).collect(Collectors.toSet());
        Map<String, Boolean> favoriteFeatures = favoriteFeatureService.getFavoriteFeatures(username, featureCodes);
        return features.stream()
                .map(feature -> {
                    var dto = featureMapper.toDto(feature);
                    dto.makeFavorite(favoriteFeatures.get(feature.getCode()));
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isFeatureExists(String code) {
        return featureRepository.existsByCode(code);
    }

    @Transactional
    public String createFeature(CreateFeatureCommand cmd) {
        Product product = productRepository.findByCode(cmd.productCode()).orElseThrow();
        Release release = releaseRepository.findByCode(cmd.releaseCode()).orElse(null);
        String code = product.getPrefix() + FEATURE_SEPARATOR + featureRepository.getNextFeatureId();
        var feature = new Feature();
        feature.setProduct(product);
        feature.setRelease(release);
        feature.setCode(code);
        feature.setTitle(cmd.title());
        feature.setDescription(cmd.description());
        feature.setStatus(FeatureStatus.NEW);
        feature.setAssignedTo(cmd.assignedTo());
        feature.setCreatedBy(cmd.createdBy());
        feature.setCreatedAt(Instant.now());
        featureRepository.save(feature);
        eventPublisher.publishFeatureCreatedEvent(feature);
        return code;
    }

    @Transactional
    public void updateFeature(UpdateFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();
        feature.setTitle(cmd.title());
        feature.setDescription(cmd.description());
        if (cmd.releaseCode() != null) {
            Release release = releaseRepository.findByCode(cmd.releaseCode()).orElse(null);
            feature.setRelease(release);
        } else {
            feature.setRelease(null);
        }
        feature.setAssignedTo(cmd.assignedTo());
        feature.setStatus(cmd.status());
        feature.setUpdatedBy(cmd.updatedBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);
        eventPublisher.publishFeatureUpdatedEvent(feature);
    }

    @Transactional
    public void deleteFeature(DeleteFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();
        favoriteFeatureRepository.deleteByFeatureCode(cmd.code());
        featureRepository.deleteByCode(cmd.code());
        eventPublisher.publishFeatureDeletedEvent(feature, cmd.deletedBy(), Instant.now());
    }

    @Transactional
    public void assignTagsToFeatures(AssignTagsToFeaturesCommand cmd) {
        List<Feature> features = this.getFeatures(cmd.featureCodes());
        List<Tag> tags = this.getTags(cmd.tagIds());

        // Assign tags to each feature
        for (Feature feature : features) {
            feature.getTags().addAll(tags);
            feature.setUpdatedBy(cmd.updatedBy());
            feature.setUpdatedAt(Instant.now());
            featureRepository.save(feature);
            eventPublisher.publishFeatureUpdatedEvent(feature);
        }
    }

    @Transactional
    public void removeTagsFromFeatures(RemoveTagsFromFeaturesCommand cmd) {
        List<Feature> features = this.getFeatures(cmd.featureCodes());
        List<Tag> tags = this.getTags(cmd.tagIds());

        // Remove tags from each feature
        for (Feature feature : features) {
            tags.forEach(feature.getTags()::remove);
            feature.setUpdatedBy(cmd.updatedBy());
            feature.setUpdatedAt(Instant.now());
            featureRepository.save(feature);
            eventPublisher.publishFeatureUpdatedEvent(feature);
        }
    }

    @Transactional
    public void assignCategoryToFeatures(Commands.AssignCategoryToFeaturesCommand cmd) {
        // Get the category
        Category category = categoryRepository
                .findById(cmd.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + cmd.categoryId()));

        // Get the features
        List<Feature> features = getFeatures(cmd.featureCodes());

        // Assign category to each feature
        for (Feature feature : features) {
            feature.setCategory(category);
            feature.setUpdatedBy(cmd.updatedBy());
            feature.setUpdatedAt(Instant.now());
            featureRepository.save(feature);
            eventPublisher.publishFeatureUpdatedEvent(feature);
        }
    }

    @Transactional
    public void removeCategoryFromFeatures(RemoveCategoryFromFeaturesCommand cmd) {
        // Get the features
        List<Feature> features = getFeatures(cmd.featureCodes());

        // Remove category from each feature
        for (Feature feature : features) {
            feature.setCategory(null);
            feature.setUpdatedBy(cmd.updatedBy());
            feature.setUpdatedAt(Instant.now());
            featureRepository.save(feature);
            eventPublisher.publishFeatureUpdatedEvent(feature);
        }
    }

    private List<Feature> getFeatures(List<String> featureCodes) {
        List<Feature> features = new ArrayList<>();

        // Find all features and validate they exist
        for (String featureCode : featureCodes) {
            Feature feature = featureRepository
                    .findByCode(featureCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found with code: " + featureCode));
            features.add(feature);
        }
        return features;
    }

    private List<Tag> getTags(List<Long> tagIds) {
        List<Tag> tags = new ArrayList<>();
        for (Long tagId : tagIds) {
            var tag = tagRepository
                    .findById(tagId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + tagId));
            tags.add(tag);
        }
        return tags;
    }
}
