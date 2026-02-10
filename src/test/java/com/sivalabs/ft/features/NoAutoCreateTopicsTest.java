package com.sivalabs.ft.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class NoAutoCreateTopicsTest {

    // This test uses a dedicated KafkaContainer with auto-create disabled so we can
    // verify that the application does not auto-create Kafka topics on startup

    @Container
    private static final KafkaContainer strictKafka = new KafkaContainer(
                    DockerImageName.parse("apache/kafka-native:3.8.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @BeforeAll
    static void startContainers() {
        TestcontainersConfiguration.postgres.start();
    }

    @Test
    void shouldNotCreateTopicsAutomatically() {
        Set<String> before = listTopics(strictKafka.getBootstrapServers());

        List<String> args = args(
                TestcontainersConfiguration.postgres,
                strictKafka.getBootstrapServers(),
                "new_features",
                "updated_features",
                "deleted_features",
                10);

        assertThatThrownBy(() -> start(args).close())
                .as("Startup must fail when required topics are missing and auto-create is disabled")
                .satisfies(e ->
                        assertThat(fullMessageChain(e)).containsPattern("(topic|missing|unknown|metadata|timeout)"));

        Set<String> after = listTopics(strictKafka.getBootstrapServers());

        assertThat(after)
                .as("Application must NOT create Kafka topics automatically")
                .doesNotContain("new_features", "updated_features", "deleted_features");

        Set<String> newTopics = new HashSet<>(after);
        newTopics.removeAll(before);
        newTopics.removeAll(Set.of("__consumer_offsets"));
        assertThat(newTopics).as("Unexpected topics created: " + newTopics).isEmpty();
    }

    private static List<String> args(
            PostgreSQLContainer<?> postgres,
            String kafkaBootstrap,
            String topicNew,
            String topicUpdated,
            String topicDeleted,
            int timeout) {
        return new ArrayList<>(List.of(
                "--spring.config.import=",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.kafka.bootstrap-servers=" + kafkaBootstrap,
                "--spring.kafka.producer.properties.max.block.ms=4000",
                "--server.port=0",
                "--ft.events.new-features=" + topicNew,
                "--ft.events.updated-features=" + topicUpdated,
                "--ft.events.deleted-features=" + topicDeleted,
                "--ft.shutdown.timeout-seconds=" + timeout));
    }

    private static ConfigurableApplicationContext start(List<String> argList) {
        return new SpringApplicationBuilder(FeatureServiceApplication.class).run(argList.toArray(String[]::new));
    }

    private static Set<String> listTopics(String bootstrapServers) {
        long deadline = System.currentTimeMillis() + 10_000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try (AdminClient adminClient = AdminClient.create(new java.util.HashMap<>(
                    java.util.Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)))) {
                return adminClient.listTopics().names().get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for Kafka readiness", ie);
                }
            }
        }
        throw new RuntimeException("Failed to list Kafka topics within timeout", last);
    }

    private static String fullMessageChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(" ");
            }
            t = t.getCause();
        }
        return sb.toString().toLowerCase();
    }
}
