package com.sivalabs.ft.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Universal integration tests for application lifecycle failure scenarios and graceful shutdown.
 */
class ApplicationLifecycleFailureTests {

    @BeforeAll
    static void ensureInfrastructure() {
        TestcontainersConfiguration.postgres.start();
        TestcontainersConfiguration.kafka.start();
        createTopicsInBroker(
                TestcontainersConfiguration.kafka.getBootstrapServers(),
                List.of("new_features", "updated_features", "deleted_features"));
    }

    static void createTopicsInBroker(String bootstrapServers, List<String> topicNames) {
        try (AdminClient admin =
                AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            admin.createTopics(topicNames.stream()
                            .map(name -> new NewTopic(name, 1, (short) 1))
                            .toList())
                    .all()
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Topics might already exist, that's fine
        }
    }

    static void waitForKafkaReady(String bootstrapServers, List<String> topicNames) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try (AdminClient admin =
                    AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
                var existing = admin.listTopics().names().get(3, TimeUnit.SECONDS);
                if (existing.containsAll(topicNames)) {
                    return;
                }
            } catch (Exception ignored) {
                // Broker might not be ready yet
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ─── arg helpers ───

    /** Standard args using shared postgres, given kafka bootstrap, standard topic names. */
    private static List<String> args(String kafkaBootstrap, int timeout, String... extra) {
        return args(
                TestcontainersConfiguration.postgres.getJdbcUrl(),
                TestcontainersConfiguration.postgres.getUsername(),
                TestcontainersConfiguration.postgres.getPassword(),
                kafkaBootstrap,
                "new_features",
                "updated_features",
                "deleted_features",
                timeout,
                extra);
    }

    /** Fully parameterised arg builder. */
    private static List<String> args(
            String dbUrl,
            String dbUser,
            String dbPass,
            String kafkaBootstrap,
            String topicNew,
            String topicUpdated,
            String topicDeleted,
            int timeout,
            String... extra) {
        List<String> list = new ArrayList<>(List.of(
                "--spring.config.import=",
                "--spring.datasource.url=" + dbUrl,
                "--spring.datasource.username=" + dbUser,
                "--spring.datasource.password=" + dbPass,
                "--spring.kafka.bootstrap-servers=" + kafkaBootstrap,
                "--spring.kafka.producer.properties.max.block.ms=4000",
                "--server.port=0",
                "--ft.events.new-features=" + topicNew,
                "--ft.events.updated-features=" + topicUpdated,
                "--ft.events.deleted-features=" + topicDeleted,
                "--ft.shutdown.timeout-seconds=" + timeout));
        list.addAll(List.of(extra));
        return list;
    }

    private static ConfigurableApplicationContext start(List<String> argList) {
        return new SpringApplicationBuilder(FeatureServiceApplication.class).run(argList.toArray(String[]::new));
    }

    // ─── helpers ───

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

    @SuppressWarnings("unchecked")
    private static void setPhysicalCloseTimeout(ConfigurableApplicationContext ctx) {
        // Disables automatic flush in producer.close() so the test validates listener-driven flush().
        ProducerFactory<?, ?> raw = ctx.getBean(ProducerFactory.class);
        if (raw instanceof DefaultKafkaProducerFactory<?, ?> dpf) {
            dpf.setPhysicalCloseTimeout(0);
        }
    }

    @SuppressWarnings("unchecked")
    private static void sendMessage(ConfigurableApplicationContext ctx, String value) {
        KafkaTemplate<String, Object> tpl = (KafkaTemplate<String, Object>) ctx.getBean(KafkaTemplate.class);
        tpl.send("new_features", value);
    }

    // ──────────────────── Startup failure tests ────────────────────

    @Test
    void shouldFailStartupWhenDatabaseUnavailable() {
        List<String> a = args(
                "jdbc:postgresql://localhost:1/nonexistent",
                "test",
                "test",
                TestcontainersConfiguration.kafka.getBootstrapServers(),
                "new_features",
                "updated_features",
                "deleted_features",
                10,
                "--spring.datasource.hikari.connection-timeout=3000");

        assertThatThrownBy(() -> start(a).close())
                .as("Startup should fail when database is unavailable")
                .satisfies(e -> assertThat(fullMessageChain(e)).containsPattern("(database|datasource|connection)"));
    }

    @Test
    void shouldFailStartupWhenKafkaTopicsMissing() {
        List<String> a = args(
                TestcontainersConfiguration.postgres.getJdbcUrl(),
                TestcontainersConfiguration.postgres.getUsername(),
                TestcontainersConfiguration.postgres.getPassword(),
                TestcontainersConfiguration.kafka.getBootstrapServers(),
                "nonexistent_topic_abc",
                "nonexistent_topic_def",
                "nonexistent_topic_ghi",
                10);

        assertThatThrownBy(() -> start(a).close())
                .as("Startup should fail when required Kafka topics are missing")
                .satisfies(e -> assertThat(fullMessageChain(e)).containsPattern("(topic|missing)"));
    }

    /** Two of three topics exist — catches partial-check implementations. */
    @Test
    void shouldFailStartupWhenOneKafkaTopicMissing() {
        String p = "partial_" + System.nanoTime() + "_";
        createTopicsInBroker(
                TestcontainersConfiguration.kafka.getBootstrapServers(), List.of(p + "new", p + "updated"));

        List<String> a = args(
                TestcontainersConfiguration.postgres.getJdbcUrl(),
                TestcontainersConfiguration.postgres.getUsername(),
                TestcontainersConfiguration.postgres.getPassword(),
                TestcontainersConfiguration.kafka.getBootstrapServers(),
                p + "new",
                p + "updated",
                p + "deleted",
                10);

        assertThatThrownBy(() -> start(a).close())
                .as("Startup should fail when even one required Kafka topic is missing");
    }

    @Test
    void shouldFailStartupWhenKafkaUnavailable() {
        List<String> a = args(
                "localhost:1",
                10,
                "--spring.kafka.admin.properties.request.timeout.ms=3000",
                "--spring.kafka.admin.properties.default.api.timeout.ms=3000",
                "--spring.kafka.properties.request.timeout.ms=3000",
                "--spring.kafka.properties.default.api.timeout.ms=3000");

        assertThatThrownBy(() -> start(a).close())
                .as("Startup should fail when Kafka broker is unavailable")
                .satisfies(e -> assertThat(fullMessageChain(e))
                        .containsPattern("(kafka|broker|timed.out|timeout|connectivity)"));
    }

    // ──────────────────── Shutdown tests ────────────────────

    @Test
    void shutdownCompletesGracefully() {
        ConfigurableApplicationContext ctx = start(args(TestcontainersConfiguration.kafka.getBootstrapServers(), 10));

        assertThat(ctx.isRunning()).isTrue();

        AtomicInteger closedCount = new AtomicInteger(0);
        ctx.addApplicationListener(e -> {
            if (e instanceof ContextClosedEvent) closedCount.incrementAndGet();
        });

        assertThatCode(ctx::close).doesNotThrowAnyException();
        assertThat(closedCount.get())
                .as("ContextClosedEvent should fire exactly once")
                .isEqualTo(1);
    }

    @Test
    void shutdownFlushesKafkaMessages() {
        String linger = "--spring.kafka.producer.properties.linger.ms=60000";
        ConfigurableApplicationContext ctx =
                start(args(TestcontainersConfiguration.kafka.getBootstrapServers(), 10, linger));

        setPhysicalCloseTimeout(ctx);

        String marker = "flush-verify-" + System.nanoTime();
        sendMessage(ctx, marker);
        ctx.close();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", TestcontainersConfiguration.kafka.getBootstrapServers(),
                "group.id", "flush-verify-" + System.nanoTime(),
                "auto.offset.reset", "earliest",
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer"))) {
            consumer.subscribe(List.of("new_features"));

            boolean found = false;
            long deadline = System.currentTimeMillis() + 8_000;
            while (!found && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> recs = consumer.poll(Duration.ofSeconds(1));
                for (var r : recs) {
                    if (r.value() != null && r.value().contains(marker)) {
                        found = true;
                        break;
                    }
                }
            }
            assertThat(found)
                    .as("Message should be flushed by the shutdown listener. "
                            + "With linger.ms=60000 + physicalCloseTimeout=0, "
                            + "only an explicit flush() delivers the message.")
                    .isTrue();
        }
    }

    /**
     * Two contexts with different shutdown timeouts (1 s vs 8 s) on the same dead Kafka.
     * Verifies:
     *  1. No exception on shutdown
     *  2. Error is logged (TD: "log the error and continue shutdown")
     *  3. ft.shutdown.timeout-seconds is configurable (elapsed_long > elapsed_short * 2)
     *  4. Flush timeout bounded to ~8 s (elapsed_long < 9 s)
     */
    @Test
    void shutdownContinuesWhenKafkaFlushFails() {
        try (KafkaContainer tempKafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))) {
            tempKafka.start();
            createTopicsInBroker(
                    tempKafka.getBootstrapServers(), List.of("new_features", "updated_features", "deleted_features"));
            waitForKafkaReady(
                    tempKafka.getBootstrapServers(), List.of("new_features", "updated_features", "deleted_features"));

            // Disable automatic producer flush to ensure this test validates
            // the listener's flush() call rather than Kafka client's automatic flush.
            String linger = "--spring.kafka.producer.properties.linger.ms=60000";
            ConfigurableApplicationContext ctxShort = start(args(tempKafka.getBootstrapServers(), 1, linger));
            ConfigurableApplicationContext ctxLong = start(args(tempKafka.getBootstrapServers(), 8, linger));

            assertThat(ctxShort.isRunning()).isTrue();
            assertThat(ctxLong.isRunning()).isTrue();

            setPhysicalCloseTimeout(ctxShort);
            setPhysicalCloseTimeout(ctxLong);

            // Buffer messages while Kafka is alive (linger.ms keeps them unsent)
            sendMessage(ctxShort, "flush-fail-short");
            sendMessage(ctxLong, "flush-fail-long");

            // Kill Kafka — flush will block until timeout
            tempKafka.stop();

            ListAppender<ILoggingEvent> appender = null;
            if (LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) instanceof Logger root) {
                @SuppressWarnings("unchecked")
                ListAppender<ILoggingEvent> existing = (ListAppender<ILoggingEvent>) root.getAppender("KAFKA_GUARD");
                if (existing != null) {
                    existing.list.clear();
                    appender = existing;
                }
            }

            // ── short-timeout shutdown (1 s) ──
            long t0 = System.currentTimeMillis();
            assertThatCode(ctxShort::close).doesNotThrowAnyException();
            long elapsedShort = System.currentTimeMillis() - t0;

            // ── long-timeout shutdown (8 s) ──
            long t1 = System.currentTimeMillis();
            assertThatCode(ctxLong::close).doesNotThrowAnyException();
            long elapsedLong = System.currentTimeMillis() - t1;

            // ── Configurable timeout ──
            // Only assert when the short shutdown actually waited (not fail-fast).
            if (elapsedShort > 500) {
                assertThat(elapsedLong)
                        .as(
                                "timeout=8 should take longer than timeout=1, "
                                        + "proving ft.shutdown.timeout-seconds is used. "
                                        + "short=%dms, long=%dms",
                                elapsedShort, elapsedLong)
                        .isGreaterThan(elapsedShort * 2);
            }

            // ── Flush bounded to ~8 s even with total=8 ──
            assertThat(elapsedLong)
                    .as("Flush should be bounded to ~8 s max (elapsed=%dms)", elapsedLong)
                    .isLessThan(9_000);

            // ── Verify error was logged ──
            if (appender != null) {
                List<String> warns = new ArrayList<>(appender.list)
                        .stream()
                                .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
                                .filter(e -> {
                                    String name = e.getLoggerName();
                                    return name != null && name.startsWith("com.sivalabs.ft.features");
                                })
                                .map(ILoggingEvent::getFormattedMessage)
                                .filter(m -> m != null)
                                .toList();
                assertThat(warns)
                        .as("Shutdown should log an error/warning about flush failure")
                        .anyMatch(m -> {
                            String l = m.toLowerCase();
                            return (l.contains("flush") || l.contains("kafka"))
                                    && (l.contains("fail")
                                            || l.contains("error")
                                            || l.contains("timed")
                                            || l.contains("timeout")
                                            || l.contains("exceeded"));
                        });
            }
        }
    }

    /** Shutdown continues when the DB pool is already closed. */
    @Test
    void shutdownContinuesWhenDatabaseAlreadyClosed() {
        ConfigurableApplicationContext ctx = start(args(TestcontainersConfiguration.kafka.getBootstrapServers(), 10));

        assertThat(ctx.isRunning()).isTrue();

        DataSource ds = ctx.getBean(DataSource.class);
        if (ds instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }

        assertThatCode(ctx::close)
                .as("Shutdown should continue when DB pool is already closed")
                .doesNotThrowAnyException();
    }
}
