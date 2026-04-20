package com.sivalabs.ft.features.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.config.event.ResilientApplicationEventMulticaster;
import com.sivalabs.ft.features.config.event.ResilientEventConfig;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

@SpringBootTest
@Import(ResilientEventConfig.class)
public class CustomMulticasterContinueOnErrorTest extends AbstractIT {

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    ApplicationEventMulticaster multicaster;

    @Autowired
    List<String> executed;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestEventListenerA testEventListenerA(List<String> executed) {
            return new TestEventListenerA(executed);
        }

        @Bean
        public TestEventListenerB testEventListenerB(List<String> executed) {
            return new TestEventListenerB(executed);
        }

        @Bean
        public TestEventListenerC testEventListenerC(List<String> executed) {
            return new TestEventListenerC(executed);
        }

        @Bean
        public List<String> executed() {
            return new CopyOnWriteArrayList<>();
        }
    }

    static class TestEventListenerA {
        private final List<String> executed;

        TestEventListenerA(List<String> executed) {
            this.executed = executed;
        }

        @EventListener
        @Order(1)
        public void handleTestEvent(String event) {
            executed.add("TestEventA");
        }
    }

    static class TestEventListenerB {
        private final List<String> executed;

        TestEventListenerB(List<String> executed) {
            this.executed = executed;
        }

        @Async("asyncEventExecutor")
        @EventListener
        @Order(2)
        public void handleTestEvent(String event) {
            throw new RuntimeException("TestEventB FAILED");
        }
    }

    static class TestEventListenerC {
        private final List<String> executed;

        TestEventListenerC(List<String> executed) {
            this.executed = executed;
        }

        @EventListener
        @Order(3)
        public void handleTestEvent(String event) {
            executed.add("TestEventC");
        }
    }

    @BeforeEach
    void setup() {
        executed.clear();
    }

    @Test
    public void shouldContinueOnErrorTest() {

        assertThat(multicaster).isInstanceOf(ResilientApplicationEventMulticaster.class);

        eventPublisher.publishEvent("Test Event");

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(executed).containsExactlyInAnyOrder("TestEventA", "TestEventC");
                });
    }
}
