package com.sivalabs.ft.features.listener.async;

import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FeatureAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(FeatureAuditEventListener.class);

    @Async("asyncEventExecutor")
    @Transactional
    @KafkaListener(topics = "${ft.events.new-features}", groupId = "feature-audit-group")
    public void onCreated(FeatureCreatedEvent event) {
        if (event.id().equals(998L)) {
            throw new RuntimeException("Simulated exception for testing event handling");
        }
        log.info("Audit created: featureId={}", event.id());
    }

    @Async("asyncEventExecutor")
    @Transactional
    @KafkaListener(topics = "${ft.events.updated-features}", groupId = "feature-audit-group")
    public void onUpdated(FeatureUpdatedEvent event) {
        log.info("Audit updated: featureId={}", event.id());
    }

    @Async("asyncEventExecutor")
    @Transactional
    @KafkaListener(topics = "${ft.events.deleted-features}", groupId = "feature-audit-group")
    public void onDeleted(FeatureDeletedEvent event) {
        log.info("Audit deleted: featureId={}, deletedBy={}", event.id(), event.deletedBy());
    }
}
