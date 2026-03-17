package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.api.models.PagedResult;
import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);
    public static final String RELEASE_SEPARATOR = "-";

    private static final List<ReleaseStatus> TERMINAL_STATUSES =
            List.of(ReleaseStatus.COMPLETED, ReleaseStatus.RELEASED, ReleaseStatus.CANCELLED);

    private final ReleaseRepository releaseRepository;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;
    private final ReleaseMapper releaseMapper;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            ReleaseMapper releaseMapper) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.releaseMapper = releaseMapper;
    }

    @Transactional(readOnly = true)
    public PagedResult<ReleaseDto> findReleases(
            String productCode,
            ReleaseStatus status,
            String owner,
            Instant startDate,
            Instant endDate,
            int page,
            int size) {
        validateDateRange(startDate, endDate);
        Pageable pageable = PageRequest.of(page, size);
        Specification<Release> spec = buildSpecification(productCode, status, owner, startDate, endDate);
        var pageResult = releaseRepository.findAll(spec, pageable);
        return new PagedResult<>(
                pageResult.getContent().stream().map(releaseMapper::toDto).toList(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public Optional<ReleaseDto> findReleaseByCode(String code) {
        return releaseRepository.findByCode(code).map(releaseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isReleaseExists(String code) {
        return releaseRepository.existsByCode(code);
    }

    @Transactional(readOnly = true)
    public List<ReleaseDto> findOverdueReleases() {
        return releaseRepository.findOverdue(Instant.now(), TERMINAL_STATUSES).stream()
                .map(releaseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReleaseDto> findAtRiskReleases(int daysThreshold) {
        Instant now = Instant.now();
        Instant deadline = now.plus(daysThreshold, ChronoUnit.DAYS);
        return releaseRepository.findAtRisk(now, deadline, TERMINAL_STATUSES).stream()
                .map(releaseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReleaseDto> findReleasesByStatus(ReleaseStatus status) {
        return releaseRepository.findByStatus(status).stream()
                .map(releaseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReleaseDto> findReleasesByOwner(String owner) {
        return releaseRepository.findByOwner(owner).stream()
                .map(releaseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReleaseDto> findReleasesByDateRange(Instant startDate, Instant endDate) {
        validateDateRange(startDate, endDate);
        return releaseRepository.findByPlannedReleaseDateBetween(startDate, endDate).stream()
                .map(releaseMapper::toDto)
                .toList();
    }

    private void validateDateRange(Instant startDate, Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate cannot be after endDate");
        }
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
        log.info("Created release with code {} by {}", code, cmd.createdBy());
        return code;
    }

    @Transactional
    public void updateRelease(UpdateReleaseCommand cmd) {
        Release release = releaseRepository.findByCode(cmd.code()).orElseThrow();
        ReleaseStatus newStatus = cmd.status();
        if (newStatus != null) {
            ReleaseStatus currentStatus = release.getStatus();
            if (!currentStatus.canTransitionTo(newStatus)) {
                throw new BadRequestException("Invalid status transition from " + currentStatus + " to " + newStatus);
            }
            if (currentStatus != newStatus) {
                log.info(
                        "Release {} status changed from {} to {} by {}",
                        cmd.code(),
                        currentStatus,
                        newStatus,
                        cmd.updatedBy());
            }
            release.setStatus(newStatus);
        }
        release.setDescription(cmd.description());
        release.setReleasedAt(cmd.releasedAt());
        release.setPlannedStartDate(cmd.plannedStartDate());
        release.setPlannedReleaseDate(cmd.plannedReleaseDate());
        release.setActualReleaseDate(cmd.actualReleaseDate());
        release.setOwner(cmd.owner());
        release.setNotes(cmd.notes());
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);
        log.info("Updated release with code {} by {}", cmd.code(), cmd.updatedBy());
    }

    @Transactional
    public void deleteRelease(String code) {
        if (!releaseRepository.existsByCode(code)) {
            throw new ResourceNotFoundException("Release with code " + code + " not found");
        }
        featureRepository.unsetRelease(code);
        releaseRepository.deleteByCode(code);
        log.info("Deleted release with code {}", code);
    }

    private Specification<Release> buildSpecification(
            String productCode, ReleaseStatus status, String owner, Instant startDate, Instant endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productCode != null) {
                predicates.add(cb.equal(root.get("product").get("code"), productCode));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (owner != null) {
                predicates.add(cb.equal(root.get("owner"), owner));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("plannedReleaseDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("plannedReleaseDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
