package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateMilestoneCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateMilestoneCommand;
import com.sivalabs.ft.features.domain.dtos.MilestoneDto;
import com.sivalabs.ft.features.domain.entities.Milestone;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.MilestoneMapper;
import com.sivalabs.ft.features.domain.models.MilestoneStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MilestoneService {
    private final MilestoneRepository milestoneRepository;
    private final ProductRepository productRepository;
    private final MilestoneMapper milestoneMapper;

    MilestoneService(
            MilestoneRepository milestoneRepository,
            ProductRepository productRepository,
            MilestoneMapper milestoneMapper) {
        this.milestoneRepository = milestoneRepository;
        this.productRepository = productRepository;
        this.milestoneMapper = milestoneMapper;
    }

    @Transactional(readOnly = true)
    public List<MilestoneDto> findMilestonesByProductCode(String productCode) {
        return milestoneRepository.findByProductCode(productCode).stream()
                .map(milestoneMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MilestoneDto> findMilestonesByProductCodeAndStatus(String productCode, MilestoneStatus status) {
        return milestoneRepository.findByProductCodeAndStatus(productCode, status).stream()
                .map(milestoneMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MilestoneDto> findMilestonesByProductCodeAndOwner(String productCode, String owner) {
        return milestoneRepository.findByProductCodeAndOwner(productCode, owner).stream()
                .map(milestoneMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MilestoneDto> findMilestonesByProductCodeAndStatusAndOwner(
            String productCode, MilestoneStatus status, String owner) {
        return milestoneRepository.findByProductCodeAndStatusAndOwner(productCode, status, owner).stream()
                .map(milestoneMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<MilestoneDto> findMilestoneByCode(String code) {
        return milestoneRepository.findByCodeWithReleases(code).map(milestoneMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isMilestoneExists(String code) {
        return milestoneRepository.existsByCode(code);
    }

    @Transactional
    public void createMilestone(CreateMilestoneCommand command) {
        if (milestoneRepository.existsByCode(command.code())) {
            throw new BadRequestException("Milestone with code '%s' already exists".formatted(command.code()));
        }

        Product product = productRepository
                .findByCode(command.productCode())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Product not found with code: " + command.productCode()));

        Milestone milestone = new Milestone();
        milestone.setCode(command.code());
        milestone.setName(command.name());
        milestone.setDescription(command.description());
        milestone.setTargetDate(command.targetDate());
        milestone.setStatus(command.status());
        milestone.setProduct(product);
        milestone.setOwner(command.owner());
        milestone.setNotes(command.notes());
        milestone.setCreatedBy(command.createdBy());

        milestoneRepository.save(milestone);
    }

    @Transactional
    public void updateMilestone(UpdateMilestoneCommand command) {
        Milestone milestone = milestoneRepository
                .findByCode(command.code())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found with code: " + command.code()));

        milestone.setName(command.name());
        milestone.setDescription(command.description());
        milestone.setTargetDate(command.targetDate());
        milestone.setActualDate(command.actualDate());
        milestone.setStatus(command.status());
        milestone.setOwner(command.owner());
        milestone.setNotes(command.notes());
        milestone.setUpdatedBy(command.updatedBy());
        milestone.setUpdatedAt(Instant.now());

        milestoneRepository.save(milestone);
    }

    @Transactional
    public void deleteMilestone(String code) {
        if (!milestoneRepository.existsByCode(code)) {
            throw new ResourceNotFoundException("Milestone not found with code: " + code);
        }
        milestoneRepository.deleteByCode(code);
    }
}
