package com.sivalabs.ft.features;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FeatureServiceApplicationTests {

    @DynamicPropertySource
    static void ensureKafkaTopics(DynamicPropertyRegistry registry) {
        TestcontainersConfiguration.postgres.start();
        TestcontainersConfiguration.kafka.start();
        TestcontainersConfiguration.ensureKafkaTopics();
    }

    @Test
    void contextLoads() {}
}
