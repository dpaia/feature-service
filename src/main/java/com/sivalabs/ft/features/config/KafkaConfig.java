package com.sivalabs.ft.features.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Kafka configuration for administrative operations.
 * Provides AdminClient bean for topic management and connectivity verification.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates Kafka AdminClient bean for administrative operations.
     * Used by ApplicationLifecycleListener for connectivity verification and topic checks.
     * Marked as @Lazy to defer creation until actually needed, allowing Kafka container time to start.
     */
    @Bean(destroyMethod = "close")
    @Lazy
    public AdminClient kafkaAdminClient() {
        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000); // 10 seconds timeout
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 30000); // 30 seconds idle timeout

        return AdminClient.create(props);
    }
}
