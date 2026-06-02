package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.events.EventDeduplicationService;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.models.EventType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
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
    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);
    public static final String FEATURE_SEPARATOR = "-";
    private final FavoriteFeatureService favoriteFeatureService;
    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ProductRepository productRepository;
    private final FavoriteFeatureRepository favoriteFeatureRepository;
    private final EventPublisher eventPublisher;
    private final EventDeduplicationService eventDeduplicationService;
    private final FeatureMapper featureMapper;

    FeatureService(
            FavoriteFeatureService favoriteFeatureService,
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ProductRepository productRepository,
            FavoriteFeatureRepository favoriteFeatureRepository,
            EventPublisher eventPublisher,
            EventDeduplicationService eventDeduplicationService,
            FeatureMapper featureMapper) {
        this.favoriteFeatureService = favoriteFeatureService;
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.eventDeduplicationService = eventDeduplicationService;
        this.favoriteFeatureRepository = favoriteFeatureRepository;
        this.featureMapper = featureMapper;
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
        // Use EventDeduplicationService for API-level idempotency with result storage
        return eventDeduplicationService.executeIdempotent(cmd.eventId(), EventType.API, () -> {
            // Create new feature
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

            feature = featureRepository.save(feature);

            // Publish event for Kafka listeners
            eventPublisher.publishFeatureCreatedEvent(cmd.eventId(), feature);

            log.info(
                    "Successfully created feature with code: {} for API eventId: {}, event eventId: {}",
                    code,
                    cmd.eventId(),
                    cmd.eventId());

            return code; // Return the feature code directly
        });
    }

    @Transactional
    public void updateFeature(UpdateFeatureCommand cmd) {
        // Use EventDeduplicationService for API-level idempotency
        eventDeduplicationService.executeIdempotent(cmd.eventId(), EventType.API, () -> {
            // Update feature
            Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();
            feature.setTitle(cmd.title());
            feature.setDescription(cmd.description());
            if (cmd.releaseCode() != null) {
                Release release =
                        releaseRepository.findByCode(cmd.releaseCode()).orElse(null);
                feature.setRelease(release);
            } else {
                feature.setRelease(null);
            }
            feature.setAssignedTo(cmd.assignedTo());
            feature.setStatus(cmd.status());
            feature.setUpdatedBy(cmd.updatedBy());
            feature.setUpdatedAt(Instant.now());
            feature = featureRepository.save(feature);

            // Publish event for Kafka listeners
            eventPublisher.publishFeatureUpdatedEvent(cmd.eventId(), feature);

            log.info(
                    "Successfully updated feature with code: {} for API eventId: {}, event eventId: {}",
                    cmd.code(),
                    cmd.eventId(),
                    cmd.eventId());

            return "updated"; // Return simple result for idempotency
        });
    }

    @Transactional
    public void deleteFeature(DeleteFeatureCommand cmd) {
        // Use EventDeduplicationService for API-level idempotency
        eventDeduplicationService.executeIdempotent(cmd.eventId(), EventType.API, () -> {
            // Get feature before deletion
            Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();

            // Delete related data first
            favoriteFeatureRepository.deleteByFeatureCode(cmd.code());

            // Publish event before deleting the feature (while feature still exists)
            eventPublisher.publishFeatureDeletedEvent(cmd.eventId(), feature, cmd.deletedBy(), Instant.now());

            // Delete the feature last
            featureRepository.deleteByCode(cmd.code());

            log.info(
                    "Successfully deleted feature with code: {} for API eventId: {}, event eventId: {}",
                    cmd.code(),
                    cmd.eventId(),
                    cmd.eventId());

            return "deleted"; // Return simple result for idempotency
        });
    }
}
