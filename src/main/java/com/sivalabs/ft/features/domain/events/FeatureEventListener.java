package com.sivalabs.ft.features.domain.events;

import com.sivalabs.ft.features.domain.FeatureService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FeatureEventListener {
    private static final Logger log = LoggerFactory.getLogger(FeatureEventListener.class);
    private final FeatureService featureService;

    public FeatureEventListener(FeatureService featureService) {
        this.featureService = featureService;
    }

    @KafkaListener(
            topics = {"${ft.events.new-features}", "${ft.events.updated-features}", "${ft.events.deleted-features}"},
            groupId = "${spring.kafka.consumer.group-id}")
    public void onFeatureEvents(ConsumerRecords<String, Object> records) {
        long startTime = System.currentTimeMillis();
        log.info("Received batch of {} events", records.count());

        for (ConsumerRecord<String, Object> record : records) {
            log.info(
                    "Processing event - Topic: {}, Partition: {}, Offset: {}, Payload: {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.value());
            featureService.handleFeatureEvent(record.value());
        }

        long processingTime = System.currentTimeMillis() - startTime;
        log.info("Successfully processed batch of {} events in {} ms", records.count(), processingTime);
    }
}
