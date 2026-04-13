package com.sivalabs.ft.features.domain;

import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureServiceDeleteFeatureTest {
    @Mock
    FavoriteFeatureService favoriteFeatureService;

    @Mock
    ReleaseRepository releaseRepository;

    @Mock
    FeatureRepository featureRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    FavoriteFeatureRepository favoriteFeatureRepository;

    @Mock
    EventPublisher eventPublisher;

    @Mock
    FeatureMapper featureMapper;

    @InjectMocks
    FeatureService service;

    @Test
    void shouldDeleteFeatureAndPublishEvent() {
        var feature = mock(Feature.class);
        when(featureRepository.findByCode("IDEA-1")).thenReturn(Optional.of(feature));
        var cmd = new DeleteFeatureCommand("IDEA-1", "admin");

        service.deleteFeature(cmd);

        verify(favoriteFeatureRepository).deleteByFeatureCode("IDEA-1");
        verify(featureRepository).deleteByCode("IDEA-1");
        verify(eventPublisher).publishFeatureDeletedEvent(eq(feature), eq("admin"), any());
    }
}
