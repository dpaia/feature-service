package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class FeatureRepositoryTest {

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setCode("release-chain-product");
        product.setPrefix("RCP");
        product.setName("Release Chain Product");
        product.setImageUrl("https://example.com/release-chain-product.png");
        product.setCreatedBy("tester");
        product.setCreatedAt(Instant.now());
        product = productRepository.save(product);
    }

    @Test
    void shouldFindFeaturesByReleaseCodeWithParentsUsingNativeQuery() {
        Release root = createRelease("RCP-2026.1", null);
        Release minor = createRelease("RCP-2026.1.1", root);
        Release patch = createRelease("RCP-2026.1.2", minor);
        Release unrelated = createRelease("RCP-2027.1", null);

        createFeature("RCP-1", root);
        createFeature("RCP-2", minor);
        createFeature("RCP-3", patch);
        createFeature("RCP-4", unrelated);

        assertThat(featureRepository.findByReleaseCodeWithParents("RCP-2026.1.2"))
                .extracting(Feature::getCode)
                .containsExactlyInAnyOrder("RCP-1", "RCP-2", "RCP-3");
    }

    private Release createRelease(String code, Release parent) {
        Release release = new Release();
        release.setProduct(product);
        release.setParent(parent);
        release.setCode(code);
        release.setDescription(code);
        release.setStatus(ReleaseStatus.DRAFT);
        release.setCreatedBy("tester");
        release.setCreatedAt(Instant.now());
        return releaseRepository.save(release);
    }

    private void createFeature(String code, Release release) {
        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setRelease(release);
        feature.setCode(code);
        feature.setTitle(code);
        feature.setStatus(FeatureStatus.NEW);
        feature.setCreatedBy("tester");
        feature.setCreatedAt(Instant.now());
        featureRepository.save(feature);
    }
}
