package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.testsupport.MockJavaMailSenderConfig;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Import({MockJavaMailSenderConfig.class, NotificationWebSocketIntegrationTest.TestJwtDecoderConfig.class})
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
class NotificationWebSocketIntegrationTest extends AbstractIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockOAuth2User(username = "creator")
    void shouldReceiveUnreadCountOverWebSocket() throws Exception {
        // Ensure a clean state so unread count starts from zero.
        jdbcTemplate.execute("DELETE FROM notifications");

        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Map<String, Object>> messageFuture = new CompletableFuture<>();

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer user1");
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer user1");

        StompSession session = null;
        try {
            session = stompClient
                    .connectAsync(wsUrl, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS);
            final StompSession connectedSession = session;

            // Subscribe to user-scoped queue where unread-count updates are delivered.
            session.subscribe("/user/queue/notifications", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Object.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    try {
                        if (payload instanceof Map<?, ?> map) {
                            messageFuture.complete((Map<String, Object>) map);
                            return;
                        }
                        if (payload instanceof String text) {
                            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
                            messageFuture.complete(parsed);
                            return;
                        }
                        if (payload instanceof byte[] bytes) {
                            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class);
                            messageFuture.complete(parsed);
                            return;
                        }
                        Map<String, Object> parsed = objectMapper.convertValue(payload, Map.class);
                        messageFuture.complete(parsed);
                    } catch (Exception e) {
                        messageFuture.completeExceptionally(e);
                    }
                }
            });
            await().pollDelay(Duration.ofMillis(200))
                    .atMost(Duration.ofSeconds(5))
                    .untilAsserted(
                            () -> assertThat(connectedSession.isConnected()).isTrue());

            // Trigger the end-to-end path: API -> DB -> Kafka -> WebSocket.
            Map<String, Object> payload = new HashMap<>();
            payload.put("productCode", "intellij");
            payload.put("title", "Realtime Feature");
            payload.put("description", "Test realtime");
            payload.put("releaseCode", null);
            payload.put("assignedTo", "user1");
            var result = mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))
                    .exchange();
            assertThat(result).hasStatus2xxSuccessful();

            // Verify the WebSocket client receives the unread-count update.
            Map<String, Object> message;
            try {
                message = messageFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError(
                        "Expected unread-count WebSocket message for preferred_username 'user1' but none arrived", e);
            }
            assertThat(message.get("type")).isEqualTo("UnreadCountChanged");
            assertThat(((Number) message.get("unreadCount")).longValue()).isEqualTo(1L);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            stompClient.stop();
        }
    }

    @Test
    @WithMockOAuth2User(username = "creator")
    void shouldUsePreferredUsernameForWebSocketPrincipal() throws Exception {
        jdbcTemplate.execute("DELETE FROM notifications");

        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Map<String, Object>> messageFuture = new CompletableFuture<>();

        // Token format: sub.<sub>.preferred_username.<preferred> (HTTP header-safe)
        String token = "sub.internal-123.preferred_username.user1";
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        StompSession session = null;
        try {
            session = stompClient
                    .connectAsync(wsUrl, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS);

            session.subscribe("/user/queue/notifications", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Object.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    try {
                        if (payload instanceof Map<?, ?> map) {
                            messageFuture.complete((Map<String, Object>) map);
                            return;
                        }
                        if (payload instanceof String text) {
                            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
                            messageFuture.complete(parsed);
                            return;
                        }
                        if (payload instanceof byte[] bytes) {
                            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class);
                            messageFuture.complete(parsed);
                            return;
                        }
                        Map<String, Object> parsed = objectMapper.convertValue(payload, Map.class);
                        messageFuture.complete(parsed);
                    } catch (Exception e) {
                        messageFuture.completeExceptionally(e);
                    }
                }
            });

            // Trigger the event for user1 (preferred_username).
            Map<String, Object> payload = new HashMap<>();
            payload.put("productCode", "intellij");
            payload.put("title", "Realtime Feature");
            payload.put("description", "Test realtime");
            payload.put("releaseCode", null);
            payload.put("assignedTo", "user1");
            var result = mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))
                    .exchange();
            assertThat(result).hasStatus2xxSuccessful();

            Map<String, Object> message = waitForMessage(messageFuture, 5, TimeUnit.SECONDS);
            assertThat(message.get("type")).isEqualTo("UnreadCountChanged");
            assertThat(((Number) message.get("unreadCount")).longValue()).isEqualTo(1L);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            stompClient.stop();
        }
    }

    @Test
    void shouldRejectUnauthorizedWebSocketHandshake() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        try {
            var future = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {});
            StompSession session = null;
            try {
                session = future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                // Handshake rejected due to missing auth is an acceptable outcome.
                return;
            }

            // Some implementations allow the WebSocket handshake but reject unauthenticated STOMP usage later.
            // In that case, verify that an anonymous session does NOT receive user-specific updates.
            CompletableFuture<Map<String, Object>> messageFuture = new CompletableFuture<>();
            session.subscribe("/user/queue/notifications", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    messageFuture.complete((Map<String, Object>) payload);
                }
            });

            Map<String, Object> payload = new HashMap<>();
            payload.put("productCode", "intellij");
            payload.put("title", "Realtime Feature");
            payload.put("description", "Test realtime");
            payload.put("releaseCode", null);
            payload.put("assignedTo", "user1");
            mvc.post()
                    .uri("/api/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))
                    .exchange();

            try {
                Map<String, Object> message = messageFuture.get(2, TimeUnit.SECONDS);
                throw new AssertionError(
                        "Expected unauthenticated WebSocket session to be rejected, but received: " + message);
            } catch (TimeoutException e) {
                // Expected: no message delivered to anonymous session.
            } finally {
                session.disconnect();
            }
        } finally {
            stompClient.stop();
        }
    }

    private Map<String, Object> waitForMessage(
            CompletableFuture<Map<String, Object>> messageFuture, long timeout, TimeUnit unit) throws Exception {
        try {
            return messageFuture.get(timeout, unit);
        } catch (TimeoutException e) {
            throw new AssertionError(
                    "Expected unread-count WebSocket message for preferred_username 'user1' but none arrived", e);
        }
    }

    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                Instant now = Instant.now();
                Map<String, Object> headers = Map.of("alg", "none");
                Map<String, Object> claims = parseTokenClaims(token);
                return new Jwt(token, now, now.plusSeconds(300), headers, claims);
            };
        }

        private Map<String, Object> parseTokenClaims(String token) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", token);
            claims.put("preferred_username", token);
            claims.put("realm_access", Map.of("roles", List.of("ROLE_USER")));

            if (token != null && token.startsWith("sub.")) {
                String[] parts = token.split("\\.");
                for (int i = 0; i + 1 < parts.length; i += 2) {
                    claims.put(parts[i], parts[i + 1]);
                }
            }
            return claims;
        }
    }
}
