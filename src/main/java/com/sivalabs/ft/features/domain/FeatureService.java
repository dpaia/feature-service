package com.sivalabs.ft.features.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureService {
    private static final Logger logger = LoggerFactory.getLogger(FeatureService.class);
    public static final String FEATURE_SEPARATOR = "-";
    private final FavoriteFeatureService favoriteFeatureService;
    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ProductRepository productRepository;
    private final FavoriteFeatureRepository favoriteFeatureRepository;
    private final EventPublisher eventPublisher;
    private final FeatureMapper featureMapper;
    private final NotificationService notificationService;
    private final NotificationRecipientService recipientService;
    private final ObjectMapper objectMapper;

    FeatureService(
            FavoriteFeatureService favoriteFeatureService,
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ProductRepository productRepository,
            FavoriteFeatureRepository favoriteFeatureRepository,
            EventPublisher eventPublisher,
            FeatureMapper featureMapper,
            NotificationService notificationService,
            NotificationRecipientService recipientService,
            ObjectMapper objectMapper) {
        this.favoriteFeatureService = favoriteFeatureService;
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.favoriteFeatureRepository = favoriteFeatureRepository;
        this.featureMapper = featureMapper;
        this.notificationService = notificationService;
        this.recipientService = recipientService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<FeatureDto> findFeatureByCode(String username, String code) {
        Optional<Feature> optionalFeature = featureRepository.findByCode(code);
        if (optionalFeature.isEmpty()) {
            return Optional.empty();
        }
        List<FeatureDto> featureDtos = updateFavoriteStatus(List.of(optionalFeature.get()), username);
        return Optional.ofNullable(featureDtos.getFirst());
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findFeaturesByRelease(String username, String releaseCode) {
        List<Feature> features = featureRepository.findByReleaseCode(releaseCode);
        return updateFavoriteStatus(features, username);
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> findFeaturesByProduct(String username, String productCode) {
        List<Feature> features = featureRepository.findByProductCode(productCode);
        return updateFavoriteStatus(features, username);
    }

    private List<FeatureDto> updateFavoriteStatus(List<Feature> features, String username) {
        if (username == null || features.isEmpty()) {
            return features.stream().map(featureMapper::toDto).toList();
        }
        Set<String> featureCodes = features.stream().map(Feature::getCode).collect(Collectors.toSet());
        Map<String, Boolean> favoriteFeatures = favoriteFeatureService.getFavoriteFeatures(username, featureCodes);
        return features.stream()
                .map(feature -> {
                    var dto = featureMapper.toDto(feature);
                    dto.makeFavorite(favoriteFeatures.get(feature.getCode()));
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isFeatureExists(String code) {
        return featureRepository.existsByCode(code);
    }

    @Transactional
    public String createFeature(CreateFeatureCommand cmd) {
        Product product = productRepository.findByCode(cmd.productCode()).orElseThrow();
        Release release = releaseRepository.findByCode(cmd.releaseCode()).orElse(null);
        String code = product.getPrefix() + FEATURE_SEPARATOR + featureRepository.getNextFeatureId();
        var feature = new Feature();
        feature.setProduct(product);
        feature.setRelease(release);
        feature.setCode(code);
        feature.setTitle(cmd.title());
        feature.setDescription(cmd.description());
        feature.setStatus(FeatureStatus.NEW);
        feature.setAssignedTo(cmd.assignedTo());
        feature.setCreatedBy(cmd.createdBy());
        feature.setCreatedAt(Instant.now());
        featureRepository.save(feature);
        eventPublisher.publishFeatureCreatedEvent(feature);

        // Create notifications synchronously
        // Pass createdBy as excludeUser to filter out self-notifications
        // (e.g., when user creates feature and assigns it to themselves)
        createNotificationsForFeature(feature, NotificationEventType.FEATURE_CREATED, "created", cmd.createdBy());

        return code;
    }

    @Transactional
    public void updateFeature(UpdateFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();
        feature.setTitle(cmd.title());
        feature.setDescription(cmd.description());
        if (cmd.releaseCode() != null) {
            Release release = releaseRepository.findByCode(cmd.releaseCode()).orElse(null);
            feature.setRelease(release);
        } else {
            feature.setRelease(null);
        }
        feature.setAssignedTo(cmd.assignedTo());
        feature.setStatus(cmd.status());
        feature.setUpdatedBy(cmd.updatedBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);
        eventPublisher.publishFeatureUpdatedEvent(feature);

        // Create notifications synchronously
        // Pass updatedBy as excludeUser to filter out self-notifications
        // (e.g., when user updates feature they're assigned to)
        createNotificationsForFeature(feature, NotificationEventType.FEATURE_UPDATED, "updated", cmd.updatedBy());
    }

    @Transactional
    public void deleteFeature(DeleteFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();

        // Create notifications before deletion
        // Pass deletedBy as excludeUser to filter out self-notifications
        // (e.g., when user deletes feature they're assigned to or created)
        createNotificationsForFeature(feature, NotificationEventType.FEATURE_DELETED, "deleted", cmd.deletedBy());

        favoriteFeatureRepository.deleteByFeatureCode(cmd.code());
        featureRepository.deleteByCode(cmd.code());
        eventPublisher.publishFeatureDeletedEvent(feature, cmd.deletedBy(), Instant.now());
    }

    private void createNotificationsForFeature(
            Feature feature, NotificationEventType eventType, String action, String excludeUser) {
        FeatureDto featureDto = featureMapper.toDto(feature);
        Set<String> recipients = recipientService.getFeatureNotificationRecipients(featureDto, excludeUser);

        if (recipients.isEmpty()) {
            return;
        }

        // Prepare event details once (same for all recipients)
        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("action", action);
        eventDetails.put("featureCode", feature.getCode());
        eventDetails.put("title", feature.getTitle());
        if (feature.getStatus() != null) {
            eventDetails.put("status", feature.getStatus().name());
        }

        try {
            String eventDetailsJson = objectMapper.writeValueAsString(eventDetails);
            String link = "/features/" + feature.getCode();

            for (String recipientUserId : recipients) {
                notificationService.createNotification(recipientUserId, eventType, eventDetailsJson, link);
                logger.debug("Created notification for user {} about feature {}", recipientUserId, feature.getCode());
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event details for feature {}", feature.getCode(), e);
        }
    }
}
