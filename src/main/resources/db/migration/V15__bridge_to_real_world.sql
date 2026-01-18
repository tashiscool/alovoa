-- V15: Bridge to Real World Features
-- Date venue suggestions, post-date feedback, and relationship milestones

-- Track first real-world dates and their outcomes
CREATE TABLE real_world_date (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    conversation_id BIGINT,
    video_date_id BIGINT,
    scheduled_at DATETIME,
    occurred_at DATETIME,
    date_type VARCHAR(50) NOT NULL,
    venue_category VARCHAR(100),
    venue_name VARCHAR(200),
    location_city VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'SUGGESTED',
    suggested_by_user_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rwd_user_a FOREIGN KEY (user_a_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_rwd_user_b FOREIGN KEY (user_b_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_rwd_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE SET NULL,
    CONSTRAINT fk_rwd_video_date FOREIGN KEY (video_date_id) REFERENCES video_date(id) ON DELETE SET NULL,

    INDEX idx_rwd_users (user_a_id, user_b_id),
    INDEX idx_rwd_status (status)
);

-- Date venue suggestions based on shared interests
CREATE TABLE date_venue_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    conversation_id BIGINT,
    venue_category VARCHAR(100) NOT NULL,
    venue_name VARCHAR(200),
    venue_description TEXT,
    matching_interests TEXT,
    reason TEXT,
    suggested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dismissed BOOLEAN DEFAULT FALSE,
    accepted BOOLEAN DEFAULT FALSE,

    CONSTRAINT fk_dvs_user_a FOREIGN KEY (user_a_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_dvs_user_b FOREIGN KEY (user_b_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_dvs_users (user_a_id, user_b_id)
);

-- Post-date feedback for real-world dates
CREATE TABLE post_date_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    real_world_date_id BIGINT,
    video_date_id BIGINT,
    from_user_id BIGINT NOT NULL,
    about_user_id BIGINT NOT NULL,

    -- Overall experience
    overall_rating INT,
    would_see_again BOOLEAN,
    chemistry_rating INT,
    conversation_rating INT,

    -- Safety and respect
    felt_safe BOOLEAN DEFAULT TRUE,
    was_respectful BOOLEAN DEFAULT TRUE,

    -- Specific feedback
    highlights TEXT,
    concerns TEXT,
    private_notes TEXT,

    -- Met expectations
    photos_accurate BOOLEAN,
    profile_accurate BOOLEAN,

    -- Future plans
    planning_second_date BOOLEAN,
    exchanged_contact BOOLEAN,

    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pdf_real_date FOREIGN KEY (real_world_date_id) REFERENCES real_world_date(id) ON DELETE SET NULL,
    CONSTRAINT fk_pdf_video_date FOREIGN KEY (video_date_id) REFERENCES video_date(id) ON DELETE SET NULL,
    CONSTRAINT fk_pdf_from FOREIGN KEY (from_user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_pdf_about FOREIGN KEY (about_user_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_pdf_from (from_user_id),
    INDEX idx_pdf_about (about_user_id)
);

-- Relationship milestones for check-ins
CREATE TABLE relationship_milestone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    conversation_id BIGINT,
    milestone_type VARCHAR(50) NOT NULL,
    milestone_date DATE NOT NULL,
    check_in_sent BOOLEAN DEFAULT FALSE,
    check_in_sent_at DATETIME,

    -- Responses from both users
    user_a_response TEXT,
    user_a_responded_at DATETIME,
    user_b_response TEXT,
    user_b_responded_at DATETIME,

    -- Outcome
    relationship_status VARCHAR(50),
    still_together BOOLEAN,
    left_platform_together BOOLEAN DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rm_user_a FOREIGN KEY (user_a_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_rm_user_b FOREIGN KEY (user_b_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_rm_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE SET NULL,

    INDEX idx_rm_users (user_a_id, user_b_id),
    INDEX idx_rm_milestone_type (milestone_type),
    INDEX idx_rm_date (milestone_date)
);

-- Track when to send milestone check-ins
ALTER TABLE conversation ADD COLUMN first_message_date DATETIME AFTER updated_date;
ALTER TABLE conversation ADD COLUMN first_video_date_completed DATETIME AFTER first_message_date;
ALTER TABLE conversation ADD COLUMN first_real_date_completed DATETIME AFTER first_video_date_completed;
ALTER TABLE conversation ADD COLUMN mutual_interest_confirmed BOOLEAN DEFAULT FALSE AFTER first_real_date_completed;
ALTER TABLE conversation ADD COLUMN relationship_started_date DATE AFTER mutual_interest_confirmed;
