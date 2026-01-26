package com.sivalabs.ft.features.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@SpringBootTest
public class CustomMulticasterContinueOnErrorTest extends AbstractIT {

    @Autowired
    ApplicationEventPublisher eventPublisher;

    static List<String> executed = new ArrayList<>();

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestEventListenerA testEventListenerA() {
            return new TestEventListenerA();
        }

        @Bean
        public TestEventListenerB testEventListenerB() {
            return new TestEventListenerB();
        }

        @Bean
        public TestEventListenerC testEventListenerC() {
            return new TestEventListenerC();
        }
    }

    static class TestEventListenerA {
        @EventListener
        public void handleTestEvent(String event) {
            executed.add("TestEventA");
        }
    }

    static class TestEventListenerB {
        @EventListener
        public void handleTestEvent(String event) {
            throw new RuntimeException("TestEventB FAILED");
        }
    }

    static class TestEventListenerC {
        @EventListener
        public void handleTestEvent(String event) {
            executed.add("TestEventC");
        }
    }

    @Test
    public void shouldContinueOnErrorTest() {
        executed.clear();

        eventPublisher.publishEvent("Test Event");

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(executed).containsExactlyInAnyOrder("TestEventA", "TestEventC");
                });
    }
}
