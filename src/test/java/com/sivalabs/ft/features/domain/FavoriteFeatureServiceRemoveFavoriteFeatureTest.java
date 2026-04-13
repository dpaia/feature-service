package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteFeatureServiceRemoveFavoriteFeatureTest {
    @Mock
    FavoriteFeatureRepository favoriteFeatureRepository;

    @Mock
    FeatureRepository featureRepository;

    @InjectMocks
    FavoriteFeatureService service;

    @Test
    void shouldRemoveFavoriteFeature() {
        when(favoriteFeatureRepository.deleteByUserIdAndFeatureCode("user1", "F-1"))
                .thenReturn(1);

        service.removeFavoriteFeature("user1", "F-1");

        verify(favoriteFeatureRepository).deleteByUserIdAndFeatureCode("user1", "F-1");
    }

    @Test
    void shouldThrowWhenDeleteCountIsNotExactlyOne() {
        when(favoriteFeatureRepository.deleteByUserIdAndFeatureCode("user1", "F-1"))
                .thenReturn(2);

        assertThatThrownBy(() -> service.removeFavoriteFeature("user1", "F-1")).isInstanceOf(BadRequestException.class);
    }
}
