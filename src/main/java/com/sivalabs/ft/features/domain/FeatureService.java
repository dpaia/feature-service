package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final FavoriteFeatureRepository favoriteFeatureRepository;
    private final EventPublisher eventPublisher;
    private final FeatureMapper featureMapper;
    private final PlanningHistoryService planningHistoryService;

    FeatureService(
            FavoriteFeatureService favoriteFeatureService,
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ProductRepository productRepository,
            FavoriteFeatureRepository favoriteFeatureRepository,
            EventPublisher eventPublisher,
            FeatureMapper featureMapper,
            PlanningHistoryService planningHistoryService) {
        this.favoriteFeatureService = favoriteFeatureService;
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.productRepository = productRepository;
        this.favoriteFeatureRepository = favoriteFeatureRepository;
        this.eventPublisher = eventPublisher;
        this.featureMapper = featureMapper;
        this.planningHistoryService = planningHistoryService;
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
        feature = featureRepository.save(feature);

        // Record creation in planning history
        planningHistoryService.recordChange(
                EntityType.FEATURE,
                feature.getCode(),
                feature.getId(),
                ChangeType.CREATED,
                null,
                null,
                null,
                null,
                cmd.createdBy());

        eventPublisher.publishFeatureCreatedEvent(feature);
        return code;
    }

    @Transactional
    public void updateFeature(UpdateFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();

        // Capture old values for change tracking
        String oldTitle = feature.getTitle();
        String oldDescription = feature.getDescription();
        FeatureStatus oldStatus = feature.getStatus();
        String oldAssignedTo = feature.getAssignedTo();
        String oldReleaseCode =
                feature.getRelease() != null ? feature.getRelease().getCode() : null;
        Instant oldPlannedCompletionAt = feature.getPlannedCompletionAt();
        Instant oldActualCompletionAt = feature.getActualCompletionAt();
        FeaturePlanningStatus oldPlanningStatus = feature.getFeaturePlanningStatus();
        String oldFeatureOwner = feature.getFeatureOwner();
        String oldBlockageReason = feature.getBlockageReason();

        // Update feature
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
        feature.setPlannedCompletionAt(cmd.plannedCompletionAt());
        feature.setActualCompletionAt(cmd.actualCompletionAt());
        feature.setFeaturePlanningStatus(cmd.featurePlanningStatus());
        feature.setFeatureOwner(cmd.featureOwner());
        feature.setBlockageReason(cmd.blockageReason());
        feature.setUpdatedBy(cmd.updatedBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);

        // Track changes in planning history
        trackFieldChange(EntityType.FEATURE, feature.getCode(), "title", oldTitle, cmd.title(), cmd.updatedBy());

        trackFieldChange(
                EntityType.FEATURE,
                feature.getCode(),
                "description",
                oldDescription,
                cmd.description(),
                cmd.updatedBy());

        // Status change
        if (oldStatus != cmd.status()) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    feature.getCode(),
                    ChangeType.STATUS_CHANGED,
                    "status",
                    oldStatus != null ? oldStatus.toString() : null,
                    cmd.status() != null ? cmd.status().toString() : null,
                    cmd.updatedBy());
        }

        // Assignment change
        if (!Objects.equals(oldAssignedTo, cmd.assignedTo())) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    feature.getCode(),
                    ChangeType.ASSIGNED,
                    "assignedTo",
                    oldAssignedTo,
                    cmd.assignedTo(),
                    cmd.updatedBy());
        }

        // Release change (move)
        if (!Objects.equals(oldReleaseCode, cmd.releaseCode())) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    feature.getCode(),
                    ChangeType.MOVED,
                    "release",
                    oldReleaseCode,
                    cmd.releaseCode(),
                    cmd.updatedBy());
        }

        // Other planning fields
        trackFieldChange(
                EntityType.FEATURE,
                feature.getCode(),
                "plannedCompletionAt",
                oldPlannedCompletionAt != null ? oldPlannedCompletionAt.toString() : null,
                cmd.plannedCompletionAt() != null ? cmd.plannedCompletionAt().toString() : null,
                cmd.updatedBy());

        trackFieldChange(
                EntityType.FEATURE,
                feature.getCode(),
                "actualCompletionAt",
                oldActualCompletionAt != null ? oldActualCompletionAt.toString() : null,
                cmd.actualCompletionAt() != null ? cmd.actualCompletionAt().toString() : null,
                cmd.updatedBy());

        if (oldPlanningStatus != cmd.featurePlanningStatus()) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    feature.getCode(),
                    ChangeType.UPDATED,
                    "featurePlanningStatus",
                    oldPlanningStatus != null ? oldPlanningStatus.toString() : null,
                    cmd.featurePlanningStatus() != null
                            ? cmd.featurePlanningStatus().toString()
                            : null,
                    cmd.updatedBy());
        }

        trackFieldChange(
                EntityType.FEATURE,
                feature.getCode(),
                "featureOwner",
                oldFeatureOwner,
                cmd.featureOwner(),
                cmd.updatedBy());

        trackFieldChange(
                EntityType.FEATURE,
                feature.getCode(),
                "blockageReason",
                oldBlockageReason,
                cmd.blockageReason(),
                cmd.updatedBy());

        eventPublisher.publishFeatureUpdatedEvent(feature);
    }

    @Transactional
    public void deleteFeature(DeleteFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();

        // Record deletion in planning history
        planningHistoryService.recordChange(
                EntityType.FEATURE, feature.getCode(), ChangeType.DELETED, null, null, null, cmd.deletedBy());

        favoriteFeatureRepository.deleteByFeatureCode(cmd.code());
        featureRepository.deleteByCode(cmd.code());
        eventPublisher.publishFeatureDeletedEvent(feature, cmd.deletedBy(), Instant.now());
    }

    private void trackFieldChange(
            EntityType entityType,
            String entityCode,
            String fieldName,
            String oldValue,
            String newValue,
            String changedBy) {
        if (!Objects.equals(oldValue, newValue)) {
            planningHistoryService.recordChange(
                    entityType, entityCode, ChangeType.UPDATED, fieldName, oldValue, newValue, changedBy);
        }
    }
}
