package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.ReleaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceDeleteReleaseTest {
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
    void shouldDeleteReleaseAndUnsetFeatures() {
        when(releaseRepository.existsByCode("IDEA-2024.1")).thenReturn(true);

        service.deleteRelease("IDEA-2024.1");

        verify(featureRepository).unsetRelease("IDEA-2024.1");
        verify(releaseRepository).deleteByCode("IDEA-2024.1");
    }

    @Test
    void shouldThrowWhenReleaseNotFound() {
        when(releaseRepository.existsByCode("MISSING")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteRelease("MISSING")).isInstanceOf(ResourceNotFoundException.class);
        verify(featureRepository, never()).unsetRelease(any());
        verify(releaseRepository, never()).deleteByCode(any());
    }
}
