package com.sivalabs.ft.features.listener.sync;

import com.sivalabs.ft.features.domain.events.FeatureCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FeatureCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FeatureCreatedEventListener.class);

    @Transactional
    @KafkaListener(topics = "${ft.events.new-features}", groupId = "feature-group")
    public void handleFeatureCreatedEvent(FeatureCreatedEvent event) {
        log.info("FeatureCreatedEvent processed successfully: featureId={}", event.id());
    }
}
