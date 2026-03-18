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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MilestoneService {
    private final MilestoneRepository milestoneRepository;
    private final ProductRepository productRepository;
    private final MilestoneMapper milestoneMapper;
    private final EventPublisher eventPublisher;

    MilestoneService(
            MilestoneRepository milestoneRepository,
            ProductRepository productRepository,
            MilestoneMapper milestoneMapper,
            EventPublisher eventPublisher) {
        this.milestoneRepository = milestoneRepository;
        this.productRepository = productRepository;
        this.milestoneMapper = milestoneMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Optional<MilestoneDto> findMilestoneByCode(String code) {
        return milestoneRepository.findByCode(code).map(this::toDto);
    }

    private @NonNull MilestoneDto toDto(Milestone milestone) {
        MilestoneDto dto = milestoneMapper.toDto(milestone);
        Integer progress = calculateProgress(milestone.getReleases());
        List<MilestoneReleaseDto> releaseDtos = milestoneMapper.toReleaseDtoList(milestone.getReleases());
        return new MilestoneDto(
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

        milestone = milestoneRepository.save(milestone);

        eventPublisher.publishMilestoneCreatedEvent(toDto(milestone));

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

        milestone = milestoneRepository.save(milestone);

        eventPublisher.publishMilestoneUpdatedEvent(toDto(milestone));
    }

    @Transactional
    public void deleteMilestone(String code) {
        Milestone milestone = milestoneRepository
                .findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone with code %s not found".formatted(code)));

        milestone.getReleases().forEach(release -> release.setMilestone(null));
        milestoneRepository.delete(milestone);
        eventPublisher.publishMilestoneDeletedEvent(toDto(milestone));
    }

    private Integer calculateProgress(Collection<Release> releases) {
        if (releases == null || releases.isEmpty()) {
            return 0;
        }

        long completedCount = releases.stream()
                .filter(release -> release.getStatus() == ReleaseStatus.RELEASED)
                .count();

        return (int) Math.round((completedCount * 100.0) / releases.size());
    }
}
