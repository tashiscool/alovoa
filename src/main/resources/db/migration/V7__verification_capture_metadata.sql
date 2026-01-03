-- Add capture metadata column for video verification anti-replay and session correlation
-- Stores JSON with: mimeType, durationMs, videoWidth, videoHeight, challengeTimestamps, userAgent

ALTER TABLE user_video_verification
ADD COLUMN IF NOT EXISTS capture_metadata TEXT;
