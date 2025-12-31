package com.sivalabs.ft.features.api.controllers;

import com.sivalabs.ft.features.domain.NotificationService;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Email Tracking API")
public class EmailTrackingController {
    private static final Logger log = LoggerFactory.getLogger(EmailTrackingController.class);

    private final NotificationService notificationService;

    public EmailTrackingController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{id}/read")
    @Operation(
            summary = "Email read tracking pixel",
            description = "Public endpoint for email read tracking - returns 1x1 transparent GIF",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Notification marked as read",
                        content = @Content(mediaType = "image/gif")),
                @ApiResponse(responseCode = "400", description = "Invalid UUID format"),
                @ApiResponse(responseCode = "404", description = "Notification not found")
            })
    public ResponseEntity<byte[]> trackEmailRead(@PathVariable String id) {
        // First validate UUID format before any processing
        if (!isValidUUID(id)) {
            log.warn("Invalid UUID format for notification tracking: {}", id);
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID notificationId = UUID.fromString(id);

            // Mark notification as read (idempotent operation)
            boolean marked = notificationService.markAsReadByTracking(notificationId);

            if (!marked) {
                log.warn("Notification not found for tracking: {}", notificationId);
                return ResponseEntity.notFound().build();
            }

            log.info("Email read tracking triggered for notification {}", notificationId);

            // Return 1x1 transparent GIF
            byte[] transparentGif = createTransparentGif();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("image/gif"));
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.set("Expires", "0");

            return new ResponseEntity<>(transparentGif, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for notification tracking: {}", id);
            return ResponseEntity.badRequest().build();
        } catch (ResourceNotFoundException e) {
            log.warn("Notification not found for tracking: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error processing email read tracking for notification {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build(); // Don't expose internal errors
        }
    }

    @GetMapping("/empty/read")
    public ResponseEntity<byte[]> trackEmailReadEmptyId() {
        log.warn("Invalid UUID format for notification tracking: (empty)");
        return ResponseEntity.badRequest().build();
    }

    private boolean isValidUUID(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        // Basic UUID format check: 8-4-4-4-12 characters with hyphens
        String uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        return id.matches(uuidPattern);
    }

    /**
     * Creates a 1x1 transparent GIF image
     */
    private byte[] createTransparentGif() {
        // 1x1 transparent GIF in base64: R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7
        // Decoded to bytes
        return new byte[] {
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
            0x00,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            0x21,
            (byte) 0xF9,
            0x04,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x2C,
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
            0x04,
            0x01,
            0x00,
            0x3B
        };
    }
}
