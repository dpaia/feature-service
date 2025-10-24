package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Tag;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldSaveTag() {
        // given
        Tag tag = new Tag();
        tag.setName("performance");
        tag.setDescription("Performance related features");
        tag.setCreatedBy("admin");
        tag.setCreatedAt(Instant.now());

        // when
        Tag savedTag = tagRepository.save(tag);

        // then
        assertThat(savedTag.getId()).isNotNull();
        assertThat(savedTag.getName()).isEqualTo("performance");
        assertThat(savedTag.getDescription()).isEqualTo("Performance related features");
        assertThat(savedTag.getCreatedBy()).isEqualTo("admin");
        assertThat(savedTag.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindTagById() {
        // given
        Tag tag = new Tag();
        tag.setName("bug");
        tag.setDescription("Bug fixes");
        tag.setCreatedBy("admin");
        tag.setCreatedAt(Instant.now());
        Tag savedTag = tagRepository.save(tag);

        // when
        Optional<Tag> foundTag = tagRepository.findById(savedTag.getId());

        // then
        assertThat(foundTag).isPresent();
        assertThat(foundTag.get().getName()).isEqualTo("bug");
    }

    @Test
    void shouldUpdateTag() {
        // given
        Tag tag = new Tag();
        tag.setName("enhancement");
        tag.setDescription("Feature enhancements");
        tag.setCreatedBy("admin");
        tag.setCreatedAt(Instant.now());
        Tag savedTag = tagRepository.save(tag);

        // when
        savedTag.setDescription("Updated description");
        Tag updatedTag = tagRepository.save(savedTag);

        // then
        assertThat(updatedTag.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void shouldDeleteTag() {
        // given
        Tag tag = new Tag();
        tag.setName("refactor");
        tag.setDescription("Code refactoring");
        tag.setCreatedBy("admin");
        tag.setCreatedAt(Instant.now());
        Tag savedTag = tagRepository.save(tag);

        // when
        tagRepository.deleteById(savedTag.getId());
        Optional<Tag> foundTag = tagRepository.findById(savedTag.getId());

        // then
        assertThat(foundTag).isEmpty();
    }

    @Test
    void shouldAssociateTagsWithFeature() {
        // given
        Tag tag1 = new Tag();
        tag1.setName("security");
        tag1.setDescription("Security related features");
        tag1.setCreatedBy("admin");
        tag1.setCreatedAt(Instant.now());
        tagRepository.save(tag1);

        Tag tag2 = new Tag();
        tag2.setName("ui");
        tag2.setDescription("UI related features");
        tag2.setCreatedBy("admin");
        tag2.setCreatedAt(Instant.now());
        tagRepository.save(tag2);

        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("TEST-001");
        feature.setTitle("Test Feature");
        feature.setDescription("Test Description");
        feature.setStatus(FeatureStatus.NEW);
        feature.setCreatedBy("admin");
        feature.setCreatedAt(Instant.now());
        feature.getTags().add(tag1);
        feature.getTags().add(tag2);

        // when
        Feature savedFeature = featureRepository.save(feature);
        entityManager.flush();
        entityManager.clear();

        // then
        Feature retrievedFeature =
                featureRepository.findById(savedFeature.getId()).orElseThrow();
        assertThat(retrievedFeature.getTags()).hasSize(2);
        assertThat(retrievedFeature.getTags()).extracting("name").containsExactlyInAnyOrder("security", "ui");
    }

    @Test
    void shouldFindFeaturesByTag() {
        // given
        Tag tag = new Tag();
        tag.setName("api");
        tag.setDescription("API related features");
        tag.setCreatedBy("admin");
        tag.setCreatedAt(Instant.now());
        Tag savedTag = tagRepository.save(tag);

        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature1 = new Feature();
        feature1.setProduct(product);
        feature1.setCode("API-001");
        feature1.setTitle("API Feature 1");
        feature1.setDescription("API Description 1");
        feature1.setStatus(FeatureStatus.NEW);
        feature1.setCreatedBy("admin");
        feature1.setCreatedAt(Instant.now());
        feature1.getTags().add(savedTag);
        featureRepository.save(feature1);

        Feature feature2 = new Feature();
        feature2.setProduct(product);
        feature2.setCode("API-002");
        feature2.setTitle("API Feature 2");
        feature2.setDescription("API Description 2");
        feature2.setStatus(FeatureStatus.NEW);
        feature2.setCreatedBy("admin");
        feature2.setCreatedAt(Instant.now());
        feature2.getTags().add(savedTag);
        featureRepository.save(feature2);

        Feature feature3 = new Feature();
        feature3.setProduct(product);
        feature3.setCode("UI-001");
        feature3.setTitle("UI Feature");
        feature3.setDescription("UI Description");
        feature3.setStatus(FeatureStatus.NEW);
        feature3.setCreatedBy("admin");
        feature3.setCreatedAt(Instant.now());
        featureRepository.save(feature3);

        entityManager.flush();
        entityManager.clear();

        // when
        // Note: In a real application, you would create a custom query method in FeatureRepository
        // For this test, we'll use JPQL directly with the EntityManager
        List<Feature> featuresWithApiTag = entityManager
                .createQuery("SELECT f FROM Feature f JOIN f.tags t WHERE t.id = :tagId", Feature.class)
                .setParameter("tagId", savedTag.getId())
                .getResultList();

        // then
        assertThat(featuresWithApiTag).hasSize(2);
        assertThat(featuresWithApiTag).extracting("code").containsExactlyInAnyOrder("API-001", "API-002");
    }

    @Test
    void shouldRemoveTagFromFeature() {
        // given
        Tag tag = new Tag();
        tag.setName("documentation-test");
        tag.setDescription("Documentation related features");
        tag.setCreatedBy("admin");
        tag.setCreatedAt(Instant.now());
        Tag savedTag = tagRepository.save(tag);

        Product product = productRepository.findByCode("intellij").orElseThrow();

        Feature feature = new Feature();
        feature.setProduct(product);
        feature.setCode("DOC-001");
        feature.setTitle("Documentation Feature");
        feature.setDescription("Documentation Description");
        feature.setStatus(FeatureStatus.NEW);
        feature.setCreatedBy("admin");
        feature.setCreatedAt(Instant.now());
        feature.getTags().add(savedTag);

        Feature savedFeature = featureRepository.save(feature);
        entityManager.flush();
        entityManager.clear();

        // when
        Feature retrievedFeature =
                featureRepository.findById(savedFeature.getId()).orElseThrow();
        retrievedFeature.getTags().clear();
        featureRepository.save(retrievedFeature);
        entityManager.flush();
        entityManager.clear();

        // then
        Feature updatedFeature =
                featureRepository.findById(savedFeature.getId()).orElseThrow();
        assertThat(updatedFeature.getTags()).isEmpty();
    }
}
