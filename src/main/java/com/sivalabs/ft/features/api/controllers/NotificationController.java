package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.api.utils.SecurityUtils;
import com.sivalabs.ft.features.domain.NotificationService;
import com.sivalabs.ft.features.domain.dtos.NotificationDto;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications API")
class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    // 1x1 transparent GIF
    private static final byte[] TRANSPARENT_GIF = {
        0x47,
        0x49,
        0x46,
        0x38,
        0x39,
        0x61,
        0x01,
        0x00,
        0x01,
        0x00,
        (byte) 0x80,
        0x00,
        0x00,
        (byte) 0xff,
        (byte) 0xff,
        (byte) 0xff,
        0x00,
        0x00,
        0x00,
        0x21,
        (byte) 0xf9,
        0x04,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x2c,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x01,
        0x00,
        0x00,
        0x02,
        0x02,
        0x44,
        0x01,
        0x00,
        0x3b
    };

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{id}/read")
    @Operation(
            summary = "Email read tracking pixel",
            description =
                    "Marks a notification as read when the tracking pixel is loaded by an email client. No authentication required.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "1x1 transparent GIF",
                        content = @Content(mediaType = "image/gif")),
                @ApiResponse(responseCode = "400", description = "Invalid UUID format"),
                @ApiResponse(responseCode = "404", description = "Notification not found")
            })
    ResponseEntity<byte[]> trackEmailRead(@PathVariable UUID id) {
        notificationService.markAsReadByTrackingPixel(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/gif"))
                .body(TRANSPARENT_GIF);
    }

    @GetMapping("")
    @Operation(
            summary = "Get user notifications",
            description = "Get all notifications for the current user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema = @Schema(implementation = NotificationDto.class)))),
                @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    ResponseEntity<Page<NotificationDto>> getNotifications(Pageable pageable) {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        Page<NotificationDto> notifications = notificationService.getNotificationsForUser(username, pageable);
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    @Operation(
            summary = "Mark notification as read",
            description = "Mark a specific notification as read for the current user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = NotificationDto.class))),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "404", description = "Notification not found or access denied")
            })
    ResponseEntity<NotificationDto> markAsRead(@PathVariable UUID id) {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            NotificationDto notification = notificationService.markAsRead(id, username);
            log.info("User {} marked notification {} as read", username, id);
            return ResponseEntity.ok(notification);
        } catch (ResourceNotFoundException e) {
            log.warn("Failed to mark notification {} as read for user {}: {}", id, username, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/unread")
    @Operation(
            summary = "Mark notification as unread",
            description = "Mark a specific notification as unread for the current user",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = NotificationDto.class))),
                @ApiResponse(responseCode = "401", description = "Unauthorized"),
                @ApiResponse(responseCode = "404", description = "Notification not found or access denied")
            })
    ResponseEntity<NotificationDto> markAsUnread(@PathVariable UUID id) {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            NotificationDto notification = notificationService.markAsUnread(id, username);
            log.info("User {} marked notification {} as unread", username, id);
            return ResponseEntity.ok(notification);
        } catch (ResourceNotFoundException e) {
            log.warn("Failed to mark notification {} as unread for user {}: {}", id, username, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
