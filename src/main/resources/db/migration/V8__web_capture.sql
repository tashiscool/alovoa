-- Web-based video/screen capture (Tier A implementation)
-- Single presigned PUT upload for short recordings (2-3 minutes)

CREATE TABLE IF NOT EXISTS capture_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    capture_id BINARY(16) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    s3_key VARCHAR(500),
    title VARCHAR(255),
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    capture_type VARCHAR(30) DEFAULT 'SCREEN_MIC',
    mime_type VARCHAR(50) DEFAULT 'video/webm',
    file_size_bytes BIGINT,
    duration_seconds INT,
    bitrate_bps INT,
    width_pixels INT,
    height_pixels INT,
    hls_playlist_url VARCHAR(500),
    transcoded_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_at TIMESTAMP NULL,
    processed_at TIMESTAMP NULL,
    error_message VARCHAR(1000),

    CONSTRAINT fk_capture_session_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_capture_session_user (user_id),
    INDEX idx_capture_session_status (status),
    INDEX idx_capture_session_created (created_at)
);

-- Add CAPTURE to S3MediaType enum support (if bucket routing needed)
-- capture/ prefix will use video bucket same as video/
