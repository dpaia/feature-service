package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.InvalidStatusTransitionException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);
    public static final String RELEASE_SEPARATOR = "-";

    private final ReleaseRepository releaseRepository;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;
    private final ReleaseMapper releaseMapper;
    private final ReleaseStatusTransitionValidator statusTransitionValidator;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            ReleaseMapper releaseMapper,
            ReleaseStatusTransitionValidator statusTransitionValidator) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.releaseMapper = releaseMapper;
        this.statusTransitionValidator = statusTransitionValidator;
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
        release.setPlannedReleaseDate(cmd.plannedReleaseDate());
        release.setReleaseOwner(cmd.releaseOwner());
        release.setCreatedBy(cmd.createdBy());
        release.setCreatedAt(Instant.now());
        releaseRepository.save(release);
        log.info("Created release {} with status {}", code, ReleaseStatus.DRAFT);
        return code;
    }

    @Transactional
    public void updateRelease(UpdateReleaseCommand cmd) {
        Release release = releaseRepository
                .findByCode(cmd.code())
                .orElseThrow(() -> new ResourceNotFoundException("Release with code " + cmd.code() + " not found"));

        ReleaseStatus currentStatus = release.getStatus();
        ReleaseStatus newStatus = cmd.status();

        // Validate status transition if status is being changed
        if (newStatus != null && !currentStatus.equals(newStatus)) {
            if (!statusTransitionValidator.validateTransition(currentStatus, newStatus)) {
                Set<ReleaseStatus> validNextStates = statusTransitionValidator.getValidNextStates(currentStatus);
                log.warn(
                        "Invalid status transition attempted for release {}: {} -> {}. Valid next states: {}",
                        cmd.code(),
                        currentStatus,
                        newStatus,
                        validNextStates);
                throw new InvalidStatusTransitionException(currentStatus, newStatus, validNextStates);
            }
            log.info(
                    "Status transition for release {}: {} -> {} by user {}",
                    cmd.code(),
                    currentStatus,
                    newStatus,
                    cmd.updatedBy());
            release.setStatus(newStatus);
        }

        release.setDescription(cmd.description());
        release.setReleasedAt(cmd.releasedAt());
        if (cmd.plannedReleaseDate() != null) {
            release.setPlannedReleaseDate(cmd.plannedReleaseDate());
        }
        if (cmd.releaseOwner() != null) {
            release.setReleaseOwner(cmd.releaseOwner());
        }
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());

        try {
            releaseRepository.save(release);
        } catch (OptimisticLockException e) {
            log.warn("Concurrent update detected for release {}", cmd.code());
            throw new IllegalStateException("Release was modified by another user. Please refresh and try again.", e);
        }
    }

    /**
     * Get valid next states for a release.
     *
     * @param releaseCode The release code
     * @return Set of valid next states
     */
    @Transactional(readOnly = true)
    public Set<ReleaseStatus> getValidNextStates(String releaseCode) {
        Release release = releaseRepository
                .findByCode(releaseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Release with code " + releaseCode + " not found"));
        return statusTransitionValidator.getValidNextStates(release.getStatus());
    }

    /**
     * Find overdue releases (past planned release date but not completed/cancelled).
     */
    @Transactional(readOnly = true)
    public Page<ReleaseDto> findOverdueReleases(Pageable pageable) {
        Instant now = Instant.now();
        return releaseRepository.findOverdueReleases(now, pageable).map(releaseMapper::toDto);
    }

    /**
     * Find at-risk releases (approaching deadline within threshold days).
     *
     * @param daysThreshold Number of days before deadline
     */
    @Transactional(readOnly = true)
    public Page<ReleaseDto> findAtRiskReleases(int daysThreshold, Pageable pageable) {
        if (daysThreshold < 1) {
            throw new IllegalArgumentException("daysThreshold must be greater than or equal to 1");
        }
        Instant now = Instant.now();
        Instant thresholdTime = now.plus(Duration.ofDays(daysThreshold));
        return releaseRepository
                .findAtRiskReleases(now, thresholdTime, pageable)
                .map(releaseMapper::toDto);
    }

    /**
     * Find releases by status.
     */
    @Transactional(readOnly = true)
    public Page<ReleaseDto> findByStatus(ReleaseStatus status, Pageable pageable) {
        return releaseRepository.findByStatus(status, pageable).map(releaseMapper::toDto);
    }

    /**
     * Find releases by owner.
     */
    @Transactional(readOnly = true)
    public Page<ReleaseDto> findByOwner(String owner, Pageable pageable) {
        return releaseRepository.findByReleaseOwner(owner, pageable).map(releaseMapper::toDto);
    }

    /**
     * Find releases by date range with optional status filter.
     */
    @Transactional(readOnly = true)
    public Page<ReleaseDto> findByDateRange(
            Instant startDate, Instant endDate, ReleaseStatus status, Pageable pageable) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Both startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }

        if (status != null) {
            return releaseRepository
                    .findByPlannedReleaseDateBetweenAndStatus(startDate, endDate, status, pageable)
                    .map(releaseMapper::toDto);
        } else {
            return releaseRepository
                    .findByPlannedReleaseDateBetween(startDate, endDate, pageable)
                    .map(releaseMapper::toDto);
        }
    }

    /**
     * Enhanced list with multiple filters.
     */
    @Transactional(readOnly = true)
    public Page<ReleaseDto> findWithFilters(
            String productCode,
            ReleaseStatus status,
            String owner,
            Instant startDate,
            Instant endDate,
            Pageable pageable) {
        // Validate date range if both dates provided
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("startDate must be before or equal to endDate");
            }
        }

        return releaseRepository
                .findWithFilters(productCode, status, owner, startDate, endDate, pageable)
                .map(releaseMapper::toDto);
    }

    @Transactional
    public void deleteRelease(String code) {
        if (!releaseRepository.existsByCode(code)) {
            throw new ResourceNotFoundException("Release with code " + code + " not found");
        }
        featureRepository.unsetRelease(code);
        releaseRepository.deleteByCode(code);
    }
}
