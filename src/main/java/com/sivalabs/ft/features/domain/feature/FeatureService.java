package com.sivalabs.ft.features.domain.feature;

import com.sivalabs.ft.features.domain.product.Product;
import com.sivalabs.ft.features.domain.product.ProductRepository;
import com.sivalabs.ft.features.domain.release.Release;
import com.sivalabs.ft.features.domain.release.ReleaseRepository;
import com.sivalabs.ft.features.integration.EventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
public class FeatureService {
    private final ReleaseRepository releaseRepository;
    private final FeatureRepository featureRepository;
    private final ProductRepository productRepository;
    private final EventPublisher eventPublisher;

    FeatureService(
            ReleaseRepository releaseRepository,
            FeatureRepository featureRepository,
            ProductRepository productRepository,
            EventPublisher eventPublisher) {
        this.releaseRepository = releaseRepository;
        this.featureRepository = featureRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    public Optional<Feature> findFeatureByCode(String code) {
        return featureRepository.findByCode(code);
    }

    @Transactional
    public List<Feature> findFeatures(String releaseCode) {
        List<Feature> featureList = featureRepository.findByReleaseCode(releaseCode);
        return featureList;
    }

    public boolean isFeatureExists(String code) {
        return featureRepository.existsByCode(code);
    }

    @Transactional
    public Long createFeature(CreateFeatureCommand cmd) {
        Release release = releaseRepository.findByCode(cmd.releaseCode()).orElseThrow();
        Product product =
                productRepository.findByCode(release.getProduct().getCode()).orElseThrow();
        var feature = new Feature();
        feature.setProduct(product);
        feature.setRelease(release);
        feature.setCode(cmd.code());
        feature.setTitle(cmd.title());
        feature.setDescription(cmd.description());
        feature.setStatus(FeatureStatus.NEW);
        feature.setAssignedTo(cmd.assignedTo());
        feature.setCreatedBy(cmd.createdBy());
        feature.setCreatedAt(Instant.now());
        featureRepository.save(feature);
        eventPublisher.publishFeatureCreatedEvent(feature);
        return feature.getId();
    }

    @Transactional
    public void updateFeature(UpdateFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();
        feature.setTitle(cmd.title());
        feature.setDescription(cmd.description());
        feature.setAssignedTo(cmd.assignedTo());
        feature.setStatus(cmd.status());
        feature.setUpdatedBy(cmd.updatedBy());
        feature.setUpdatedAt(Instant.now());
        featureRepository.save(feature);
        eventPublisher.publishFeatureUpdatedEvent(feature);
    }

    @Transactional
    public void deleteFeature(DeleteFeatureCommand cmd) {
        Feature feature = featureRepository.findByCode(cmd.code()).orElseThrow();
        featureRepository.deleteByCode(cmd.code());
        eventPublisher.publishFeatureDeletedEvent(feature, cmd.deletedBy(), Instant.now());
    }
}
