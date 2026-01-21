package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {
    public static final String RELEASE_SEPARATOR = "-";
    private final ReleaseRepository releaseRepository;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;
    private final ReleaseMapper releaseMapper;
    private final PlanningHistoryService planningHistoryService;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            ReleaseMapper releaseMapper,
            PlanningHistoryService planningHistoryService) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.releaseMapper = releaseMapper;
        this.planningHistoryService = planningHistoryService;
    }

    @Transactional(readOnly = true)
    public List<ReleaseDto> findReleasesByProductCode(String productCode) {
        return releaseRepository.findByProductCode(productCode).stream()
                .map(releaseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReleaseDto> findReleaseByCode(String code) {
        return releaseRepository.findByCode(code).map(releaseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isReleaseExists(String code) {
        return releaseRepository.existsByCode(code);
    }

    @Transactional
    public String createRelease(CreateReleaseCommand cmd) {
        Product product = productRepository.findByCode(cmd.productCode()).orElseThrow();
        String code = cmd.code();
        if (!cmd.code().startsWith(product.getPrefix() + RELEASE_SEPARATOR)) {
            code = product.getPrefix() + RELEASE_SEPARATOR + cmd.code();
        }
        Release release = new Release();
        release.setProduct(product);
        release.setCode(code);
        release.setDescription(cmd.description());
        release.setStatus(ReleaseStatus.DRAFT);
        release.setPlannedStartDate(cmd.plannedStartDate());
        release.setPlannedReleaseDate(cmd.plannedReleaseDate());
        release.setOwner(cmd.owner());
        release.setNotes(cmd.notes());
        release.setCreatedBy(cmd.createdBy());
        release.setCreatedAt(Instant.now());
        release = releaseRepository.save(release);

        // Record creation in planning history
        planningHistoryService.recordChange(
                EntityType.RELEASE,
                release.getCode(),
                release.getId(),
                ChangeType.CREATED,
                null,
                null,
                null,
                null,
                cmd.createdBy());

        return code;
    }

    @Transactional
    public void updateRelease(UpdateReleaseCommand cmd) {
        Release release = releaseRepository.findByCode(cmd.code()).orElseThrow();

        // Capture old values for change tracking
        String oldDescription = release.getDescription();
        ReleaseStatus oldStatus = release.getStatus();
        Instant oldReleasedAt = release.getReleasedAt();
        Instant oldPlannedStartDate = release.getPlannedStartDate();
        Instant oldPlannedReleaseDate = release.getPlannedReleaseDate();
        Instant oldActualReleaseDate = release.getActualReleaseDate();
        String oldOwner = release.getOwner();
        String oldNotes = release.getNotes();

        // Update release
        release.setDescription(cmd.description());
        release.setStatus(cmd.status());
        release.setReleasedAt(cmd.releasedAt());
        release.setPlannedStartDate(cmd.plannedStartDate());
        release.setPlannedReleaseDate(cmd.plannedReleaseDate());
        release.setActualReleaseDate(cmd.actualReleaseDate());
        release.setOwner(cmd.owner());
        release.setNotes(cmd.notes());
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);

        // Track changes in planning history
        trackFieldChange(
                EntityType.RELEASE,
                release.getCode(),
                "description",
                oldDescription,
                cmd.description(),
                cmd.updatedBy());

        // Status change
        if (oldStatus != cmd.status()) {
            planningHistoryService.recordChange(
                    EntityType.RELEASE,
                    release.getCode(),
                    ChangeType.STATUS_CHANGED,
                    "status",
                    oldStatus != null ? oldStatus.toString() : null,
                    cmd.status() != null ? cmd.status().toString() : null,
                    cmd.updatedBy());
        }

        // Other planning fields
        trackFieldChange(
                EntityType.RELEASE,
                release.getCode(),
                "releasedAt",
                oldReleasedAt != null ? oldReleasedAt.toString() : null,
                cmd.releasedAt() != null ? cmd.releasedAt().toString() : null,
                cmd.updatedBy());

        trackFieldChange(
                EntityType.RELEASE,
                release.getCode(),
                "plannedStartDate",
                oldPlannedStartDate != null ? oldPlannedStartDate.toString() : null,
                cmd.plannedStartDate() != null ? cmd.plannedStartDate().toString() : null,
                cmd.updatedBy());

        trackFieldChange(
                EntityType.RELEASE,
                release.getCode(),
                "plannedReleaseDate",
                oldPlannedReleaseDate != null ? oldPlannedReleaseDate.toString() : null,
                cmd.plannedReleaseDate() != null ? cmd.plannedReleaseDate().toString() : null,
                cmd.updatedBy());

        trackFieldChange(
                EntityType.RELEASE,
                release.getCode(),
                "actualReleaseDate",
                oldActualReleaseDate != null ? oldActualReleaseDate.toString() : null,
                cmd.actualReleaseDate() != null ? cmd.actualReleaseDate().toString() : null,
                cmd.updatedBy());

        trackFieldChange(EntityType.RELEASE, release.getCode(), "owner", oldOwner, cmd.owner(), cmd.updatedBy());

        trackFieldChange(EntityType.RELEASE, release.getCode(), "notes", oldNotes, cmd.notes(), cmd.updatedBy());
    }

    @Transactional
    public void deleteRelease(String code) {
        Release release = releaseRepository
                .findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Release with code " + code + " not found"));

        // Record deletion in planning history - we need a user for this, so we'll add
        // it to the method signature
        // For now, we'll use a placeholder
        planningHistoryService.recordChange(
                EntityType.RELEASE,
                release.getCode(),
                ChangeType.DELETED,
                null,
                null,
                null,
                "system" // We should modify this method to accept a deletedBy parameter
                );

        featureRepository.unsetRelease(code);
        releaseRepository.deleteByCode(code);
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
