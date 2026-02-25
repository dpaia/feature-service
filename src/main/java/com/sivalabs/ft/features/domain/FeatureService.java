package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.AssignFeatureToReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.MoveFeatureCommand;
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
import java.util.EnumSet;
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

    private static final Map<FeaturePlanningStatus, Set<FeaturePlanningStatus>> ALLOWED_TRANSITIONS = Map.of(
            FeaturePlanningStatus.NOT_STARTED,
            EnumSet.of(FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.BLOCKED),
            FeaturePlanningStatus.IN_PROGRESS,
            EnumSet.of(FeaturePlanningStatus.DONE, FeaturePlanningStatus.BLOCKED, FeaturePlanningStatus.NOT_STARTED),
            FeaturePlanningStatus.BLOCKED,
            EnumSet.of(FeaturePlanningStatus.IN_PROGRESS, FeaturePlanningStatus.NOT_STARTED),
            FeaturePlanningStatus.DONE,
            EnumSet.of(FeaturePlanningStatus.NOT_STARTED, FeaturePlanningStatus.IN_PROGRESS));

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

    @Transactional(readOnly = true)
    public List<FeatureDto> findReleaseFeaturesWithFilters(
            String username,
            String releaseCode,
            FeaturePlanningStatus planningStatus,
            String owner,
            boolean overdue,
            boolean blocked) {
        List<Feature> features = featureRepository.findByReleaseCode(releaseCode);
        List<Feature> filtered = features.stream()
                .filter(f -> planningStatus == null || planningStatus == f.getPlanningStatus())
                .filter(f -> owner == null || owner.equals(f.getFeatureOwner()))
                .filter(f -> !overdue
                        || (f.getPlannedCompletionDate() != null
                                && f.getPlannedCompletionDate().isBefore(LocalDate.now())
                                && f.getPlanningStatus() != FeaturePlanningStatus.DONE))
                .filter(f -> !blocked || f.getPlanningStatus() == FeaturePlanningStatus.BLOCKED)
                .toList();
        return updateFavoriteStatus(filtered, username);
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
    public void assignFeatureToRelease(AssignFeatureToReleaseCommand cmd) {
        Release release = releaseRepository
                .findByCode(cmd.releaseCode())
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + cmd.releaseCode()));
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));
        feature.setRelease(release);
        feature.setPlannedCompletionDate(cmd.plannedCompletionDate());
        feature.setFeatureOwner(cmd.featureOwner());
        feature.setNotes(cmd.notes());
        feature.setUpdatedBy(cmd.createdBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);
        log.info("User {} assigned feature {} to release {}", cmd.createdBy(), cmd.featureCode(), cmd.releaseCode());
    }

    @Transactional
    public void updateFeaturePlanning(UpdateFeaturePlanningCommand cmd) {
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));
        if (feature.getRelease() == null || !feature.getRelease().getCode().equals(cmd.releaseCode())) {
            throw new BadRequestException(
                    "Feature " + cmd.featureCode() + " is not assigned to release " + cmd.releaseCode());
        }
        if (cmd.planningStatus() != null) {
            validatePlanningStatusTransition(feature.getPlanningStatus(), cmd.planningStatus());
            feature.setPlanningStatus(cmd.planningStatus());
        }
        feature.setPlannedCompletionDate(cmd.plannedCompletionDate());
        feature.setFeatureOwner(cmd.featureOwner());
        feature.setBlockageReason(cmd.blockageReason());
        feature.setNotes(cmd.notes());
        feature.setUpdatedBy(cmd.updatedBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);
        log.info(
                "User {} updated planning for feature {} in release {}",
                cmd.updatedBy(),
                cmd.featureCode(),
                cmd.releaseCode());
    }

    @Transactional
    public void moveFeature(MoveFeatureCommand cmd) {
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));
        Release targetRelease = releaseRepository
                .findByCode(cmd.targetReleaseCode())
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + cmd.targetReleaseCode()));
        String moveNote = "Moved to release " + cmd.targetReleaseCode() + ". Rationale: " + cmd.rationale();
        String existingNotes = feature.getNotes();
        feature.setNotes(existingNotes != null ? existingNotes + "\n" + moveNote : moveNote);
        feature.setRelease(targetRelease);
        feature.setUpdatedBy(cmd.movedBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);
        log.info("User {} moved feature {} to release {}", cmd.movedBy(), cmd.featureCode(), cmd.targetReleaseCode());
    }

    @Transactional
    public void removeFeatureFromRelease(RemoveFeatureFromReleaseCommand cmd) {
        Feature feature = featureRepository
                .findByCode(cmd.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + cmd.featureCode()));
        if (feature.getRelease() == null || !feature.getRelease().getCode().equals(cmd.releaseCode())) {
            throw new BadRequestException(
                    "Feature " + cmd.featureCode() + " is not assigned to release " + cmd.releaseCode());
        }
        String removeNote = "Removed from release " + cmd.releaseCode() + ". Rationale: " + cmd.rationale();
        String existingNotes = feature.getNotes();
        feature.setNotes(existingNotes != null ? existingNotes + "\n" + removeNote : removeNote);
        feature.setRelease(null);
        feature.setUpdatedBy(cmd.removedBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);
        log.info("User {} removed feature {} from release {}", cmd.removedBy(), cmd.featureCode(), cmd.releaseCode());
    }

    void validatePlanningStatusTransition(FeaturePlanningStatus currentStatus, FeaturePlanningStatus newStatus) {
        if (currentStatus == null || currentStatus == newStatus) {
            return;
        }
        Set<FeaturePlanningStatus> allowed = ALLOWED_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new BadRequestException(
                    "Invalid planning status transition from " + currentStatus + " to " + newStatus);
        }
    }
}
