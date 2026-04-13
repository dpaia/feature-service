package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.models.UserFavoriteFeature;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteFeatureServiceGetFavoriteFeaturesTest {
    @Mock
    FavoriteFeatureRepository favoriteFeatureRepository;

    @Mock
    FeatureRepository featureRepository;

    @InjectMocks
    FavoriteFeatureService service;

    @Test
    void shouldReturnFavoritesMapForReturnedFeatures() {
        var featureCodes = Set.of("IDEA-1", "IDEA-2");
        when(favoriteFeatureRepository.findByUserIdAndFeatureCodes("user", featureCodes))
                .thenReturn(List.of(
                        new UserFavoriteFeature(1L, "IDEA-1", false), new UserFavoriteFeature(2L, "IDEA-2", true)));

        Map<String, Boolean> result = service.getFavoriteFeatures("user", featureCodes);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("IDEA-1", false, "IDEA-2", true));
        verify(favoriteFeatureRepository).findByUserIdAndFeatureCodes("user", featureCodes);
        verifyNoInteractions(featureRepository);
    }

    @Test
    void shouldReturnEmptyMapWhenNoFavorites() {
        when(favoriteFeatureRepository.findByUserIdAndFeatureCodes("user", Set.of("X")))
                .thenReturn(List.of());

        Map<String, Boolean> result = service.getFavoriteFeatures("user", Set.of("X"));

        assertThat(result).isEmpty();
        verifyNoInteractions(featureRepository);
    }
}
