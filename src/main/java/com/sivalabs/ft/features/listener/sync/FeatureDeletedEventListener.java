package com.sivalabs.ft.features.listener.sync;

import com.sivalabs.ft.features.domain.events.FeatureDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FeatureDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FeatureDeletedEventListener.class);

    @Transactional
    @KafkaListener(topics = "${ft.events.deleted-features}", groupId = "feature-group")
    public void handleFeatureDeletedEvent(FeatureDeletedEvent event) {
        log.info(
                "FeatureDeletedEvent processed successfully: featureId={}, deletedBy={}",
                event.id(),
                event.deletedBy());
    }
}
