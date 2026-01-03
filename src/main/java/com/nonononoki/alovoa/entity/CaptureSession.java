package com.nonononoki.alovoa.entity;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a web-based video/screen capture session.
 * Tier A implementation: single presigned PUT upload for short recordings (up to 2-3 minutes).
 */
@Getter
@Setter
@Entity
@Table(name = "capture_session")
public class CaptureSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capture_id", unique = true, nullable = false)
    private UUID captureId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // S3 key for the uploaded recording
    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(length = 255)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaptureStatus status = CaptureStatus.PENDING;

    // What type of capture this is
    @Enumerated(EnumType.STRING)
    @Column(name = "capture_type")
    private CaptureType captureType = CaptureType.SCREEN_MIC;

    // Expected MIME type (always WebM for browser capture)
    @Column(name = "mime_type", length = 50)
    private String mimeType = "video/webm";

    // File size in bytes (after upload verification)
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    // Duration in seconds (after processing)
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // Bitrate used for recording (bps)
    @Column(name = "bitrate_bps")
    private Integer bitrateBps;

    // Resolution
    @Column(name = "width_pixels")
    private Integer widthPixels;

    @Column(name = "height_pixels")
    private Integer heightPixels;

    // HLS/DASH playlist URL after transcoding (optional)
    @Column(name = "hls_playlist_url", length = 500)
    private String hlsPlaylistUrl;

    // Transcoded MP4 URL (optional)
    @Column(name = "transcoded_url", length = 500)
    private String transcodedUrl;

    // Timestamps
    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "uploaded_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date uploadedAt;

    @Column(name = "processed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date processedAt;

    // Error message if processing failed
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public enum CaptureStatus {
        PENDING,      // Session created, waiting for upload
        UPLOADING,    // Upload in progress (client-side)
        UPLOADED,     // Upload complete, awaiting processing
        PROCESSING,   // Transcoding/processing in progress
        READY,        // Fully processed and available
        FAILED        // Processing failed
    }

    public enum CaptureType {
        SCREEN_ONLY,      // Screen capture only
        SCREEN_MIC,       // Screen + microphone
        SCREEN_SYSTEM,    // Screen + system audio (Chrome only)
        SCREEN_ALL,       // Screen + mic + system audio
        WEBCAM_ONLY,      // Webcam only
        WEBCAM_MIC,       // Webcam + microphone
        SCREEN_WEBCAM,    // Screen + webcam overlay + mic
        AUDIO_ONLY        // Microphone only (simplest, most reliable)
    }

    @PrePersist
    protected void onCreate() {
        if (captureId == null) {
            captureId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
    }

    /**
     * Check if capture is ready for playback
     */
    public boolean isReady() {
        return status == CaptureStatus.READY;
    }

    /**
     * Check if capture failed
     */
    public boolean isFailed() {
        return status == CaptureStatus.FAILED;
    }

    /**
     * Check if upload is complete (regardless of processing status)
     */
    public boolean isUploaded() {
        return status == CaptureStatus.UPLOADED
            || status == CaptureStatus.PROCESSING
            || status == CaptureStatus.READY;
    }
}
