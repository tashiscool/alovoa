package com.nonononoki.alovoa.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nonononoki.alovoa.entity.CaptureSession.CaptureType;
import com.nonononoki.alovoa.service.CaptureService;

/**
 * REST controller for web-based video/screen capture (Tier A).
 *
 * Flow:
 * 1. POST /sessions - Create session, get presigned PUT URL
 * 2. Client uploads directly to S3 using PUT URL
 * 3. POST /sessions/{id}/confirm - Verify upload, trigger processing
 * 4. GET /sessions/{id}/status - Poll for processing status
 * 5. GET /sessions/{id}/playback - Get presigned playback URL
 */
@RestController
@RequestMapping("/api/v1/capture")
public class CaptureController {

    @Autowired
    private CaptureService captureService;

    /**
     * Create a new capture session.
     * Returns captureId, s3Key, presigned PUT URL, and limits.
     *
     * @param captureType Type of capture (SCREEN_MIC, WEBCAM_MIC, etc.)
     */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestParam(value = "type", required = false) String captureType) {
        try {
            CaptureType type = null;
            if (captureType != null && !captureType.isEmpty()) {
                try {
                    type = CaptureType.valueOf(captureType.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid capture type",
                        "validTypes", CaptureType.values()
                    ));
                }
            }

            Map<String, Object> result = captureService.createSession(type);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create session"));
        }
    }

    /**
     * Get a fresh presigned upload URL for an existing session.
     * Useful if the original URL expired before upload completed.
     */
    @PostMapping("/sessions/{captureId}/refresh-url")
    public ResponseEntity<Map<String, Object>> refreshUploadUrl(@PathVariable String captureId) {
        try {
            UUID uuid = UUID.fromString(captureId);
            Map<String, Object> result = captureService.refreshUploadUrl(uuid);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to refresh URL"));
        }
    }

    /**
     * Confirm upload completion.
     * Called by client after successful S3 PUT.
     * Verifies file exists and validates size/type.
     */
    @PostMapping("/sessions/{captureId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmUpload(@PathVariable String captureId) {
        try {
            UUID uuid = UUID.fromString(captureId);
            Map<String, Object> result = captureService.confirmUpload(uuid);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to confirm upload"));
        }
    }

    /**
     * Get session status and metadata.
     */
    @GetMapping("/sessions/{captureId}/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(@PathVariable String captureId) {
        try {
            UUID uuid = UUID.fromString(captureId);
            Map<String, Object> result = captureService.getSessionStatus(uuid);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get status"));
        }
    }

    /**
     * Get presigned playback URL for a completed capture.
     */
    @GetMapping("/sessions/{captureId}/playback")
    public ResponseEntity<Map<String, Object>> getPlaybackUrl(@PathVariable String captureId) {
        try {
            UUID uuid = UUID.fromString(captureId);
            Map<String, Object> result = captureService.getPlaybackUrl(uuid);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get playback URL"));
        }
    }

    /**
     * List all capture sessions for current user.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions() {
        try {
            List<Map<String, Object>> sessions = captureService.listSessions();
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete a capture session and its S3 object.
     */
    @DeleteMapping("/sessions/{captureId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String captureId) {
        try {
            UUID uuid = UUID.fromString(captureId);
            captureService.deleteSession(uuid);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete session"));
        }
    }
}
