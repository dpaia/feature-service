package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateMilestoneCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateMilestoneCommand;
import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.dtos.MilestoneReleaseDto;
import com.sivalabs.ft.features.domain.dtos.MilestoneSummaryDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.MilestoneMapper;
import com.sivalabs.ft.features.domain.models.MilestoneStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MilestoneService {
    private final MilestoneRepository milestoneRepository;
    private final ProductRepository productRepository;
    private final ReleaseRepository releaseRepository;
    private final MilestoneMapper milestoneMapper;
    private final EventPublisher eventPublisher;

    MilestoneService(
            MilestoneRepository milestoneRepository,
            ProductRepository productRepository,
            ReleaseRepository releaseRepository,
            MilestoneMapper milestoneMapper,
            EventPublisher eventPublisher) {
        this.milestoneRepository = milestoneRepository;
        this.productRepository = productRepository;
        this.releaseRepository = releaseRepository;
        this.milestoneMapper = milestoneMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Optional<MilestoneDto> findMilestoneByCode(String code) {
        Optional<Milestone> milestoneOptional = milestoneRepository.findByCodeWithReleases(code);
        if (milestoneOptional.isEmpty()) {
            return Optional.empty();
        }
        Milestone milestone = milestoneOptional.get();
        MilestoneDto dto = milestoneMapper.toDto(milestone);
        Integer progress = calculateProgress(new ArrayList<>(milestone.getReleases()));
        List<MilestoneReleaseDto> releaseDtos =
                milestoneMapper.toReleaseDtoList(new ArrayList<>(milestone.getReleases()));
        dto = new MilestoneDto(
                dto.id(),
                dto.code(),
                dto.name(),
                dto.description(),
                dto.targetDate(),
                dto.actualDate(),
                dto.status(),
                dto.productCode(),
                dto.owner(),
                dto.notes(),
                progress,
                releaseDtos,
                dto.createdBy(),
                dto.createdAt(),
                dto.updatedBy(),
                dto.updatedAt());

        return Optional.of(dto);
    }

    @Transactional(readOnly = true)
    public List<MilestoneSummaryDto> findMilestonesByProductCode(String productCode, String status, String owner) {
        List<Milestone> milestones;

        if (StringUtils.isNotBlank(status) && StringUtils.isNotBlank(owner)) {
            MilestoneStatus milestoneStatus = MilestoneStatus.valueOf(status);
            milestones = milestoneRepository.findByProductCodeAndStatusAndOwner(productCode, milestoneStatus, owner);
        } else if (StringUtils.isNotBlank(status)) {
            MilestoneStatus milestoneStatus = MilestoneStatus.valueOf(status);
            milestones = milestoneRepository.findByProductCodeAndStatus(productCode, milestoneStatus);
        } else if (StringUtils.isNotBlank(owner)) {
            milestones = milestoneRepository.findByProductCodeAndOwner(productCode, owner);
        } else {
            milestones = milestoneRepository.findByProductCode(productCode);
        }

        return milestones.stream()
                .map(milestone -> {
                    MilestoneSummaryDto dto = milestoneMapper.toSummaryDto(milestone);
                    Integer progress = calculateProgress(new ArrayList<>(milestone.getReleases()));
                    return new MilestoneSummaryDto(
                            dto.id(),
                            dto.code(),
                            dto.name(),
                            dto.description(),
                            dto.targetDate(),
                            dto.actualDate(),
                            dto.status(),
                            dto.productCode(),
                            dto.owner(),
                            dto.notes(),
                            progress,
                            dto.createdBy(),
                            dto.createdAt(),
                            dto.updatedBy(),
                            dto.updatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isMilestoneExists(String code) {
        return milestoneRepository.existsByCode(code);
    }

    @Transactional
    public String createMilestone(CreateMilestoneCommand cmd) {
        if (milestoneRepository.existsByCode(cmd.code())) {
            throw new BadRequestException("Milestone with code %s already exists".formatted(cmd.code()));
        }
        Product product = productRepository
                .findByCode(cmd.productCode())
                .orElseThrow(
                        () -> new BadRequestException("Product with code %s not found".formatted(cmd.productCode())));

        Milestone milestone = new Milestone();
        milestone.setCode(cmd.code());
        milestone.setName(cmd.name());
        milestone.setDescription(cmd.description());
        milestone.setTargetDate(cmd.targetDate());
        milestone.setStatus(cmd.status());
        milestone.setProduct(product);
        milestone.setOwner(cmd.owner());
        milestone.setNotes(cmd.notes());
        milestone.setCreatedBy(cmd.createdBy());
        milestone.setCreatedAt(Instant.now());

        milestoneRepository.save(milestone);

        // Publish milestone created event
        Optional<MilestoneDto> milestoneDto = findMilestoneByCode(milestone.getCode());
        milestoneDto.ifPresent(eventPublisher::publishMilestoneCreatedEvent);

        return milestone.getCode();
    }

    @Transactional
    public void updateMilestone(UpdateMilestoneCommand cmd) {
        Milestone milestone = milestoneRepository
                .findByCode(cmd.code())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Milestone with code %s not found".formatted(cmd.code())));

        milestone.setName(cmd.name());
        milestone.setDescription(cmd.description());
        milestone.setTargetDate(cmd.targetDate());
        milestone.setActualDate(cmd.actualDate());
        milestone.setStatus(cmd.status());
        milestone.setOwner(cmd.owner());
        milestone.setNotes(cmd.notes());
        milestone.setUpdatedBy(cmd.updatedBy());
        milestone.setUpdatedAt(Instant.now());

        milestoneRepository.save(milestone);

        // Publish milestone updated event
        Optional<MilestoneDto> milestoneDto = findMilestoneByCode(milestone.getCode());
        milestoneDto.ifPresent(eventPublisher::publishMilestoneUpdatedEvent);
    }

    @Transactional
    public void deleteMilestone(String code) {
        if (!milestoneRepository.existsByCode(code)) {
            throw new ResourceNotFoundException("Milestone with code %s not found".formatted(code));
        }

        // TODO: Implement milestone deleted event publishing
        // Currently disabled due to Hibernate TransientObjectException
        // when trying to access milestone with releases after unsetMilestone operation

        releaseRepository.unsetMilestone(code);
        milestoneRepository.deleteByCode(code);

        // Note: Milestone deleted event publishing is temporarily disabled
        // to avoid Hibernate issues. This should be implemented in a separate task.
    }

    private Integer calculateProgress(List<Release> releases) {
        if (releases == null || releases.isEmpty()) {
            return 0;
        }

        long completedCount = releases.stream()
                .filter(release -> release.getStatus() == ReleaseStatus.RELEASED)
                .count();

        return (int) Math.round((completedCount * 100.0) / releases.size());
    }
}
