package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);
    public static final String RELEASE_SEPARATOR = "-";
    private static final Set<ReleaseStatus> CASCADE_STATUSES =
            EnumSet.of(ReleaseStatus.RELEASED, ReleaseStatus.DELAYED, ReleaseStatus.CANCELLED, ReleaseStatus.COMPLETED);

    private final ReleaseRepository releaseRepository;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;
    private final ReleaseMapper releaseMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            ReleaseMapper releaseMapper,
            NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.releaseMapper = releaseMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
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
        return code;
    }

    @Transactional
    public void updateRelease(UpdateReleaseCommand cmd) {
        Release release = releaseRepository.findByCode(cmd.code()).orElseThrow();
        ReleaseStatus previousStatus = release.getStatus();
        ReleaseStatus newStatus = cmd.status();

        if (newStatus != null && !newStatus.equals(previousStatus)) {
            if (!previousStatus.canTransitionTo(newStatus)) {
                throw new BadRequestException("Invalid status transition from " + previousStatus + " to " + newStatus);
            }
        }

        release.setDescription(cmd.description());
        if (newStatus != null) {
            release.setStatus(newStatus);
        }
        release.setReleasedAt(cmd.releasedAt());
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);

        if (newStatus != null && !newStatus.equals(previousStatus) && CASCADE_STATUSES.contains(newStatus)) {
            createCascadeNotifications(release, previousStatus, newStatus, cmd.updatedBy());
        }
    }

    private void createCascadeNotifications(
            Release release, ReleaseStatus previousStatus, ReleaseStatus newStatus, String excludeUser) {
        Set<String> recipients = new HashSet<>();
        for (Feature feature : release.getFeatures()) {
            if (feature.getCreatedBy() != null && !feature.getCreatedBy().equals(excludeUser)) {
                recipients.add(feature.getCreatedBy());
            }
            if (feature.getAssignedTo() != null && !feature.getAssignedTo().equals(excludeUser)) {
                recipients.add(feature.getAssignedTo());
            }
        }

        if (recipients.isEmpty()) {
            return;
        }

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("releaseCode", release.getCode());
        eventDetails.put("previousStatus", previousStatus.name());
        eventDetails.put("newStatus", newStatus.name());
        eventDetails.put("description", release.getDescription());

        try {
            String eventDetailsJson = objectMapper.writeValueAsString(eventDetails);
            String link = "/releases/" + release.getCode();
            notificationService.createNotificationsForRecipients(
                    recipients, NotificationEventType.RELEASE_UPDATED, eventDetailsJson, link);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize event details for release " + release.getCode() + ": " + e.getMessage(), e);
        }
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
