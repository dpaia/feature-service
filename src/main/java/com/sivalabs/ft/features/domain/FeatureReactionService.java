package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.dtos.FeatureReactionDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.FeatureReaction;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.mappers.FeatureReactionMapper;
import com.sivalabs.ft.features.domain.models.ReactionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureReactionService {
    private final FeatureReactionRepository featureReactionRepository;
    private final FeatureRepository featureRepository;
    private final FeatureReactionMapper featureReactionMapper;
    private final FeatureMapper featureMapper;

    public FeatureReactionService(
            FeatureReactionRepository featureReactionRepository,
            FeatureRepository featureRepository,
            FeatureReactionMapper featureReactionMapper,
            FeatureMapper featureMapper) {
        this.featureReactionRepository = featureReactionRepository;
        this.featureRepository = featureRepository;
        this.featureReactionMapper = featureReactionMapper;
        this.featureMapper = featureMapper;
    }

    @Transactional
    public Long addOrUpdateReaction(AddOrUpdateReactionCommand cmd) {
        Optional<Feature> featureOpt = featureRepository.findByCode(cmd.featureCode());
        if (featureOpt.isEmpty()) {
            throw new IllegalArgumentException("Feature not found with code: " + cmd.featureCode());
        }
        Feature feature = featureOpt.get();
        Optional<FeatureReaction> existingReactionOpt =
                featureReactionRepository.findByFeatureAndUserId(feature, cmd.userId());

        if (existingReactionOpt.isPresent()) {
            // Update existing reaction
            FeatureReaction existingReaction = existingReactionOpt.get();
            existingReaction.setReactionType(cmd.reactionType());
            existingReaction.setUpdatedAt(Instant.now());
            FeatureReaction savedReaction = featureReactionRepository.save(existingReaction);
            return savedReaction.getId();
        } else {
            // Create new reaction
            FeatureReaction newReaction = new FeatureReaction(feature, cmd.userId(), cmd.reactionType());
            FeatureReaction savedReaction = featureReactionRepository.save(newReaction);
            return savedReaction.getId();
        }
    }

    @Transactional
    public void removeReaction(RemoveReactionCommand cmd) {
        getFeatureReaction(cmd.featureCode, cmd.userId).ifPresent(featureReactionRepository::delete);
    }

    private Optional<FeatureReaction> getFeatureReaction(final String code, final String userId) {
        Optional<Feature> featureOpt = featureRepository.findByCode(code);
        if (featureOpt.isEmpty()) {
            throw new IllegalArgumentException("Feature not found with code: " + code);
        }

        Feature feature = featureOpt.get();
        Optional<FeatureReaction> reactionOpt = featureReactionRepository.findByFeatureAndUserId(feature, userId);
        return reactionOpt;
    }

    @Transactional(readOnly = true)
    public List<FeatureReactionDto> getReactionsByFeature(String featureCode) {
        Optional<Feature> featureOpt = featureRepository.findByCode(featureCode);
        if (featureOpt.isEmpty()) {
            return List.of();
        }

        List<FeatureReaction> reactions = featureReactionRepository.findByFeature(featureOpt.get());
        return featureReactionMapper.toDtoList(reactions);
    }

    @Transactional(readOnly = true)
    public Optional<FeatureReactionDto> getUserReactionForFeature(String featureCode, String userId) {
        Optional<Feature> featureOpt = featureRepository.findByCode(featureCode);
        if (featureOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<FeatureReaction> reactionOpt =
                featureReactionRepository.findByFeatureAndUserId(featureOpt.get(), userId);
        return reactionOpt.map(featureReactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> getMostLikedFeatures(int limit) {
        List<Feature> allFeatures = featureRepository.findAll();

        // Calculate likes and dislikes for each feature
        Map<Feature, Integer> featureLikeScores = allFeatures.stream()
                .collect(Collectors.toMap(feature -> feature, feature -> calculateLikeScore(feature)));

        // Sort features by like score (likes - dislikes) in descending order
        return featureLikeScores.entrySet().stream()
                .sorted(Map.Entry.<Feature, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> featureMapper.toDto(entry.getKey()))
                .collect(Collectors.toList());
    }

    private int calculateLikeScore(Feature feature) {
        List<FeatureReaction> likes =
                featureReactionRepository.findByFeatureAndReactionType(feature, ReactionType.LIKE);
        List<FeatureReaction> dislikes =
                featureReactionRepository.findByFeatureAndReactionType(feature, ReactionType.DISLIKE);
        return likes.size() - dislikes.size();
    }

    public record AddOrUpdateReactionCommand(String featureCode, String userId, ReactionType reactionType) {}

    public record RemoveReactionCommand(String featureCode, String userId) {}
}
