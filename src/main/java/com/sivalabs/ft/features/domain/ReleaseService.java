package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.events.EventType;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.List;
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
    private final MilestoneRepository milestoneRepository;
    private final EventPublisher eventPublisher;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            ReleaseMapper releaseMapper,
            MilestoneRepository milestoneRepository,
            EventPublisher eventPublisher) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.releaseMapper = releaseMapper;
        this.milestoneRepository = milestoneRepository;
        this.eventPublisher = eventPublisher;
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
        release.setCreatedBy(cmd.createdBy());
        release.setCreatedAt(Instant.now());
        releaseRepository.save(release);
        eventPublisher.publishReleaseEvent(releaseMapper.toDto(release), EventType.CREATED);
        return code;
    }

    @Transactional
    public void updateRelease(UpdateReleaseCommand cmd) {
        Release release = releaseRepository.findByCode(cmd.code()).orElseThrow();
        release.setDescription(cmd.description());
        release.setStatus(cmd.status());
        release.setReleasedAt(cmd.releasedAt());
        if (cmd.milestoneCode() != null) {
            Milestone milestone = milestoneRepository
                    .findByCode(cmd.milestoneCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Milestone with code %s not found".formatted(cmd.milestoneCode())));

            if (!milestone.getProduct().getId().equals(release.getProduct().getId())) {
                throw new BadRequestException("Milestone and Release must belong to the same Product");
            }

            release.setMilestone(milestone);
        } else {
            release.setMilestone(null);
        }
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);
        eventPublisher.publishReleaseEvent(releaseMapper.toDto(release), EventType.UPDATED);
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
