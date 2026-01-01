-- ============================================
-- AURA COMPLETE DATABASE SCHEMA
-- ============================================
-- This single migration creates all AURA-specific tables and modifications.
-- Consolidated from V2-V10 migrations for clean fresh installs.
--
-- Sections:
-- 1. Core AURA Features (videos, verification, reputation, personality)
-- 2. Compatibility & Matching
-- 3. Intake Flow & Assessments
-- 4. Political Assessment Gate
-- 5. OKCupid Feature Parity (profile details, visitors)
-- 6. Privacy-Safe Location
-- 7. S3 Storage
-- 8. Match Windows & Calendar
-- 9. Donations & Waitlist
-- 10. Messaging Enhancements
-- ============================================


-- ============================================
-- 1. CORE AURA FEATURES
-- ============================================

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


-- ============================================
-- 2. COMPATIBILITY & MATCHING
-- ============================================

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
    enemy_score DOUBLE DEFAULT 0.0,
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
    INDEX idx_compatibility_user_a (user_a_id),
    INDEX idx_compat_enemy_score (enemy_score)
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
    -- Calendar integration
    google_calendar_event_id VARCHAR(255),
    apple_calendar_event_id VARCHAR(255),
    outlook_calendar_event_id VARCHAR(255),
    ical_uid VARCHAR(255),
    user_a_calendar_synced BOOLEAN DEFAULT FALSE,
    user_b_calendar_synced BOOLEAN DEFAULT FALSE,
    reminder_sent BOOLEAN DEFAULT FALSE,
    reminder_sent_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE SET NULL,
    FOREIGN KEY (user_a_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (user_b_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_video_date_status (status),
    INDEX idx_video_date_scheduled (scheduled_at),
    INDEX idx_video_date_user_a (user_a_id),
    INDEX idx_video_date_user_b (user_b_id)
);

-- Match Decision Windows (24-hour decision windows)
CREATE TABLE IF NOT EXISTS match_window (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid BINARY(16) UNIQUE NOT NULL,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_BOTH',
    user_a_confirmed BOOLEAN DEFAULT FALSE,
    user_b_confirmed BOOLEAN DEFAULT FALSE,
    user_a_confirmed_at TIMESTAMP NULL,
    user_b_confirmed_at TIMESTAMP NULL,
    expires_at TIMESTAMP NOT NULL,
    extension_used BOOLEAN DEFAULT FALSE,
    extension_requested_by BIGINT NULL,
    compatibility_score DOUBLE,
    conversation_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    FOREIGN KEY (user_a_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (user_b_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (extension_requested_by) REFERENCES user(id) ON DELETE SET NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE SET NULL,
    UNIQUE KEY uk_match_window_users (user_a_id, user_b_id),
    INDEX idx_window_user_a (user_a_id),
    INDEX idx_window_user_b (user_b_id),
    INDEX idx_window_status (status),
    INDEX idx_window_expires (expires_at)
);


-- ============================================
-- 3. INTAKE FLOW & ASSESSMENTS
-- ============================================

-- Assessment Question table
CREATE TABLE IF NOT EXISTS assessment_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    text TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    subcategory VARCHAR(255),
    response_scale VARCHAR(50),
    domain VARCHAR(255),
    facet INT,
    keyed VARCHAR(50),
    dimension VARCHAR(255),
    red_flag_value INT,
    severity VARCHAR(50),
    inverse BOOLEAN,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    options TEXT,
    suggested_importance VARCHAR(255),
    core_question BOOLEAN DEFAULT FALSE,
    INDEX idx_question_category (category),
    INDEX idx_question_external_id (external_id),
    INDEX idx_assessment_question_core (core_question, category)
);

-- User Assessment Profile (aggregated scores)
CREATE TABLE IF NOT EXISTS user_assessment_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    openness_score DOUBLE,
    conscientiousness_score DOUBLE,
    extraversion_score DOUBLE,
    agreeableness_score DOUBLE,
    neuroticism_score DOUBLE,
    emotional_stability_score DOUBLE,
    attachment_anxiety_score DOUBLE,
    attachment_avoidance_score DOUBLE,
    attachment_style VARCHAR(50),
    values_progressive_score DOUBLE,
    values_egalitarian_score DOUBLE,
    lifestyle_social_score DOUBLE,
    lifestyle_health_score DOUBLE,
    lifestyle_work_life_score DOUBLE,
    lifestyle_finance_score DOUBLE,
    dealbreaker_flags INT,
    big_five_questions_answered INT,
    attachment_questions_answered INT,
    values_questions_answered INT,
    lifestyle_questions_answered INT,
    dealbreaker_questions_answered INT,
    big_five_complete BOOLEAN,
    attachment_complete BOOLEAN,
    values_complete BOOLEAN,
    dealbreaker_complete BOOLEAN,
    lifestyle_complete BOOLEAN,
    profile_complete BOOLEAN,
    last_updated TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_profile_user (user_id)
);

-- User Assessment Response (individual answers)
CREATE TABLE IF NOT EXISTS user_assessment_response (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    numeric_response INT,
    text_response TEXT,
    category VARCHAR(50) NOT NULL,
    answered_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES assessment_question(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_question (user_id, question_id),
    INDEX idx_response_user (user_id),
    INDEX idx_response_question (question_id),
    INDEX idx_response_category (category)
);

-- Intake progress tracking table
CREATE TABLE IF NOT EXISTS user_intake_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNIQUE NOT NULL,
    questions_complete BOOLEAN DEFAULT FALSE,
    video_intro_complete BOOLEAN DEFAULT FALSE,
    audio_intro_complete BOOLEAN DEFAULT FALSE,
    pictures_complete BOOLEAN DEFAULT FALSE,
    intake_complete BOOLEAN DEFAULT FALSE,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_intake_progress_complete (intake_complete)
);

-- Video introduction with AI analysis
CREATE TABLE IF NOT EXISTS user_video_introduction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid BINARY(16) UNIQUE,
    user_id BIGINT UNIQUE NOT NULL,
    s3_key VARCHAR(255),
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
    intro_type VARCHAR(20) DEFAULT 'VIDEO',
    voice_only_url VARCHAR(500),
    voice_duration_seconds INT,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    analyzed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_video_intro_s3_key (s3_key)
);


-- ============================================
-- 4. ACCOUNTABILITY & POLITICAL ASSESSMENT
-- ============================================

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
    wealth_contribution_view VARCHAR(50),
    additional_values_json MEDIUMTEXT,
    economic_values_score DOUBLE,
    -- Reproductive Rights
    reproductive_rights_view VARCHAR(50),
    vasectomy_status VARCHAR(50),
    vasectomy_verification_url VARCHAR(500),
    vasectomy_verified_at TIMESTAMP NULL,
    acknowledged_vasectomy_requirement BOOLEAN,
    frozen_sperm_status VARCHAR(50),
    frozen_sperm_verification_url VARCHAR(500),
    frozen_sperm_verified_at TIMESTAMP NULL,
    wants_kids BOOLEAN,
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
    INDEX idx_political_gate_status (gate_status),
    INDEX idx_political_wealth_contrib (wealth_contribution_view)
);


-- ============================================
-- 5. OKCUPID FEATURE PARITY
-- ============================================

-- Extended Profile Details
CREATE TABLE IF NOT EXISTS user_profile_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNIQUE,
    height_cm INT,
    body_type VARCHAR(50),
    ethnicity VARCHAR(50),
    diet VARCHAR(50),
    pets VARCHAR(50),
    pet_details VARCHAR(255),
    education VARCHAR(50),
    occupation VARCHAR(255),
    employer VARCHAR(255),
    languages VARCHAR(500),
    zodiac_sign VARCHAR(20),
    response_rate VARCHAR(50),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_profile_height (height_cm),
    INDEX idx_profile_body_type (body_type),
    INDEX idx_profile_ethnicity (ethnicity),
    INDEX idx_profile_diet (diet),
    INDEX idx_profile_education (education)
);

-- Profile Visitor Tracking
CREATE TABLE IF NOT EXISTS user_profile_visit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    visited_user_id BIGINT NOT NULL,
    visitor_id BIGINT NOT NULL,
    visited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    visit_count INT DEFAULT 1,
    last_visit_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (visited_user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (visitor_id) REFERENCES user(id) ON DELETE CASCADE,
    UNIQUE KEY uk_visitor_visited (visitor_id, visited_user_id),
    INDEX idx_visit_visited (visited_user_id),
    INDEX idx_visit_visitor (visitor_id),
    INDEX idx_visit_date (last_visit_at)
);


-- ============================================
-- 6. PRIVACY-SAFE LOCATION SYSTEM
-- ============================================

-- User-declared location areas
CREATE TABLE IF NOT EXISTS user_location_area (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    area_type VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(10) NOT NULL,
    country VARCHAR(10) DEFAULT 'US',
    display_level VARCHAR(20) NOT NULL DEFAULT 'CITY',
    display_as VARCHAR(100),
    label VARCHAR(30),
    visible_on_profile BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_location_user (user_id),
    INDEX idx_location_city_state (city, state)
);

-- User location preferences
CREATE TABLE IF NOT EXISTS user_location_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    max_travel_minutes INT DEFAULT 30,
    require_area_overlap BOOLEAN DEFAULT TRUE,
    show_exceptional_matches BOOLEAN DEFAULT TRUE,
    exceptional_match_threshold DOUBLE DEFAULT 0.90,
    moving_to_city VARCHAR(100),
    moving_to_state VARCHAR(10),
    moving_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- Traveling mode
CREATE TABLE IF NOT EXISTS user_traveling_mode (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    destination_city VARCHAR(100) NOT NULL,
    destination_state VARCHAR(10) NOT NULL,
    destination_country VARCHAR(10) DEFAULT 'US',
    display_as VARCHAR(100),
    arriving_date DATE NOT NULL,
    leaving_date DATE NOT NULL,
    show_me_there BOOLEAN DEFAULT TRUE,
    show_locals_to_me BOOLEAN DEFAULT TRUE,
    auto_disable BOOLEAN DEFAULT TRUE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_traveling_active (active, destination_city, destination_state)
);

-- Date spot suggestions
CREATE TABLE IF NOT EXISTS date_spot_suggestion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(10) NOT NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(255),
    description TEXT,
    venue_type VARCHAR(30),
    price_range VARCHAR(20),
    public_space BOOLEAN DEFAULT TRUE,
    well_lit BOOLEAN DEFAULT TRUE,
    near_transit BOOLEAN DEFAULT FALSE,
    easy_exit BOOLEAN DEFAULT TRUE,
    nearest_transit VARCHAR(100),
    walk_minutes_from_transit INT,
    daytime_friendly BOOLEAN DEFAULT TRUE,
    average_rating DOUBLE DEFAULT 0.0,
    rating_count INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_spot_neighborhood (neighborhood, active),
    INDEX idx_spot_city (city, state, active)
);

-- Area centroids (public geographic data)
CREATE TABLE IF NOT EXISTS area_centroid (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(10) NOT NULL,
    country VARCHAR(10) DEFAULT 'US',
    centroid_lat DOUBLE,
    centroid_lng DOUBLE,
    reference_point VARCHAR(200),
    travel_time_cache TEXT,
    display_name VARCHAR(100),
    metro_area VARCHAR(50),
    INDEX idx_centroid_city_state (city, state),
    INDEX idx_centroid_neighborhood (neighborhood, city, state)
);

-- User calendar settings
CREATE TABLE IF NOT EXISTS user_calendar_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    google_calendar_enabled BOOLEAN DEFAULT FALSE,
    google_refresh_token VARCHAR(500),
    google_calendar_id VARCHAR(255),
    apple_calendar_enabled BOOLEAN DEFAULT FALSE,
    apple_caldav_url VARCHAR(500),
    apple_calendar_id VARCHAR(255),
    outlook_calendar_enabled BOOLEAN DEFAULT FALSE,
    outlook_refresh_token VARCHAR(500),
    outlook_calendar_id VARCHAR(255),
    default_reminder_minutes INT DEFAULT 60,
    auto_add_dates BOOLEAN DEFAULT TRUE,
    show_match_name BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);


-- ============================================
-- 7. DONATIONS & WAITLIST
-- ============================================

-- Donation prompts
CREATE TABLE IF NOT EXISTS donation_prompt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    prompt_type VARCHAR(30) NOT NULL,
    shown_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dismissed BOOLEAN DEFAULT FALSE,
    donated BOOLEAN DEFAULT FALSE,
    donation_amount DECIMAL(10,2) NULL,
    stripe_session_id VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_donation_prompt_user (user_id),
    INDEX idx_donation_prompt_type (prompt_type)
);

-- Waitlist entries
CREATE TABLE IF NOT EXISTS waitlist_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid BINARY(16) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    gender VARCHAR(20) NOT NULL,
    seeking VARCHAR(20) NOT NULL,
    location VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_code VARCHAR(20) UNIQUE,
    referred_by VARCHAR(20),
    invite_codes_remaining INT DEFAULT 3,
    priority_score INT DEFAULT 0,
    source VARCHAR(50),
    utm_source VARCHAR(100),
    utm_medium VARCHAR(100),
    utm_campaign VARCHAR(100),
    signed_up_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invited_at TIMESTAMP NULL,
    registered_at TIMESTAMP NULL,
    INDEX idx_waitlist_email (email),
    INDEX idx_waitlist_status (status),
    INDEX idx_waitlist_location (location),
    INDEX idx_waitlist_priority (priority_score DESC, signed_up_at ASC),
    INDEX idx_waitlist_invite_code (invite_code),
    INDEX idx_waitlist_referred_by (referred_by)
);

-- Stripe payment tracking
CREATE TABLE IF NOT EXISTS stripe_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(255) UNIQUE NOT NULL,
    payment_intent_id VARCHAR(255),
    charge_id VARCHAR(255),
    user_id BIGINT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(3) DEFAULT 'usd',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    donation_prompt_id BIGINT NULL,
    customer_email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL,
    FOREIGN KEY (donation_prompt_id) REFERENCES donation_prompt(id) ON DELETE SET NULL,
    INDEX idx_stripe_payment_user (user_id),
    INDEX idx_stripe_payment_status (status),
    INDEX idx_stripe_payment_session (session_id)
);


-- ============================================
-- 8. MESSAGING ENHANCEMENTS
-- ============================================

-- Add read receipts to messages
ALTER TABLE message ADD COLUMN IF NOT EXISTS read_at TIMESTAMP NULL;
ALTER TABLE message ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_message_read_at ON message(read_at);
CREATE INDEX IF NOT EXISTS idx_message_delivered_at ON message(delivered_at);
CREATE INDEX IF NOT EXISTS idx_message_conversation_read ON message(conversation_id, read_at);

-- Message reactions
CREATE TABLE IF NOT EXISTS message_reaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(10) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    UNIQUE KEY unique_reaction (message_id, user_id),
    INDEX idx_message_reaction_message (message_id),
    INDEX idx_message_reaction_user (user_id)
);

-- Content moderation events
CREATE TABLE IF NOT EXISTS content_moderation_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    toxicity_score DOUBLE,
    flagged_categories VARCHAR(255),
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_moderation_user (user_id),
    INDEX idx_moderation_type (content_type),
    INDEX idx_moderation_blocked (blocked),
    INDEX idx_moderation_created (created_at)
);


-- ============================================
-- 9. USER TABLE MODIFICATIONS
-- ============================================

-- Add AURA-specific columns to user table
ALTER TABLE user ADD COLUMN IF NOT EXISTS video_verification_id BIGINT;
ALTER TABLE user ADD COLUMN IF NOT EXISTS reputation_score_id BIGINT;
ALTER TABLE user ADD COLUMN IF NOT EXISTS personality_profile_id BIGINT;
ALTER TABLE user ADD COLUMN IF NOT EXISTS political_assessment_id BIGINT;
ALTER TABLE user ADD COLUMN IF NOT EXISTS donation_tier VARCHAR(20) DEFAULT 'NONE';
ALTER TABLE user ADD COLUMN IF NOT EXISTS last_donation_date TIMESTAMP NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS donation_streak_months INT DEFAULT 0;

-- Add foreign key constraints (ignore errors if already exists)
-- Note: These may need to be run separately if constraints already exist


-- ============================================
-- 10. S3 STORAGE MODIFICATIONS
-- ============================================

-- Add S3 key columns to media entities
ALTER TABLE user_profile_picture ADD COLUMN IF NOT EXISTS s3_key VARCHAR(255);
ALTER TABLE user_image ADD COLUMN IF NOT EXISTS s3_key VARCHAR(255);
ALTER TABLE user_audio ADD COLUMN IF NOT EXISTS s3_key VARCHAR(255);
ALTER TABLE user_audio ADD COLUMN IF NOT EXISTS bin_mime VARCHAR(50);
ALTER TABLE user_verification_picture ADD COLUMN IF NOT EXISTS s3_key VARCHAR(255);

-- Create indexes for S3 key lookups
CREATE INDEX IF NOT EXISTS idx_profile_picture_s3_key ON user_profile_picture(s3_key);
CREATE INDEX IF NOT EXISTS idx_user_image_s3_key ON user_image(s3_key);
CREATE INDEX IF NOT EXISTS idx_user_audio_s3_key ON user_audio(s3_key);
CREATE INDEX IF NOT EXISTS idx_verification_picture_s3_key ON user_verification_picture(s3_key);

CREATE INDEX IF NOT EXISTS idx_user_donation_tier ON user(donation_tier);


-- ============================================
-- 11. SEED DATA - DC METRO AREA
-- ============================================

-- Area centroids for DC Metro
INSERT INTO area_centroid (neighborhood, city, state, centroid_lat, centroid_lng, reference_point, display_name, metro_area) VALUES
('Dupont Circle', 'Washington', 'DC', 38.9096, -77.0434, 'Dupont Circle Metro', 'Dupont Circle', 'DC Metro'),
('Adams Morgan', 'Washington', 'DC', 38.9214, -77.0425, 'Adams Morgan Main Street', 'Adams Morgan', 'DC Metro'),
('Capitol Hill', 'Washington', 'DC', 38.8899, -76.9958, 'Eastern Market Metro', 'Capitol Hill', 'DC Metro'),
('Georgetown', 'Washington', 'DC', 38.9076, -77.0723, 'M Street & Wisconsin Ave', 'Georgetown', 'DC Metro'),
('Navy Yard', 'Washington', 'DC', 38.8764, -77.0056, 'Navy Yard Metro', 'Navy Yard', 'DC Metro'),
('Shaw', 'Washington', 'DC', 38.9127, -77.0219, 'Shaw-Howard Metro', 'Shaw', 'DC Metro'),
('U Street', 'Washington', 'DC', 38.9170, -77.0295, 'U Street Metro', 'U Street', 'DC Metro'),
('Penn Quarter', 'Washington', 'DC', 38.8961, -77.0232, 'Gallery Place Metro', 'Penn Quarter', 'DC Metro'),
('Foggy Bottom', 'Washington', 'DC', 38.8998, -77.0507, 'Foggy Bottom Metro', 'Foggy Bottom', 'DC Metro'),
('Columbia Heights', 'Washington', 'DC', 38.9282, -77.0326, 'Columbia Heights Metro', 'Columbia Heights', 'DC Metro'),
(NULL, 'Washington', 'DC', 38.9072, -77.0369, 'Downtown DC', 'Washington, DC', 'DC Metro'),
('Clarendon', 'Arlington', 'VA', 38.8868, -77.0954, 'Clarendon Metro', 'Clarendon', 'DC Metro'),
('Ballston', 'Arlington', 'VA', 38.8828, -77.1116, 'Ballston Metro', 'Ballston', 'DC Metro'),
('Rosslyn', 'Arlington', 'VA', 38.8964, -77.0729, 'Rosslyn Metro', 'Rosslyn', 'DC Metro'),
('Crystal City', 'Arlington', 'VA', 38.8568, -77.0492, 'Crystal City Metro', 'Crystal City', 'DC Metro'),
('Pentagon City', 'Arlington', 'VA', 38.8624, -77.0593, 'Pentagon City Metro', 'Pentagon City', 'DC Metro'),
('Shirlington', 'Arlington', 'VA', 38.8419, -77.0866, 'Shirlington Village', 'Shirlington', 'DC Metro'),
(NULL, 'Arlington', 'VA', 38.8799, -77.1067, 'Courthouse Metro', 'Arlington', 'DC Metro'),
('Old Town', 'Alexandria', 'VA', 38.8048, -77.0435, 'King Street Metro', 'Old Town Alexandria', 'DC Metro'),
('Del Ray', 'Alexandria', 'VA', 38.8333, -77.0591, 'Del Ray Main Street', 'Del Ray', 'DC Metro'),
(NULL, 'Alexandria', 'VA', 38.8048, -77.0469, 'Alexandria City Center', 'Alexandria', 'DC Metro'),
(NULL, 'Falls Church', 'VA', 38.8823, -77.1711, 'Falls Church Metro', 'Falls Church', 'DC Metro'),
(NULL, 'Tysons', 'VA', 38.9187, -77.2244, 'Tysons Corner Metro', 'Tysons', 'DC Metro'),
(NULL, 'Reston', 'VA', 38.9586, -77.3570, 'Reston Town Center', 'Reston', 'DC Metro'),
(NULL, 'Fairfax', 'VA', 38.8462, -77.3064, 'Fairfax City Hall', 'Fairfax', 'DC Metro'),
(NULL, 'Bethesda', 'MD', 38.9847, -77.0947, 'Bethesda Metro', 'Bethesda', 'DC Metro'),
(NULL, 'Silver Spring', 'MD', 38.9907, -77.0261, 'Silver Spring Metro', 'Silver Spring', 'DC Metro'),
(NULL, 'Rockville', 'MD', 38.9850, -77.1465, 'Rockville Town Center', 'Rockville', 'DC Metro'),
(NULL, 'College Park', 'MD', 39.0021, -76.9312, 'College Park Metro', 'College Park', 'DC Metro'),
(NULL, 'Takoma Park', 'MD', 38.9779, -77.0075, 'Takoma Metro', 'Takoma Park', 'DC Metro'),
(NULL, 'Hyattsville', 'MD', 38.9559, -76.9455, 'Prince Georges Plaza Metro', 'Hyattsville', 'DC Metro'),
(NULL, 'Gaithersburg', 'MD', 39.1434, -77.2014, 'Gaithersburg City Center', 'Gaithersburg', 'DC Metro')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

-- Sample date spots
INSERT INTO date_spot_suggestion (neighborhood, city, state, name, address, description, venue_type, price_range, public_space, well_lit, near_transit, easy_exit, nearest_transit, walk_minutes_from_transit, daytime_friendly) VALUES
('Dupont Circle', 'Washington', 'DC', 'Kramerbooks & Afterwords Cafe', '1517 Connecticut Ave NW', 'Iconic bookstore cafe', 'COFFEE_SHOP', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'Dupont Circle Metro', 3, TRUE),
('Dupont Circle', 'Washington', 'DC', 'The Phillips Collection', '1600 21st St NW', 'Intimate art museum', 'MUSEUM', 'MODERATE', TRUE, TRUE, TRUE, TRUE, 'Dupont Circle Metro', 5, TRUE),
('Capitol Hill', 'Washington', 'DC', 'Eastern Market', '225 7th St SE', 'Historic market with food stalls', 'MARKET', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'Eastern Market Metro', 1, TRUE),
('Clarendon', 'Arlington', 'VA', 'Northside Social', '3211 Wilson Blvd', 'Spacious coffee shop', 'COFFEE_SHOP', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'Clarendon Metro', 2, TRUE),
('Old Town', 'Alexandria', 'VA', 'Waterfront Park', 'N Union St & King St', 'Beautiful waterfront walk', 'PARK', 'FREE', TRUE, TRUE, TRUE, TRUE, 'King Street Metro', 10, TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);
