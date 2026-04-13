package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.entities.FavoriteFeature;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteFeatureServiceAddFavoriteFeatureTest {
    @Mock
    FavoriteFeatureRepository favoriteFeatureRepository;

    @Mock
    FeatureRepository featureRepository;

    @InjectMocks
    FavoriteFeatureService service;

    @Test
    void shouldAddFavoriteFeature() {
        var feature = new Feature();
        feature.setId(1L);
        when(featureRepository.findByCode("IDEA-1")).thenReturn(Optional.of(feature));
        when(favoriteFeatureRepository.existsByUserIdAndFeatureId("user", 1L)).thenReturn(false);

        service.addFavoriteFeature("user", "IDEA-1");

        var captor = ArgumentCaptor.forClass(FavoriteFeature.class);
        verify(favoriteFeatureRepository).save(captor.capture());
        assertThat(captor.getValue().getFeatureId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo("user");
    }

    @Test
    void shouldThrowWhenFeatureNotFound() {
        when(featureRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addFavoriteFeature("user", "INVALID")).isInstanceOf(BadRequestException.class);
        verify(favoriteFeatureRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAlreadyFavorited() {
        var feature = new Feature();
        feature.setId(1L);
        when(featureRepository.findByCode("IDEA-1")).thenReturn(Optional.of(feature));
        when(favoriteFeatureRepository.existsByUserIdAndFeatureId("user", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.addFavoriteFeature("user", "IDEA-1")).isInstanceOf(BadRequestException.class);
        verify(favoriteFeatureRepository, never()).save(any());
    }
}
