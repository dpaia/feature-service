package com.sivalabs.ft.features.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.domain.FeatureService;
import com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand;
import com.sivalabs.ft.features.domain.entities.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Tests for the Feature event flow.
 * These tests verify that events are published and handled correctly when features are created.
 */
class FeatureEventTests extends AbstractIT {

    @Autowired
    private FeatureService featureService;
    
    @Autowired
    private TestEventListener testEventListener;
    
    @BeforeEach
    void setUp() {
        testEventListener.reset();
    }
    
    /**
     * Test configuration that provides a test event listener to capture events.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }
    
    /**
     * Test event listener that captures FeatureCreatedApplicationEvents for verification.
     */
    static class TestEventListener {
        private final FeatureEventListener delegate = mock(FeatureEventListener.class);
        
        void reset() {
            clearInvocations(delegate);
        }
        
        @EventListener
        public void handleFeatureCreatedEvent(FeatureCreatedApplicationEvent event) {
            delegate.handleFeatureCreatedEvent(event);
        }
        
        public void verifyEventReceived(int times) {
            verify(delegate, times(times)).handleFeatureCreatedEvent(any(FeatureCreatedApplicationEvent.class));
        }
        
        public Feature getCapturedFeature() {
            ArgumentCaptor<FeatureCreatedApplicationEvent> captor = ArgumentCaptor.forClass(FeatureCreatedApplicationEvent.class);
            verify(delegate).handleFeatureCreatedEvent(captor.capture());
            return captor.getValue().getFeature();
        }
    }

    /**
     * Test that verifies a FeatureCreatedApplicationEvent is published when a feature is created
     * via the REST API and that the event listener handles the event.
     */
    @Test
    @WithMockOAuth2User(username = "user")
    void shouldPublishAndHandleEventWhenFeatureCreatedViaAPI() {
        var payload =
                """
            {
                "productCode": "intellij",
                "releaseCode": "IDEA-2023.3.8",
                "title": "Event Test Feature",
                "description": "Testing event publishing and handling",
                "assignedTo": "john.doe"
            }
            """;

        // Create a feature via the REST API
        var result = mvc.post()
                .uri("/api/features")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        
        // Verify that the event listener was called
        testEventListener.verifyEventReceived(1);
        
        // Verify feature details from the event
        Feature feature = testEventListener.getCapturedFeature();
        assertThat(feature).isNotNull();
        assertThat(feature.getTitle()).isEqualTo("Event Test Feature");
    }
    
    /**
     * Test that verifies a FeatureCreatedApplicationEvent is published when a feature is created
     * directly via the FeatureService and that the event listener handles the event.
     */
    @Test
    void shouldPublishAndHandleEventWhenFeatureCreatedViaService() {
        // Create a feature via the service
        CreateFeatureCommand command = new CreateFeatureCommand(
                "intellij",
                "IDEA-2023.3.8",
                "Service Event Test Feature",
                "Testing event publishing and handling via service",
                "jane.doe",
                "test-user"
        );
        
        String featureCode = featureService.createFeature(command);
        assertThat(featureCode).isNotNull();
        
        // Verify that the event listener was called
        testEventListener.verifyEventReceived(1);
        
        // Verify feature details from the event
        Feature feature = testEventListener.getCapturedFeature();
        assertThat(feature).isNotNull();
        assertThat(feature.getTitle()).isEqualTo("Service Event Test Feature");
    }
}