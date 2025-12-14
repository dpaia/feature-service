package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service to determine notification recipients based on business rules
 * Returns all potential recipients (createdBy + assignedTo), filtering via excludeUser parameter
 */
@Service
public class NotificationRecipientService {
    private static final Logger log = LoggerFactory.getLogger(NotificationRecipientService.class);

    private final FeatureRepository featureRepository;

    public NotificationRecipientService(FeatureRepository featureRepository) {
        this.featureRepository = featureRepository;
    }

    /**
     * Get notification recipients for feature events from DTO
     * Returns createdBy and assignedTo users
     * Use excludeUser parameter to filter out the user who triggered the action
     */
    public Set<String> getFeatureNotificationRecipients(FeatureDto featureDto, String excludeUser) {
        Set<String> recipients = new HashSet<>();

        // Add creator if not excluded
        if (featureDto.createdBy() != null && !featureDto.createdBy().equals(excludeUser)) {
            recipients.add(featureDto.createdBy());
        }

        // Add assignee if not excluded
        if (featureDto.assignedTo() != null && !featureDto.assignedTo().equals(excludeUser)) {
            recipients.add(featureDto.assignedTo());
        }

        log.debug("Feature {} notification recipients (excluding {}): {}", featureDto.code(), excludeUser, recipients);
        return recipients;
    }

    /**
     * Get notification recipients for release cascade notifications
     * Returns all users who have features in the release (createdBy + assignedTo from each feature)
     * Use excludeUser parameter to filter out the user who triggered the action
     */
    public Set<String> getReleaseNotificationRecipients(String releaseCode, String excludeUser) {
        Set<String> recipients = new HashSet<>();

        // Get all features in this release
        List<com.sivalabs.ft.features.domain.entities.Feature> features =
                featureRepository.findByReleaseCode(releaseCode);

        for (var feature : features) {
            // Add creator if not excluded
            if (feature.getCreatedBy() != null && !feature.getCreatedBy().equals(excludeUser)) {
                recipients.add(feature.getCreatedBy());
            }

            // Add assignee if not excluded
            if (feature.getAssignedTo() != null && !feature.getAssignedTo().equals(excludeUser)) {
                recipients.add(feature.getAssignedTo());
            }
        }

        log.debug(
                "Release {} cascade notification recipients (excluding {}): {}", releaseCode, excludeUser, recipients);
        return recipients;
    }
}
