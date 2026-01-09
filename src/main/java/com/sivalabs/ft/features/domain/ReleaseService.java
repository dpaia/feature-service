package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand;
import com.sivalabs.ft.features.domain.dtos.ReleaseDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import java.util.ArrayList;
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
    private static final Logger logger = LoggerFactory.getLogger(ReleaseService.class);
    public static final String RELEASE_SEPARATOR = "-";

    private final ReleaseRepository releaseRepository;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;
    private final ReleaseMapper releaseMapper;
    private final ReleaseStatusValidator releaseStatusValidator;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    ReleaseService(
            ReleaseRepository releaseRepository,
            ProductRepository productRepository,
            FeatureRepository featureRepository,
            ReleaseMapper releaseMapper,
            ReleaseStatusValidator releaseStatusValidator,
            NotificationService notificationService,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
        this.featureRepository = featureRepository;
        this.releaseMapper = releaseMapper;
        this.releaseStatusValidator = releaseStatusValidator;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
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

        ReleaseStatus oldStatus = release.getStatus();
        ReleaseStatus newStatus = cmd.status();

        // Validate status transition
        releaseStatusValidator.validateTransition(oldStatus, newStatus);

        // Update release fields
        release.setDescription(cmd.description());
        release.setStatus(newStatus);
        release.setReleasedAt(cmd.releasedAt());
        release.setUpdatedBy(cmd.updatedBy());
        release.setUpdatedAt(Instant.now());

        // Create cascade notifications for significant status changes
        if (isSignificantStatus(newStatus)) {
            createNotificationsForRelease(release, oldStatus, cmd.updatedBy());
        }

        releaseRepository.save(release);
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
     * Checks if status triggers cascade notifications.
     */
    private boolean isSignificantStatus(ReleaseStatus status) {
        return status == ReleaseStatus.RELEASED
                || status == ReleaseStatus.DELAYED
                || status == ReleaseStatus.CANCELLED
                || status == ReleaseStatus.COMPLETED;
    }

    /**
     * Creates cascade notifications for users with features in the release.
     */
    private void createNotificationsForRelease(Release release, ReleaseStatus oldStatus, String updatedBy) {
        List<Feature> features = featureRepository.findByReleaseCode(release.getCode());

        if (features.isEmpty()) {
            logger.debug("No features found for release {}, skipping notifications", release.getCode());
            return;
        }

        // Collect all unique recipients (createdBy + assignedTo from all features)
        Set<String> recipients = new HashSet<>();
        for (Feature feature : features) {
            if (feature.getCreatedBy() != null && !feature.getCreatedBy().equals(updatedBy)) {
                recipients.add(feature.getCreatedBy());
            }
            if (feature.getAssignedTo() != null && !feature.getAssignedTo().equals(updatedBy)) {
                recipients.add(feature.getAssignedTo());
            }
        }

        if (recipients.isEmpty()) {
            logger.debug("No notification recipients found for release {}", release.getCode());
            return;
        }

        // Prepare event details
        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("releaseCode", release.getCode());
        eventDetails.put("oldStatus", oldStatus.name());
        eventDetails.put("newStatus", release.getStatus().name());
        eventDetails.put("description", release.getDescription());
        eventDetails.put("actor", updatedBy);

        try {
            String eventDetailsJson = objectMapper.writeValueAsString(eventDetails);
            String link = "/releases/" + release.getCode();

            // Prepare batch notification data
            List<NotificationService.NotificationData> notificationsData = new ArrayList<>();
            for (String recipient : recipients) {
                String recipientEmail = userRepository
                        .findByUsername(recipient)
                        .map(user -> user.getEmail())
                        .orElse(null);

                if (recipientEmail == null) {
                    logger.warn(
                            "User {} not found, skipping notification for release {}", recipient, release.getCode());
                    continue;
                }

                notificationsData.add(new NotificationService.NotificationData(
                        recipient, recipientEmail, NotificationEventType.RELEASE_UPDATED, eventDetailsJson, link));
            }

            // Create all notifications in a single batch operation
            notificationService.createNotificationsBatch(notificationsData);

            logger.info(
                    "Created {} cascade notifications for release {} status change: {} -> {}",
                    recipients.size(),
                    release.getCode(),
                    oldStatus,
                    release.getStatus());

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event details for release {}", release.getCode(), e);
        }
    }
}
