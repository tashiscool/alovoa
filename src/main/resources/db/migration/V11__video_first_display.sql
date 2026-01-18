-- V11: Video-First Display System
-- Tracks when users watch video introductions before seeing photos
-- Part of AURA's authenticity-first design philosophy

CREATE TABLE video_intro_watch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    viewer_id BIGINT NOT NULL,
    profile_owner_id BIGINT NOT NULL,
    watched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    watch_duration_seconds INT DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_video_watch_viewer FOREIGN KEY (viewer_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_video_watch_owner FOREIGN KEY (profile_owner_id) REFERENCES user(id) ON DELETE CASCADE,

    UNIQUE KEY uk_video_watch_pair (viewer_id, profile_owner_id),
    INDEX idx_video_watch_viewer (viewer_id),
    INDEX idx_video_watch_owner (profile_owner_id),
    INDEX idx_video_watch_watched_at (watched_at)
);

-- Add video intro URL columns for quick access
ALTER TABLE user_video_introduction
    ADD COLUMN public_video_url VARCHAR(500) AFTER s3_key,
    ADD COLUMN thumbnail_url VARCHAR(500) AFTER public_video_url;

-- Add flag to user preferences for video-first requirement
ALTER TABLE user ADD COLUMN require_video_first BOOLEAN NOT NULL DEFAULT FALSE AFTER show_zodiac;
