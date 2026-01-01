-- AURA Intake Flow Schema
-- V3: User intake progress tracking and video introduction with AI analysis

-- Intake progress tracking table
CREATE TABLE user_intake_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNIQUE NOT NULL,
    questions_complete BOOLEAN DEFAULT FALSE,
    video_intro_complete BOOLEAN DEFAULT FALSE,
    audio_intro_complete BOOLEAN DEFAULT FALSE,
    pictures_complete BOOLEAN DEFAULT FALSE,
    intake_complete BOOLEAN DEFAULT FALSE,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_intake_progress_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- Video introduction with AI analysis (optional) or manual entry
CREATE TABLE user_video_introduction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid BINARY(16) UNIQUE,
    user_id BIGINT UNIQUE NOT NULL,
    video_data LONGBLOB,
    mime_type VARCHAR(50),
    duration_seconds INT,
    transcript TEXT,
    worldview_summary TEXT,
    background_summary TEXT,
    life_story_summary TEXT,
    personality_indicators TEXT,
    ai_provider VARCHAR(50),
    manual_entry BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'PENDING',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    analyzed_at TIMESTAMP NULL,
    CONSTRAINT fk_video_intro_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- Add core_question flag to assessment_question table
ALTER TABLE assessment_question ADD COLUMN core_question BOOLEAN DEFAULT FALSE;

-- Index for fast core question lookup by category
CREATE INDEX idx_assessment_question_core ON assessment_question(core_question, category);

-- Index for intake progress lookup
CREATE INDEX idx_intake_progress_complete ON user_intake_progress(intake_complete);
