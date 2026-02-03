package com.sivalabs.ft.features.listener.sync;

import com.sivalabs.ft.features.domain.events.FeatureUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FeatureUpdatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FeatureUpdatedEventListener.class);

    @Transactional
    @KafkaListener(topics = "${ft.events.updated-features}", groupId = "feature-group")
    public void handleFeatureUpdatedEvent(FeatureUpdatedEvent event) {
        log.info("FeatureUpdatedEvent processed successfully: featureId={}", event.id());
    }
}
