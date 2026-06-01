package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.FeatureReaction;
import com.sivalabs.ft.features.domain.models.ReactionType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureReactionRepository extends ListCrudRepository<FeatureReaction, Long> {
    List<FeatureReaction> findByFeature(Feature feature);
    List<FeatureReaction> findByUserId(String userId);
    List<FeatureReaction> findByFeatureAndReactionType(Feature feature, ReactionType reactionType);
    Optional<FeatureReaction> findByFeatureAndUserId(Feature feature, String userId);
}