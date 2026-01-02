-- V6: Profile Scaffolding from Video/Voice Intros
-- Extends video intro system to support multiple prompted segments
-- and AI-inferred assessment scores for quick profile creation

-- ============================================================
-- VIDEO SEGMENT PROMPTS
-- Multiple 2-3 minute videos, each with guided prompts
-- ============================================================

CREATE TABLE IF NOT EXISTS video_segment_prompt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_key VARCHAR(50) NOT NULL UNIQUE,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    suggested_topics TEXT,
    example_responses TEXT,
    duration_min_seconds INT DEFAULT 60,
    duration_max_seconds INT DEFAULT 180,
    display_order INT DEFAULT 0,
    required_for_matching BOOLEAN DEFAULT FALSE,
    active BOOLEAN DEFAULT TRUE
);

-- Insert default prompts
INSERT INTO video_segment_prompt (prompt_key, category, title, description, suggested_topics, required_for_matching, display_order) VALUES
('worldview', 'CORE', 'Your Worldview',
 'Share your perspective on life, what matters most to you, and how you see the world.',
 'Values you live by, Your life philosophy, What gives your life meaning, How you approach challenges',
 TRUE, 1),

('background', 'CORE', 'Your Background',
 'Tell us about where you come from, your journey, and what has shaped who you are today.',
 'Where you grew up, Your education/career path, Formative experiences, Family and culture',
 TRUE, 2),

('personality', 'CORE', 'Who You Are',
 'Describe your personality - how friends would describe you, your energy, and what makes you unique.',
 'Your social style, How you handle stress, What energizes you, Your sense of humor',
 TRUE, 3),

('relationships', 'DATING', 'Love & Relationships',
 'Share what you are looking for in a partner and how you approach relationships.',
 'What you value in a partner, Your attachment style, Past relationship lessons, Deal-breakers',
 TRUE, 4),

('lifestyle', 'DATING', 'Your Lifestyle',
 'Describe your day-to-day life, hobbies, and how you spend your time.',
 'Work-life balance, Health and fitness, Social activities, Future goals',
 FALSE, 5),

('values_deep', 'OPTIONAL', 'Deep Values',
 'Share your views on important topics that matter in relationships.',
 'Family planning, Religion/spirituality, Politics, Career ambitions',
 FALSE, 6);

-- ============================================================
-- USER VIDEO SEGMENTS
-- Individual video clips for each prompt
-- ============================================================

CREATE TABLE IF NOT EXISTS user_video_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    prompt_id BIGINT NOT NULL,

    -- Storage
    s3_key VARCHAR(500),
    mime_type VARCHAR(50),
    duration_seconds INT,

    -- Media type
    intro_type VARCHAR(20) DEFAULT 'VIDEO',

    -- Transcript and analysis
    transcript TEXT,
    ai_summary TEXT,

    -- AI provider that processed this
    ai_provider VARCHAR(50),

    -- Status
    status VARCHAR(20) DEFAULT 'PENDING',

    -- Timestamps
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    analyzed_at TIMESTAMP NULL,

    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (prompt_id) REFERENCES video_segment_prompt(id),
    INDEX idx_segment_user (user_id),
    INDEX idx_segment_status (status)
);

-- ============================================================
-- ADD INFERENCE FIELDS TO USER_VIDEO_INTRODUCTION
-- For storing aggregated AI-inferred scores
-- ============================================================

ALTER TABLE user_video_introduction
    ADD COLUMN IF NOT EXISTS inferred_assessment_json TEXT,
    ADD COLUMN IF NOT EXISTS inferred_openness DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_conscientiousness DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_extraversion DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_agreeableness DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_neuroticism DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_attachment_anxiety DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_attachment_avoidance DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_attachment_style VARCHAR(30),
    ADD COLUMN IF NOT EXISTS inferred_values_progressive DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_values_egalitarian DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_lifestyle_social DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_lifestyle_health DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_lifestyle_work_life DOUBLE,
    ADD COLUMN IF NOT EXISTS inferred_lifestyle_finance DOUBLE,
    ADD COLUMN IF NOT EXISTS overall_inference_confidence DOUBLE,
    ADD COLUMN IF NOT EXISTS inference_reviewed BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS inference_confirmed BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP NULL;

-- ============================================================
-- SCAFFOLDED PROFILE STATUS
-- Track user's progress through the scaffolding flow
-- ============================================================

CREATE TABLE IF NOT EXISTS user_scaffolding_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,

    -- Segment completion tracking
    segments_completed INT DEFAULT 0,
    segments_required INT DEFAULT 4,

    -- Overall status
    status VARCHAR(30) DEFAULT 'NOT_STARTED',

    -- Inference status
    inference_generated BOOLEAN DEFAULT FALSE,
    inference_reviewed BOOLEAN DEFAULT FALSE,
    inference_confirmed BOOLEAN DEFAULT FALSE,

    -- User adjustments (JSON of manual overrides)
    user_adjustments TEXT,

    -- Timestamps
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    confirmed_at TIMESTAMP NULL,

    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_scaffolding_status (status)
);

-- ============================================================
-- INFERRED DEALBREAKERS
-- Store AI-detected dealbreakers from video content
-- ============================================================

CREATE TABLE IF NOT EXISTS inferred_dealbreaker (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    segment_id BIGINT,

    dealbreaker_key VARCHAR(50) NOT NULL,
    confidence DOUBLE,
    source_quote TEXT,

    -- Whether user confirmed this dealbreaker
    confirmed BOOLEAN DEFAULT FALSE,
    rejected BOOLEAN DEFAULT FALSE,

    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (segment_id) REFERENCES user_video_segment(id) ON DELETE SET NULL,
    INDEX idx_inferred_db_user (user_id)
);
