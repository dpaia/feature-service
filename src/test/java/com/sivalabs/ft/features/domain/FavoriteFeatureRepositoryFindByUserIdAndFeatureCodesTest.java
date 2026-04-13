package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.domain.models.UserFavoriteFeature;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FavoriteFeatureRepositoryFindByUserIdAndFeatureCodesTest extends AbstractIT {
    @Autowired
    FavoriteFeatureRepository favoriteFeatureRepository;

    @Test
    void shouldReturnFavoriteStatusForFeatureCodes() {
        List<UserFavoriteFeature> results =
                favoriteFeatureRepository.findByUserIdAndFeatureCodes("user", Set.of("IDEA-1", "IDEA-2"));
        assertThat(results)
                .extracting(UserFavoriteFeature::code, UserFavoriteFeature::isFavorite)
                .containsExactlyInAnyOrder(tuple("IDEA-1", false), tuple("IDEA-2", true));
    }

    @Test
    void shouldReturnResultsEvenWithNoFavorites() {
        List<UserFavoriteFeature> results =
                favoriteFeatureRepository.findByUserIdAndFeatureCodes("unknown-user", Set.of("IDEA-1"));
        assertThat(results)
                .extracting(UserFavoriteFeature::code, UserFavoriteFeature::isFavorite)
                .containsExactly(tuple("IDEA-1", false));
    }
}
