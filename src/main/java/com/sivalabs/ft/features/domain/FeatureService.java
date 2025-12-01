package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.AssignFeatureToReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.MoveFeatureToReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.RemoveFeatureFromReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeaturePlanningCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureService {
    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);
    public static final String FEATURE_SEPARATOR = "-";
    private final FavoriteFeatureService favoriteFeatureService;
    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ProductRepository productRepository;
    private final FavoriteFeatureRepository favoriteFeatureRepository;
    private final EventPublisher eventPublisher;
    private final FeatureMapper featureMapper;

    FeatureService(
            FavoriteFeatureService favoriteFeatureService,
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ProductRepository productRepository,
            FavoriteFeatureRepository favoriteFeatureRepository,
            EventPublisher eventPublisher,
            FeatureMapper featureMapper) {
        this.favoriteFeatureService = favoriteFeatureService;
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.favoriteFeatureRepository = favoriteFeatureRepository;
        this.featureMapper = featureMapper;
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

    // Feature Planning Methods

    /**
     * Validates if a status transition is allowed according to the state machine rules.
     * Allowed transitions:
     * - NOT_STARTED -> IN_PROGRESS, BLOCKED
     * - IN_PROGRESS -> DONE, BLOCKED, NOT_STARTED
     * - BLOCKED -> IN_PROGRESS, NOT_STARTED
     * - DONE -> NOT_STARTED, IN_PROGRESS
     */
    private void validatePlanningStatusTransition(FeaturePlanningStatus fromStatus, FeaturePlanningStatus toStatus) {
        if (fromStatus == toStatus) {
            return; // Same status is always valid
        }

        Set<FeaturePlanningStatus> allowedTransitions =
                switch (fromStatus) {
                    case NOT_STARTED -> Set.of(FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.BLOCKED);
                    case IN_PROGRESS ->
                        Set.of(
                                FeaturePlanningStatus.DONE,
                                FeaturePlanningStatus.BLOCKED,
                                FeaturePlanningStatus.NOT_STARTED);
                    case BLOCKED -> Set.of(FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.NOT_STARTED);
                    case DONE -> Set.of(FeaturePlanningStatus.NOT_STARTED, FeaturePlanningStatus.IN_PROGRESS);
                };

        if (!allowedTransitions.contains(toStatus)) {
            throw new BadRequestException("Invalid planning status transition from " + fromStatus + " to " + toStatus);
        }
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findFeaturesWithPlanningByReleaseCode(String releaseCode) {
        return findFeaturesWithPlanningByReleaseCode(releaseCode, null, null, false, false);
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findFeaturesWithPlanningByReleaseCode(
            String releaseCode, FeaturePlanningStatus status, String owner, Boolean overdue, Boolean blocked) {
        var features = featureRepository.findByReleaseCode(releaseCode).stream()
                .filter(feature -> {
                    // Filter by planning status
                    if (status != null && !status.equals(feature.getPlanningStatus())) {
                        return false;
                    }

                    // Filter by owner
                    if (owner != null && !owner.trim().isEmpty()) {
                        String featureOwner = feature.getFeatureOwner();
                        if (featureOwner == null || !featureOwner.toLowerCase().contains(owner.toLowerCase())) {
                            return false;
                        }
                    }

                    // Filter by overdue status
                    if (overdue != null && overdue) {
                        var plannedDate = feature.getPlannedCompletionDate();
                        if (plannedDate == null || !plannedDate.isBefore(LocalDate.now())) {
                            return false;
                        }
                    }

                    // Filter by blocked status
                    if (blocked != null && blocked) {
                        String blockageReason = feature.getBlockageReason();
                        if (blockageReason == null || blockageReason.trim().isEmpty()) {
                            return false;
                        }
                    }

                    return true;
                })
                .toList();

        return features.stream().map(featureMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public FeatureDto findFeatureWithPlanning(String featureCode) {
        Feature feature = featureRepository
                .findByCode(featureCode)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + featureCode));
        return featureMapper.toDto(feature);
    }

    @Transactional
    public void assignFeatureToRelease(AssignFeatureToReleaseCommand cmd) {
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));
        Release release = releaseRepository
                .findByCode(cmd.releaseCode())
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + cmd.releaseCode()));

        // Check if feature is already assigned to any release (including same release)
        if (feature.getRelease() != null) {
            if (feature.getRelease().getCode().equals(cmd.releaseCode())) {
                throw new BadRequestException(
                        "Feature " + cmd.featureCode() + " is already assigned to release " + cmd.releaseCode());
            } else {
                throw new BadRequestException("Feature " + cmd.featureCode() + " is already assigned to release "
                        + feature.getRelease().getCode() + ". Use move operation to reassign.");
            }
        }

        // Assign feature to release and set planning details
        feature.setRelease(release);
        feature.setPlannedCompletionDate(cmd.plannedCompletionDate());
        feature.setFeatureOwner(cmd.featureOwner());
        feature.setNotes(cmd.notes());
        feature.setPlanningStatus(FeaturePlanningStatus.NOT_STARTED);
        feature.setUpdatedBy(cmd.assignedBy());
        feature.setUpdatedAt(Instant.now());

        featureRepository.save(feature);

        log.info(
                "Feature {} assigned to release {} by user {}", cmd.featureCode(), cmd.releaseCode(), cmd.assignedBy());
    }

    @Transactional
    public void updateFeaturePlanning(UpdateFeaturePlanningCommand cmd) {
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));

        // Validate planning status transition
        if (cmd.planningStatus() != null) {
            FeaturePlanningStatus currentStatus = feature.getPlanningStatus() != null
                    ? feature.getPlanningStatus()
                    : FeaturePlanningStatus.NOT_STARTED;
            validatePlanningStatusTransition(currentStatus, cmd.planningStatus());
            feature.setPlanningStatus(cmd.planningStatus());

            // Clear blockage reason when moving away from BLOCKED status
            if (currentStatus == FeaturePlanningStatus.BLOCKED
                    && cmd.planningStatus() != FeaturePlanningStatus.BLOCKED) {
                feature.setBlockageReason(null);
            }
        }

        if (cmd.plannedCompletionDate() != null) {
            feature.setPlannedCompletionDate(cmd.plannedCompletionDate());
        }
        if (cmd.featureOwner() != null) {
            feature.setFeatureOwner(cmd.featureOwner());
        }
        if (cmd.notes() != null) {
            feature.setNotes(cmd.notes());
        }
        if (cmd.blockageReason() != null) {
            feature.setBlockageReason(cmd.blockageReason());
        }

        feature.setUpdatedBy(cmd.updatedBy());
        feature.setUpdatedAt(Instant.now());

        featureRepository.save(feature);

        log.info("Feature planning updated for feature {} by user {}", cmd.featureCode(), cmd.updatedBy());
    }

    @Transactional
    public void moveFeatureToRelease(MoveFeatureToReleaseCommand cmd) {
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));

        Release targetRelease = releaseRepository
                .findByCode(cmd.targetReleaseCode())
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + cmd.targetReleaseCode()));

        String sourceReleaseCode =
                feature.getRelease() != null ? feature.getRelease().getCode() : "unassigned";

        // Move feature to target release and reset planning status
        feature.setRelease(targetRelease);
        feature.setPlanningStatus(FeaturePlanningStatus.NOT_STARTED);
        feature.setNotes("Moved from " + sourceReleaseCode + ": " + cmd.rationale());
        feature.setPlannedCompletionDate(null);
        feature.setBlockageReason(null);
        feature.setUpdatedBy(cmd.movedBy());
        feature.setUpdatedAt(Instant.now());

        featureRepository.save(feature);

        log.info(
                "Feature {} moved from {} to release {} by user {} with rationale: {}",
                cmd.featureCode(),
                sourceReleaseCode,
                cmd.targetReleaseCode(),
                cmd.movedBy(),
                cmd.rationale());
    }

    @Transactional
    public void removeFeatureFromRelease(RemoveFeatureFromReleaseCommand cmd) {
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));

        String releaseCode = feature.getRelease() != null ? feature.getRelease().getCode() : "no release";

        // Remove feature from release and clear planning details
        feature.setRelease(null);
        feature.setPlanningStatus(null);
        feature.setPlannedCompletionDate(null);
        feature.setFeatureOwner(null);
        feature.setNotes(null);
        feature.setBlockageReason(null);
        feature.setUpdatedBy(cmd.removedBy());
        feature.setUpdatedAt(Instant.now());

        featureRepository.save(feature);

        log.info(
                "Feature {} removed from release {} by user {} with rationale: {}",
                cmd.featureCode(),
                releaseCode,
                cmd.removedBy(),
                cmd.rationale());
    }
}
