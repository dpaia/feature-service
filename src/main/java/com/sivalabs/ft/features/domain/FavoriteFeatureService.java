package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.FavoriteFeature;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.UserFavoriteFeature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteFeatureService {
    private final FavoriteFeatureRepository favoriteFeatureRepository;
    private final FeatureRepository featureRepository;

    FavoriteFeatureService(FavoriteFeatureRepository favoriteFeatureRepository, FeatureRepository featureRepository) {
        this.favoriteFeatureRepository = favoriteFeatureRepository;
        this.featureRepository = featureRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getFavoriteFeatures(String userId, Set<String> featureCodes) {
        List<UserFavoriteFeature> favoriteFeatures =
                this.favoriteFeatureRepository.findByUserIdAndFeatureCodes(userId, featureCodes);
        if (favoriteFeatures.isEmpty()) {
            return Map.of();
        }
        Map<String, Boolean> result = new HashMap<>();
        for (UserFavoriteFeature favoriteFeature : favoriteFeatures) {
            result.put(favoriteFeature.code(), favoriteFeature.isFavorite());
        }
        return result;
    }

    public void addFavoriteFeature(String userId, String featureCode) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public void removeFavoriteFeature(String userId, String featureCode) {
        int count = favoriteFeatureRepository.deleteByUserIdAndFeatureCode(userId, featureCode);
        if (count != 1) {
            throw new BadRequestException("Feature is not favorited by the user to remove");
        }
    }
}
