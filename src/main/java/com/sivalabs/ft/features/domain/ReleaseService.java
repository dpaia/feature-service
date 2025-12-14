package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.HashMap;
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
    private static final Logger logger = LoggerFactory.getLogger(ReleaseService.class);
    public static final String RELEASE_SEPARATOR = "-";

    private final ReleaseRepository releaseRepository;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;
    private final ReleaseMapper releaseMapper;
    private final NotificationService notificationService;
    private final NotificationRecipientService recipientService;
    private final ObjectMapper objectMapper;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            ReleaseMapper releaseMapper,
            NotificationService notificationService,
            NotificationRecipientService recipientService,
            ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.releaseMapper = releaseMapper;
        this.notificationService = notificationService;
        this.recipientService = recipientService;
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

        // Validate status transition
        if (!isValidStatusTransition(previousStatus, newStatus)) {
            throw new BadRequestException(String.format(
                    "Invalid status transition from %s to %s for release %s", previousStatus, newStatus, cmd.code()));
        }

        release.setDescription(cmd.description());
        release.setStatus(newStatus);
        release.setReleasedAt(cmd.releasedAt());
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);

        // Create cascade notifications for significant status changes
        if (shouldTriggerCascadeNotification(newStatus)) {
            createCascadeNotificationsForRelease(release, previousStatus, newStatus, cmd.updatedBy());
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

    /**
     * Validate if the status transition is allowed according to business rules
     */
    private boolean isValidStatusTransition(ReleaseStatus from, ReleaseStatus to) {
        if (from == to) {
            return true; // Same status is always valid
        }

        switch (from) {
            case DRAFT:
                return to == ReleaseStatus.PLANNED;
            case PLANNED:
                return to == ReleaseStatus.IN_PROGRESS;
            case IN_PROGRESS:
                return to == ReleaseStatus.RELEASED || to == ReleaseStatus.DELAYED || to == ReleaseStatus.CANCELLED;
            case DELAYED:
                return to == ReleaseStatus.RELEASED || to == ReleaseStatus.CANCELLED;
            case RELEASED:
                return to == ReleaseStatus.COMPLETED;
            case CANCELLED:
            case COMPLETED:
                return false; // End states, cannot change further
            default:
                return false;
        }
    }

    /**
     * Check if the status change should trigger cascade notifications
     */
    private boolean shouldTriggerCascadeNotification(ReleaseStatus newStatus) {
        return newStatus == ReleaseStatus.RELEASED
                || newStatus == ReleaseStatus.DELAYED
                || newStatus == ReleaseStatus.CANCELLED
                || newStatus == ReleaseStatus.COMPLETED;
    }

    /**
     * Create cascade notifications for all users with features in the release
     */
    private void createCascadeNotificationsForRelease(
            Release release, ReleaseStatus previousStatus, ReleaseStatus newStatus, String excludeUser) {

        Set<String> recipients = recipientService.getReleaseNotificationRecipients(release.getCode(), excludeUser);

        if (recipients.isEmpty()) {
            logger.debug("No recipients found for release {} cascade notifications", release.getCode());
            return;
        }

        // Prepare event details once (same for all recipients)
        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("releaseCode", release.getCode());
        eventDetails.put("previousStatus", previousStatus.name());
        eventDetails.put("newStatus", newStatus.name());
        eventDetails.put("releaseDescription", release.getDescription());

        try {
            String eventDetailsJson = objectMapper.writeValueAsString(eventDetails);
            String link = "/releases/" + release.getCode();

            for (String recipientUserId : recipients) {
                notificationService.createNotification(
                        recipientUserId, NotificationEventType.RELEASE_UPDATED, eventDetailsJson, link);
                logger.debug(
                        "Created cascade notification for user {} about release {} status change to {}",
                        recipientUserId,
                        release.getCode(),
                        newStatus);
            }

            logger.info(
                    "Created {} cascade notifications for release {} status change from {} to {}",
                    recipients.size(),
                    release.getCode(),
                    previousStatus,
                    newStatus);

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event details for release {}", release.getCode(), e);
        }
    }
}
