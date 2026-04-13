package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.events.EventPublisher;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureServiceCreateFeatureTest {
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
    void shouldCreateFeatureAndPublishEvent() {
        var product = new Product();
        product.setPrefix("IDEA");
        when(productRepository.findByCode("intellij")).thenReturn(Optional.of(product));
        var release = new Release();
        release.setCode("IDEA-2024.1");
        when(releaseRepository.findByCode("IDEA-2024.1")).thenReturn(Optional.of(release));
        when(featureRepository.getNextFeatureId()).thenReturn(42L);

        var cmd = new CreateFeatureCommand("intellij", "IDEA-2024.1", "Title", "Desc", "user1", "admin");
        String code = service.createFeature(cmd);

        assertThat(code).isEqualTo("IDEA-42");
        var captor = ArgumentCaptor.forClass(Feature.class);
        verify(featureRepository).save(captor.capture());
        assertThat(captor.getValue().getProduct()).isSameAs(product);
        assertThat(captor.getValue().getRelease()).isSameAs(release);
        assertThat(captor.getValue().getCode()).isEqualTo("IDEA-42");
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        assertThat(captor.getValue().getDescription()).isEqualTo("Desc");
        assertThat(captor.getValue().getStatus()).isEqualTo(FeatureStatus.NEW);
        assertThat(captor.getValue().getAssignedTo()).isEqualTo("user1");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("admin");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        verify(eventPublisher).publishFeatureCreatedEvent(same(captor.getValue()));
    }

    @Test
    void shouldCreateFeatureWithoutReleaseWhenReleaseCodeIsUnknown() {
        var product = new Product();
        product.setPrefix("IDEA");
        when(productRepository.findByCode("intellij")).thenReturn(Optional.of(product));
        when(releaseRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());
        when(featureRepository.getNextFeatureId()).thenReturn(43L);

        var cmd = new CreateFeatureCommand("intellij", "UNKNOWN", "Title", "Desc", "user1", "admin");
        String code = service.createFeature(cmd);

        assertThat(code).isEqualTo("IDEA-43");
        var captor = ArgumentCaptor.forClass(Feature.class);
        verify(featureRepository).save(captor.capture());
        assertThat(captor.getValue().getRelease()).isNull();
        verify(eventPublisher).publishFeatureCreatedEvent(same(captor.getValue()));
    }
}
