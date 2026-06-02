package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.NotificationRepository;
import com.sivalabs.ft.features.domain.entities.Notification;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint for tracking email opens via tracking pixel.
 * This endpoint is accessible without authentication.
 */
@RestController
class NotificationTrackingController {
    private static final Logger log = LoggerFactory.getLogger(NotificationTrackingController.class);

    // 1x1 transparent GIF (base64 encoded)
    private static final byte[] TRANSPARENT_GIF =
            Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final NotificationRepository notificationRepository;

    NotificationTrackingController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/notifications/{id}/read")
    @Transactional
    public ResponseEntity<byte[]> trackRead(@PathVariable String id) {
        UUID notificationId;
        try {
            notificationId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid notification ID format");
        }

        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        // Idempotent: only update if not already read
        if (!Boolean.TRUE.equals(notification.getRead())) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
            log.info("Notification {} marked as read via tracking pixel", notificationId);
        } else {
            log.debug("Notification {} already read, skipping update", notificationId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_GIF);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);

        return new ResponseEntity<>(TRANSPARENT_GIF, headers, HttpStatus.OK);
    }
}
