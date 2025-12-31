-- AURA Extension Migration
-- This migration adds tables for the AURA dating features:
-- - Video verification with face matching
-- - Personality assessment (Big Five)
-- - Reputation system
-- - Daily match limits
-- - Compatibility scoring
-- - Video dating

-- User Videos (profile intro videos)
CREATE TABLE IF NOT EXISTS user_video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE,
    user_id BIGINT NOT NULL,
    video_type VARCHAR(50) NOT NULL,
    video_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    duration_seconds INT,
    transcript MEDIUMTEXT,
    sentiment_scores MEDIUMTEXT,
    is_intro BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_user_video_user (user_id),
    INDEX idx_user_video_type (video_type)
);

-- User Video Verification (face verification status)
CREATE TABLE IF NOT EXISTS user_video_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE,
    user_id BIGINT UNIQUE NOT NULL,
    video_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    face_match_score DOUBLE,
    liveness_score DOUBLE,
    deepfake_score DOUBLE,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    failure_reason TEXT,
    session_id VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_verification_status (status),
    INDEX idx_verification_session (session_id)
);

-- User Reputation Score
CREATE TABLE IF NOT EXISTS user_reputation_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    response_quality DOUBLE DEFAULT 50.0,
    respect_score DOUBLE DEFAULT 50.0,
    authenticity_score DOUBLE DEFAULT 50.0,
    investment_score DOUBLE DEFAULT 50.0,
    ghosting_count INT DEFAULT 0,
    reports_received INT DEFAULT 0,
    reports_upheld INT DEFAULT 0,
    dates_completed INT DEFAULT 0,
    positive_feedback_count INT DEFAULT 0,
    trust_level VARCHAR(50) DEFAULT 'NEW_MEMBER',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_reputation_trust (trust_level)
);

-- User Behavior Events (for reputation tracking)
CREATE TABLE IF NOT EXISTS user_behavior_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    behavior_type VARCHAR(50) NOT NULL,
    target_user_id BIGINT,
    event_data MEDIUMTEXT,
    reputation_impact DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (target_user_id) REFERENCES user(id) ON DELETE SET NULL,
    INDEX idx_behavior_user (user_id),
    INDEX idx_behavior_type (behavior_type),
    INDEX idx_behavior_created (created_at)
);

-- User Personality Profile (Big Five assessment)
CREATE TABLE IF NOT EXISTS user_personality_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    openness DOUBLE,
    conscientiousness DOUBLE,
    extraversion DOUBLE,
    agreeableness DOUBLE,
    neuroticism DOUBLE,
    attachment_style VARCHAR(50),
    attachment_confidence DOUBLE,
    communication_directness DOUBLE,
    communication_emotional DOUBLE,
    values_answers MEDIUMTEXT,
    personality_embedding_id VARCHAR(100),
    values_embedding_id VARCHAR(100),
    interests_embedding_id VARCHAR(100),
    assessment_completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    assessment_version INT DEFAULT 1,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_personality_attachment (attachment_style)
);

-- User Daily Match Limit
CREATE TABLE IF NOT EXISTS user_daily_match_limit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    match_date DATE NOT NULL,
    matches_shown INT DEFAULT 0,
    match_limit INT DEFAULT 5,
    shown_user_ids MEDIUMTEXT,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_match_date (user_id, match_date),
    INDEX idx_match_date (match_date)
);

-- Compatibility Score (cached scores between users)
CREATE TABLE IF NOT EXISTS compatibility_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    values_score DOUBLE,
    lifestyle_score DOUBLE,
    personality_score DOUBLE,
    attraction_score DOUBLE,
    circumstantial_score DOUBLE,
    growth_score DOUBLE,
    overall_score DOUBLE,
    explanation_json MEDIUMTEXT,
    top_compatibilities MEDIUMTEXT,
    potential_challenges MEDIUMTEXT,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_a_profile_updated_at TIMESTAMP NULL,
    user_b_profile_updated_at TIMESTAMP NULL,
    FOREIGN KEY (user_a_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (user_b_id) REFERENCES user(id) ON DELETE CASCADE,
    UNIQUE KEY uk_compatibility_pair (user_a_id, user_b_id),
    INDEX idx_compatibility_overall (overall_score),
    INDEX idx_compatibility_user_a (user_a_id)
);

-- Video Date (in-app video dates)
CREATE TABLE IF NOT EXISTS video_date (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    duration_seconds INT,
    status VARCHAR(50) NOT NULL DEFAULT 'PROPOSED',
    room_url VARCHAR(500),
    user_a_feedback MEDIUMTEXT,
    user_b_feedback MEDIUMTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE SET NULL,
    FOREIGN KEY (user_a_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (user_b_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_video_date_status (status),
    INDEX idx_video_date_scheduled (scheduled_at),
    INDEX idx_video_date_user_a (user_a_id),
    INDEX idx_video_date_user_b (user_b_id)
);

-- Add video_verified column to user table if not exists
-- Note: Hibernate may handle this automatically with ddl-auto=update
ALTER TABLE user ADD COLUMN IF NOT EXISTS video_verification_id BIGINT;
ALTER TABLE user ADD CONSTRAINT fk_user_video_verification
    FOREIGN KEY (video_verification_id) REFERENCES user_video_verification(id) ON DELETE SET NULL;

ALTER TABLE user ADD COLUMN IF NOT EXISTS reputation_score_id BIGINT;
ALTER TABLE user ADD CONSTRAINT fk_user_reputation_score
    FOREIGN KEY (reputation_score_id) REFERENCES user_reputation_score(id) ON DELETE SET NULL;

ALTER TABLE user ADD COLUMN IF NOT EXISTS personality_profile_id BIGINT;
ALTER TABLE user ADD CONSTRAINT fk_user_personality_profile
    FOREIGN KEY (personality_profile_id) REFERENCES user_personality_profile(id) ON DELETE SET NULL;

-- ===============================================
-- PUBLIC ACCOUNTABILITY SYSTEM
-- ===============================================

-- User Accountability Reports (public feedback on profiles)
CREATE TABLE IF NOT EXISTS user_accountability_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE NOT NULL,
    subject_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    behavior_type VARCHAR(50),
    title VARCHAR(200),
    description MEDIUMTEXT,
    anonymous BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    evidence_verified BOOLEAN DEFAULT FALSE,
    verification_notes TEXT,
    incident_start_date DATE,
    incident_end_date DATE,
    from_match BOOLEAN DEFAULT FALSE,
    conversation_id BIGINT,
    subject_response TEXT,
    subject_response_date TIMESTAMP NULL,
    visibility VARCHAR(50) DEFAULT 'HIDDEN',
    helpful_count INT DEFAULT 0,
    flagged_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    published_at TIMESTAMP NULL,
    reputation_impact DOUBLE,
    FOREIGN KEY (subject_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (reporter_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_accountability_subject (subject_id),
    INDEX idx_accountability_reporter (reporter_id),
    INDEX idx_accountability_status (status),
    INDEX idx_accountability_category (category)
);

-- Report Evidence (screenshots and verification)
CREATE TABLE IF NOT EXISTS report_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE NOT NULL,
    report_id BIGINT NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    file_url VARCHAR(500),
    original_filename VARCHAR(255),
    mime_type VARCHAR(100),
    file_size BIGINT,
    caption TEXT,
    extracted_text MEDIUMTEXT,
    verified BOOLEAN DEFAULT FALSE,
    verification_method VARCHAR(50),
    verification_confidence DOUBLE,
    matched_message_ids TEXT,
    image_hash VARCHAR(64),
    appears_tampered BOOLEAN,
    tamper_analysis_notes TEXT,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    display_order INT DEFAULT 0,
    FOREIGN KEY (report_id) REFERENCES user_accountability_report(id) ON DELETE CASCADE,
    INDEX idx_evidence_report (report_id),
    INDEX idx_evidence_type (evidence_type),
    INDEX idx_evidence_verified (verified)
);

-- ===============================================
-- POLITICAL/ECONOMIC ASSESSMENT SYSTEM
-- ===============================================

-- User Political Assessment
CREATE TABLE IF NOT EXISTS user_political_assessment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE NOT NULL,
    user_id BIGINT UNIQUE NOT NULL,

    -- Economic Class Assessment
    income_bracket VARCHAR(50),
    primary_income_source VARCHAR(50),
    wealth_bracket VARCHAR(50),
    owns_rental_properties BOOLEAN,
    employs_others BOOLEAN,
    lives_off_capital BOOLEAN,
    economic_class VARCHAR(50),

    -- Political/Economic Beliefs
    political_orientation VARCHAR(50),
    wealth_redistribution_view INT,
    worker_ownership_view INT,
    universal_services_view INT,
    housing_rights_view INT,
    billionaire_existence_view INT,
    meritocracy_belief_view INT,
    additional_values_json MEDIUMTEXT,
    economic_values_score DOUBLE,

    -- Reproductive Rights
    reproductive_rights_view VARCHAR(50),
    vasectomy_status VARCHAR(50),
    vasectomy_verification_url VARCHAR(500),
    vasectomy_verified_at TIMESTAMP NULL,
    acknowledged_vasectomy_requirement BOOLEAN,

    -- Gate Status
    gate_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_ASSESSMENT',
    rejection_reason VARCHAR(50),
    conservative_explanation TEXT,
    explanation_reviewed BOOLEAN,
    review_notes TEXT,

    -- Class Consciousness
    class_consciousness_score DOUBLE,
    policy_class_analysis_score INT,
    labor_history_score INT,

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assessment_completed_at TIMESTAMP NULL,
    last_updated_at TIMESTAMP NULL,
    assessment_version INT DEFAULT 1,

    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_political_user (user_id),
    INDEX idx_political_class (economic_class),
    INDEX idx_political_orientation (political_orientation),
    INDEX idx_political_gate_status (gate_status)
);

-- Add political assessment reference to user table
ALTER TABLE user ADD COLUMN IF NOT EXISTS political_assessment_id BIGINT;
ALTER TABLE user ADD CONSTRAINT fk_user_political_assessment
    FOREIGN KEY (political_assessment_id) REFERENCES user_political_assessment(id) ON DELETE SET NULL;
