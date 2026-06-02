package com.sivalabs.ft.features.domain.events;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class UnreadCountChangedConsumer {
    private final SimpMessagingTemplate messagingTemplate;

    public UnreadCountChangedConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(
            topics = "${ft.events.unread-count}",
            groupId = "${ft.events.unread-count.group-id:${spring.application.name}-${random.uuid}}")
    public void onUnreadCountChanged(UnreadCountChangedEvent event) {
        UnreadCountMessage message = new UnreadCountMessage(event.type(), event.unreadCount());
        messagingTemplate.convertAndSendToUser(event.recipientUserId(), "/queue/notifications", message);
    }
}
