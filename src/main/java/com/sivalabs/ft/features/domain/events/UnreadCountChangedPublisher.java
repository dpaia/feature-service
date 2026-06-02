package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class UnreadCountChangedPublisher {
    private static final Logger log = LoggerFactory.getLogger(UnreadCountChangedPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationProperties properties;

    public UnreadCountChangedPublisher(KafkaTemplate<String, Object> kafkaTemplate, ApplicationProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publish(String recipientUserId, long unreadCount) {
        UnreadCountChangedEvent event = UnreadCountChangedEvent.of(recipientUserId, unreadCount);
        kafkaTemplate.send(properties.events().unreadCount(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Failed to publish unread count event for user {}", recipientUserId, ex);
            }
        });
    }
}
