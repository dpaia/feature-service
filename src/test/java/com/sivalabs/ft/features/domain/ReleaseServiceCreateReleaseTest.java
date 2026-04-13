package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceCreateReleaseTest {
    @Mock
    ReleaseRepository releaseRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    FeatureRepository featureRepository;

    @Mock
    ReleaseMapper releaseMapper;

    @InjectMocks
    ReleaseService service;

    @Test
    void shouldCreateReleaseWithPrefixedCode() {
        var product = new Product();
        product.setPrefix("IDEA");
        when(productRepository.findByCode("intellij")).thenReturn(Optional.of(product));
        var cmd = new CreateReleaseCommand("intellij", "2024.1", "First release", "admin");

        String code = service.createRelease(cmd);

        assertThat(code).isEqualTo("IDEA-2024.1");
        var captor = ArgumentCaptor.forClass(Release.class);
        verify(releaseRepository).save(captor.capture());
        assertThat(captor.getValue().getProduct()).isSameAs(product);
        assertThat(captor.getValue().getCode()).isEqualTo("IDEA-2024.1");
        assertThat(captor.getValue().getDescription()).isEqualTo("First release");
        assertThat(captor.getValue().getStatus()).isEqualTo(ReleaseStatus.DRAFT);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("admin");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldKeepCodeWhenAlreadyPrefixed() {
        var product = new Product();
        product.setPrefix("IDEA");
        when(productRepository.findByCode("intellij")).thenReturn(Optional.of(product));
        var cmd = new CreateReleaseCommand("intellij", "IDEA-2024.1", "Release", "admin");

        String code = service.createRelease(cmd);

        assertThat(code).isEqualTo("IDEA-2024.1");
        var captor = ArgumentCaptor.forClass(Release.class);
        verify(releaseRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("IDEA-2024.1");
    }
}
