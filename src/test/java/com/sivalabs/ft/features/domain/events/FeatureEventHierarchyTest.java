package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.models.FeatureStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FeatureEventHierarchyTest {

    private AnnotationConfigApplicationContext context;
    
    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @Test
    void testFeatureCreatedEventListeners() {
        // Get the event tracker from the context
        TestEventTracker eventTracker = context.getBean(TestEventTracker.class);
        eventTracker.reset();
        
        // Create and publish a FeatureCreatedEvent
        FeatureCreatedEvent event = new FeatureCreatedEvent(
                1L,
                "TEST-001",
                "Test Feature",
                "A test feature for event demonstration",
                FeatureStatus.NEW,
                "REL-001",
                "test-assignee",
                "test-creator",
                Instant.now()
        );
        
        // Publish the event
        System.out.println("Publishing FeatureCreatedEvent...");
        context.publishEvent(event);
        
        // Get the invocation order
        List<String> invocationOrder = eventTracker.getInvocationOrder();
        
        // Verify the invocation order
        assertEquals(2, invocationOrder.size(), "Should have 2 listener invocations");
        assertEquals("BaseListener-FeatureCreatedEvent", invocationOrder.get(0), "Base listener should be invoked first");
        assertEquals("CreatedListener-FeatureCreatedEvent", invocationOrder.get(1), "Created listener should be invoked second");
        
        // Verify that only the appropriate listeners were invoked
        assertTrue(invocationOrder.stream().noneMatch(s -> s.startsWith("UpdatedListener")), "Updated listener should not be invoked");
        assertTrue(invocationOrder.stream().noneMatch(s -> s.startsWith("DeletedListener")), "Deleted listener should not be invoked");
    }

    @Test
    void testFeatureUpdatedEventListeners() {
        // Get the event tracker from the context
        TestEventTracker eventTracker = context.getBean(TestEventTracker.class);
        eventTracker.reset();
        
        // Create and publish a FeatureUpdatedEvent
        FeatureUpdatedEvent event = new FeatureUpdatedEvent(
                1L,
                "TEST-001",
                "Test Feature",
                "A test feature for event demonstration",
                FeatureStatus.IN_PROGRESS,
                "REL-001",
                "test-assignee",
                "test-creator",
                Instant.now(),
                "test-updater",
                Instant.now()
        );
        
        // Publish the event
        System.out.println("Publishing FeatureUpdatedEvent...");
        context.publishEvent(event);
        
        // Get the invocation order
        List<String> invocationOrder = eventTracker.getInvocationOrder();
        
        // Verify the invocation order
        assertEquals(2, invocationOrder.size(), "Should have 2 listener invocations");
        assertEquals("BaseListener-FeatureUpdatedEvent", invocationOrder.get(0), "Base listener should be invoked first");
        assertEquals("UpdatedListener-FeatureUpdatedEvent", invocationOrder.get(1), "Updated listener should be invoked second");
        
        // Verify that only the appropriate listeners were invoked
        assertTrue(invocationOrder.stream().noneMatch(s -> s.startsWith("CreatedListener")), "Created listener should not be invoked");
        assertTrue(invocationOrder.stream().noneMatch(s -> s.startsWith("DeletedListener")), "Deleted listener should not be invoked");
    }

    @Test
    void testFeatureDeletedEventListeners() {
        // Get the event tracker from the context
        TestEventTracker eventTracker = context.getBean(TestEventTracker.class);
        eventTracker.reset();
        
        // Create and publish a FeatureDeletedEvent
        FeatureDeletedEvent event = new FeatureDeletedEvent(
                1L,
                "TEST-001",
                "Test Feature",
                "A test feature for event demonstration",
                FeatureStatus.RELEASED,
                "REL-001",
                "test-assignee",
                "test-creator",
                Instant.now(),
                "test-updater",
                Instant.now(),
                "test-deleter",
                Instant.now()
        );
        
        // Publish the event
        System.out.println("Publishing FeatureDeletedEvent...");
        context.publishEvent(event);
        
        // Get the invocation order
        List<String> invocationOrder = eventTracker.getInvocationOrder();
        
        // Verify the invocation order
        assertEquals(2, invocationOrder.size(), "Should have 2 listener invocations");
        assertEquals("BaseListener-FeatureDeletedEvent", invocationOrder.get(0), "Base listener should be invoked first");
        assertEquals("DeletedListener-FeatureDeletedEvent", invocationOrder.get(1), "Deleted listener should be invoked second");
        
        // Verify that only the appropriate listeners were invoked
        assertTrue(invocationOrder.stream().noneMatch(s -> s.startsWith("CreatedListener")), "Created listener should not be invoked");
        assertTrue(invocationOrder.stream().noneMatch(s -> s.startsWith("UpdatedListener")), "Updated listener should not be invoked");
    }

    /**
     * Test configuration that defines the event listeners.
     */
    @Configuration
    static class TestConfig {
        @Bean
        public TestEventTracker testEventTracker() {
            return new TestEventTracker();
        }
        
        @Bean
        public TestEventListener testEventListener(TestEventTracker eventTracker) {
            return new TestEventListener(eventTracker);
        }
    }
    
    /**
     * Class that tracks the order of event listener invocations.
     */
    static class TestEventTracker {
        private final List<String> invocationOrder = new ArrayList<>();
        
        public void reset() {
            invocationOrder.clear();
        }
        
        public void recordInvocation(String invocation) {
            invocationOrder.add(invocation);
        }
        
        public List<String> getInvocationOrder() {
            return new ArrayList<>(invocationOrder);
        }
    }
    
    /**
     * Test event listener that records the order of invocations.
     */
    static class TestEventListener {
        private final TestEventTracker eventTracker;
        
        public TestEventListener(TestEventTracker eventTracker) {
            this.eventTracker = eventTracker;
        }
        
        @EventListener
        @Order(1)
        public void handleFeatureEvent(FeatureEvent event) {
            System.out.println("Base listener - Received feature event for feature with ID: " + 
                    event.getId() + ", code: " + event.getCode() + ", type: " + 
                    event.getClass().getSimpleName());
            eventTracker.recordInvocation("BaseListener-" + event.getClass().getSimpleName());
        }
        
        @EventListener
        @Order(2)
        public void handleFeatureCreatedEvent(FeatureCreatedEvent event) {
            System.out.println("Specific listener - Feature created with ID: " + 
                    event.getId() + ", code: " + event.getCode() + ", by: " + 
                    event.getCreatedBy());
            eventTracker.recordInvocation("CreatedListener-" + event.getClass().getSimpleName());
        }
        
        @EventListener
        @Order(2)
        public void handleFeatureUpdatedEvent(FeatureUpdatedEvent event) {
            System.out.println("Specific listener - Feature updated with ID: " + 
                    event.getId() + ", code: " + event.getCode() + ", by: " + 
                    event.getUpdatedBy());
            eventTracker.recordInvocation("UpdatedListener-" + event.getClass().getSimpleName());
        }
        
        @EventListener
        @Order(2)
        public void handleFeatureDeletedEvent(FeatureDeletedEvent event) {
            System.out.println("Specific listener - Feature deleted with ID: " + 
                    event.getId() + ", code: " + event.getCode() + ", by: " + 
                    event.getDeletedBy());
            eventTracker.recordInvocation("DeletedListener-" + event.getClass().getSimpleName());
        }
    }
}