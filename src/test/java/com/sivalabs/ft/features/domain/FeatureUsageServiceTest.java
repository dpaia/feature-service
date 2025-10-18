package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.domain.dtos.FeatureUsageDto;
import com.sivalabs.ft.features.domain.dtos.UsageStatsDto;
import com.sivalabs.ft.features.domain.entities.FeatureUsage;
import com.sivalabs.ft.features.domain.mappers.FeatureUsageMapper;
import com.sivalabs.ft.features.domain.models.ActionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class FeatureUsageServiceTest {

    @Mock
    private FeatureUsageRepository featureUsageRepository;

    @Mock
    private FeatureUsageMapper featureUsageMapper;

    @Mock
    private com.sivalabs.ft.features.ApplicationProperties applicationProperties;

    private FeatureUsageService featureUsageService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        var usageTracking =
                new com.sivalabs.ft.features.ApplicationProperties.UsageTrackingProperties(true, true, true);
        when(applicationProperties.usageTracking()).thenReturn(usageTracking);
        featureUsageService = new FeatureUsageService(
                featureUsageRepository, featureUsageMapper, objectMapper, applicationProperties);
    }

    @Test
    void shouldLogUsageWithAllParameters() {
        // Given
        String userId = "user123";
        String featureCode = "FEAT-001";
        String productCode = "PROD-001";
        ActionType actionType = ActionType.FEATURE_VIEWED;
        Map<String, Object> context = Map.of("key", "value");
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        // When
        featureUsageService.logUsage(userId, featureCode, productCode, null, actionType, context, ipAddress, userAgent);

        // Then
        ArgumentCaptor<FeatureUsage> captor = ArgumentCaptor.forClass(FeatureUsage.class);
        verify(featureUsageRepository).save(captor.capture());

        FeatureUsage savedUsage = captor.getValue();
        assertThat(savedUsage.getUserId()).isEqualTo(userId);
        assertThat(savedUsage.getFeatureCode()).isEqualTo(featureCode);
        assertThat(savedUsage.getProductCode()).isEqualTo(productCode);
        assertThat(savedUsage.getActionType()).isEqualTo(actionType);
        assertThat(savedUsage.getIpAddress()).isEqualTo(ipAddress);
        assertThat(savedUsage.getUserAgent()).isEqualTo(userAgent);
        assertThat(savedUsage.getContext()).isNotNull();
        assertThat(savedUsage.getTimestamp()).isNotNull();
    }

    @Test
    void shouldLogUsageWithMinimalParameters() {
        // Given
        String userId = "user123";
        String featureCode = "FEAT-001";
        String productCode = "PROD-001";
        ActionType actionType = ActionType.FEATURE_CREATED;

        // When
        featureUsageService.logUsage(userId, featureCode, productCode, actionType);

        // Then
        ArgumentCaptor<FeatureUsage> captor = ArgumentCaptor.forClass(FeatureUsage.class);
        verify(featureUsageRepository).save(captor.capture());

        FeatureUsage savedUsage = captor.getValue();
        assertThat(savedUsage.getUserId()).isEqualTo(userId);
        assertThat(savedUsage.getFeatureCode()).isEqualTo(featureCode);
        assertThat(savedUsage.getProductCode()).isEqualTo(productCode);
        assertThat(savedUsage.getActionType()).isEqualTo(actionType);
        assertThat(savedUsage.getIpAddress()).isNull();
        assertThat(savedUsage.getUserAgent()).isNull();
        assertThat(savedUsage.getContext()).isNull();
    }

    @Test
    void shouldHandleExceptionGracefullyWhenLogging() {
        // Given
        when(featureUsageRepository.save(any())).thenThrow(new RuntimeException("Database error"));

        // When - should not throw exception
        featureUsageService.logUsage("user123", "FEAT-001", "PROD-001", ActionType.FEATURE_VIEWED);

        // Then
        verify(featureUsageRepository).save(any());
    }

    @Test
    void shouldFindUsageEventsByFilters() {
        // Given
        String userId = "user123";
        String featureCode = "FEAT-001";
        ActionType actionType = ActionType.FEATURE_VIEWED;
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();
        Pageable pageable = PageRequest.of(0, 10);

        FeatureUsage usage = new FeatureUsage();
        usage.setUserId(userId);
        usage.setFeatureCode(featureCode);
        usage.setActionType(actionType);

        FeatureUsageDto dto = new FeatureUsageDto(
                1L, userId, featureCode, "PROD-001", null, actionType, Instant.now(), null, null, null);

        Page<FeatureUsage> usagePage = new PageImpl<>(List.of(usage));
        when(featureUsageRepository.findByFilters(userId, featureCode, null, actionType, startDate, endDate, pageable))
                .thenReturn(usagePage);
        when(featureUsageMapper.toDto(usage)).thenReturn(dto);

        // When
        Page<FeatureUsageDto> result = featureUsageService.findUsageEvents(
                userId, featureCode, null, actionType, startDate, endDate, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).userId()).isEqualTo(userId);
        verify(featureUsageRepository)
                .findByFilters(userId, featureCode, null, actionType, startDate, endDate, pageable);
    }

    @Test
    void shouldFindUsageByUserId() {
        // Given
        String userId = "user123";
        Pageable pageable = PageRequest.of(0, 10);

        FeatureUsage usage = new FeatureUsage();
        usage.setUserId(userId);

        FeatureUsageDto dto = new FeatureUsageDto(
                1L, userId, "FEAT-001", "PROD-001", null, ActionType.FEATURE_VIEWED, Instant.now(), null, null, null);

        Page<FeatureUsage> usagePage = new PageImpl<>(List.of(usage));
        when(featureUsageRepository.findByUserId(userId, pageable)).thenReturn(usagePage);
        when(featureUsageMapper.toDto(usage)).thenReturn(dto);

        // When
        Page<FeatureUsageDto> result = featureUsageService.findByUserId(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(featureUsageRepository).findByUserId(userId, pageable);
    }

    @Test
    void shouldGetUsageStats() {
        // Given
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        when(featureUsageRepository.countByTimestampBetween(startDate, endDate)).thenReturn(100L);
        when(featureUsageRepository.countDistinctUsers(startDate, endDate)).thenReturn(2L);
        when(featureUsageRepository.countDistinctFeatures(startDate, endDate)).thenReturn(2L);
        when(featureUsageRepository.countDistinctProducts(startDate, endDate)).thenReturn(3L);
        when(featureUsageRepository.findActionTypeStats(startDate, endDate))
                .thenReturn(List.of(
                        new Object[] {ActionType.FEATURE_VIEWED, 50L}, new Object[] {ActionType.FEATURE_CREATED, 30L}));
        when(featureUsageRepository.findTopFeatures(startDate, endDate))
                .thenReturn(List.of(new Object[] {"FEAT-001", 25L}, new Object[] {"FEAT-002", 20L}));
        when(featureUsageRepository.findTopProducts(startDate, endDate))
                .thenReturn(List.of(new Object[] {"PROD-001", 35L}, new Object[] {"PROD-002", 25L}));
        when(featureUsageRepository.findTopUsers(startDate, endDate))
                .thenReturn(List.of(new Object[] {"user1", 40L}, new Object[] {"user2", 30L}));

        // When
        UsageStatsDto stats = featureUsageService.getUsageStats(null, startDate, endDate);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.totalUsageCount()).isEqualTo(100L);
        assertThat(stats.uniqueUserCount()).isEqualTo(2L);
        assertThat(stats.uniqueFeatureCount()).isEqualTo(2L);
        assertThat(stats.uniqueProductCount()).isEqualTo(3L);
        assertThat(stats.usageByActionType()).hasSize(2);
        assertThat(stats.topFeatures()).hasSize(2);
        assertThat(stats.topProducts()).hasSize(2);
        assertThat(stats.topUsers()).hasSize(2);
    }

    @Test
    void shouldGetTopFeatures() {
        // Given
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();
        int limit = 5;

        when(featureUsageRepository.findTopFeatures(startDate, endDate))
                .thenReturn(List.of(
                        new Object[] {"FEAT-001", 25L}, new Object[] {"FEAT-002", 20L}, new Object[] {"FEAT-003", 15L
                        }));

        // When
        Map<String, Long> topFeatures = featureUsageService.getTopFeatures(startDate, endDate, limit);

        // Then
        assertThat(topFeatures).hasSize(3);
        assertThat(topFeatures.get("FEAT-001")).isEqualTo(25L);
        assertThat(topFeatures.get("FEAT-002")).isEqualTo(20L);
    }

    @Test
    void shouldGetTopUsers() {
        // Given
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();
        int limit = 5;

        when(featureUsageRepository.findTopUsers(startDate, endDate))
                .thenReturn(List.of(new Object[] {"user1", 40L}, new Object[] {"user2", 30L}));

        // When
        Map<String, Long> topUsers = featureUsageService.getTopUsers(startDate, endDate, limit);

        // Then
        assertThat(topUsers).hasSize(2);
        assertThat(topUsers.get("user1")).isEqualTo(40L);
        assertThat(topUsers.get("user2")).isEqualTo(30L);
    }
}
