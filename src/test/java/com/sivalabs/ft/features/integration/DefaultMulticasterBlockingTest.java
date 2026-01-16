package com.sivalabs.ft.features.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sivalabs.ft.features.AbstractIT;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

@SpringBootTest
public class DefaultMulticasterBlockingTest extends AbstractIT {

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    ApplicationEventMulticaster multicaster;

    static List<String> executed = new ArrayList<>();

    @TestConfiguration
    public static class TestMulticasterConfig {

        @Bean(name = "applicationEventMulticaster")
        @Primary
        public ApplicationEventMulticaster applicationEventMulticaster() {
            return new SimpleApplicationEventMulticaster();
        }

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
        @Order(1)
        public void handleTestEvent(String event) {
            executed.add("TestEventA");
        }
    }

    static class TestEventListenerB {
        @EventListener
        @Order(2)
        public void handleTestEvent(String event) {
            throw new RuntimeException("TestEventB FAILED");
        }
    }

    static class TestEventListenerC {
        @EventListener
        @Order(3)
        public void handleTestEvent(String event) {
            executed.add("TestEventC");
        }
    }

    @Test
    public void shouldStopOnFailureTest() {
        executed.clear();

        assertThatThrownBy(() -> eventPublisher.publishEvent("Test Event")).isInstanceOf(RuntimeException.class);

        assertThat(executed).containsExactly("TestEventA");
        assertThat(executed).doesNotContain("TestEventC");
    }
}
