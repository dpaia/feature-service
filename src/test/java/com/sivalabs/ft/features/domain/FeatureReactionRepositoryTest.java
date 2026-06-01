package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.FeatureReaction;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.ReactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class FeatureReactionRepositoryTest {

    @Autowired
    private FeatureReactionRepository featureReactionRepository;

    @Autowired
    private FeatureRepository featureRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    private Feature testFeature;
    
    @BeforeEach
    void setUp() {
        // Create a product
        Product product = new Product();
        product.setCode("test-product");
        product.setPrefix("TEST");
        product.setName("Test Product");
        product.setImageUrl("https://example.com/test-image.png");
        product.setCreatedBy("test-user");
        product.setCreatedAt(Instant.now());
        Product savedProduct = productRepository.save(product);
        
        // Create a feature
        Feature feature = new Feature();
        feature.setCode("TEST-1");
        feature.setTitle("Test Feature");
        feature.setStatus(FeatureStatus.NEW);
        feature.setCreatedBy("test-user");
        feature.setCreatedAt(Instant.now());
        feature.setProduct(savedProduct);
        testFeature = featureRepository.save(feature);
    }

    @Test
    void testCreateFeatureReaction() {
        FeatureReaction reaction = new FeatureReaction();
        reaction.setFeature(testFeature);
        reaction.setUserId("s.v.");
        reaction.setReactionType(ReactionType.LIKE);
        reaction.setCreatedAt(Instant.now());

        FeatureReaction savedReaction = featureReactionRepository.save(reaction);

        assertThat(savedReaction.getId()).isNotNull();
        assertThat(savedReaction.getUserId()).isEqualTo("s.v.");
        assertThat(savedReaction.getReactionType()).isEqualTo(ReactionType.LIKE);
        assertThat(savedReaction.getFeature().getId()).isEqualTo(testFeature.getId());
    }
    
    @Test
    void testUpdateFeatureReaction() {
        FeatureReaction reaction = new FeatureReaction();
        reaction.setFeature(testFeature);
        reaction.setUserId("s.v.");
        reaction.setReactionType(ReactionType.LIKE);
        reaction.setCreatedAt(Instant.now());
        
        FeatureReaction savedReaction = featureReactionRepository.save(reaction);

        savedReaction.setReactionType(ReactionType.DISLIKE);
        FeatureReaction updatedReaction = featureReactionRepository.save(savedReaction);

        assertThat(updatedReaction.getId()).isEqualTo(savedReaction.getId());
        assertThat(updatedReaction.getReactionType()).isEqualTo(ReactionType.DISLIKE);
        assertThat(updatedReaction.getUpdatedAt()).isNotNull();
    }
    
    @Test
    void testDeleteFeatureReaction() {
        FeatureReaction reaction = new FeatureReaction();
        reaction.setFeature(testFeature);
        reaction.setUserId("s.v.");
        reaction.setReactionType(ReactionType.LIKE);
        reaction.setCreatedAt(Instant.now());
        
        FeatureReaction savedReaction = featureReactionRepository.save(reaction);
        Long reactionId = savedReaction.getId();

        featureReactionRepository.delete(savedReaction);

        Optional<FeatureReaction> deletedReaction = featureReactionRepository.findById(reactionId);
        assertThat(deletedReaction).isEmpty();
    }
    
    @Test
    void testFindByFeature() {
        FeatureReaction reaction1 = new FeatureReaction();
        reaction1.setFeature(testFeature);
        reaction1.setUserId("s.v.");
        reaction1.setReactionType(ReactionType.LIKE);
        reaction1.setCreatedAt(Instant.now());
        
        FeatureReaction reaction2 = new FeatureReaction();
        reaction2.setFeature(testFeature);
        reaction2.setUserId("e.z.");
        reaction2.setReactionType(ReactionType.DISLIKE);
        reaction2.setCreatedAt(Instant.now());
        
        featureReactionRepository.save(reaction1);
        featureReactionRepository.save(reaction2);

        List<FeatureReaction> reactions = featureReactionRepository.findByFeature(testFeature);

        assertThat(reactions).hasSize(2);
        assertThat(reactions).extracting(FeatureReaction::getUserId).containsExactlyInAnyOrder("s.v.", "e.z.");
    }
    
    @Test
    void testFindByUserId() {
        FeatureReaction reaction = new FeatureReaction();
        reaction.setFeature(testFeature);
        reaction.setUserId("s.v.");
        reaction.setReactionType(ReactionType.LIKE);
        reaction.setCreatedAt(Instant.now());
        
        featureReactionRepository.save(reaction);

        List<FeatureReaction> reactions = featureReactionRepository.findByUserId("s.v.");

        assertThat(reactions).hasSize(1);
        assertThat(reactions.get(0).getReactionType()).isEqualTo(ReactionType.LIKE);
    }
    
    @Test
    void testFindByFeatureAndReactionType() {
        FeatureReaction reaction1 = new FeatureReaction();
        reaction1.setFeature(testFeature);
        reaction1.setUserId("s.v.");
        reaction1.setReactionType(ReactionType.LIKE);
        reaction1.setCreatedAt(Instant.now());
        
        FeatureReaction reaction2 = new FeatureReaction();
        reaction2.setFeature(testFeature);
        reaction2.setUserId("e.z.");
        reaction2.setReactionType(ReactionType.LIKE);
        reaction2.setCreatedAt(Instant.now());
        
        FeatureReaction reaction3 = new FeatureReaction();
        reaction3.setFeature(testFeature);
        reaction3.setUserId("k.a.");
        reaction3.setReactionType(ReactionType.DISLIKE);
        reaction3.setCreatedAt(Instant.now());
        
        featureReactionRepository.save(reaction1);
        featureReactionRepository.save(reaction2);
        featureReactionRepository.save(reaction3);

        List<FeatureReaction> likeReactions = featureReactionRepository.findByFeatureAndReactionType(testFeature, ReactionType.LIKE);

        assertThat(likeReactions).hasSize(2);
        assertThat(likeReactions).extracting(FeatureReaction::getUserId).containsExactlyInAnyOrder("s.v.", "e.z.");
    }
}