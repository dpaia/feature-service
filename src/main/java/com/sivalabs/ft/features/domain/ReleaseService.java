package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.ChangeType;
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
        releaseRepository.save(release);
        planningHistoryService.recordReleaseCreated(release);
        return code;
    }

    @Transactional
    public void updateRelease(UpdateReleaseCommand cmd) {
        Release release = releaseRepository.findByCode(cmd.code()).orElseThrow();

        String oldStatus = release.getStatus() != null ? release.getStatus().name() : null;
        String newStatus = cmd.status() != null ? cmd.status().name() : null;

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

        if (!Objects.equals(oldStatus, newStatus)) {
            planningHistoryService.recordReleaseFieldChange(
                    release, "status", oldStatus, newStatus, ChangeType.STATUS_CHANGED, cmd.updatedBy());
        } else {
            planningHistoryService.recordReleaseFieldChange(
                    release, null, null, null, ChangeType.UPDATED, cmd.updatedBy());
        }
    }

    @Transactional
    public void deleteRelease(String code) {
        Release release = releaseRepository
                .findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Release with code " + code + " not found"));
        planningHistoryService.recordReleaseDeleted(
                release, release.getUpdatedBy() != null ? release.getUpdatedBy() : release.getCreatedBy());
        featureRepository.unsetRelease(code);
        releaseRepository.deleteByCode(code);
    }
}
