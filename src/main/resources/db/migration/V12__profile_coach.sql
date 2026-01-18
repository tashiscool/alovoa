-- V12: Profile Coach with Editing Frequency Monitoring
-- Tracks profile editing patterns to detect optimization behavior
-- Part of AURA's anti-gaming design philosophy

CREATE TABLE profile_edit_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    edit_type VARCHAR(50) NOT NULL,
    field_edited VARCHAR(100),
    edit_date DATE NOT NULL,
    edit_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_id VARCHAR(100),

    CONSTRAINT fk_profile_edit_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_profile_edit_user (user_id),
    INDEX idx_profile_edit_date (user_id, edit_date),
    INDEX idx_profile_edit_type (edit_type)
);

-- Daily edit summary for quick lookups
CREATE TABLE profile_edit_daily_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    edit_date DATE NOT NULL,
    total_edits INT NOT NULL DEFAULT 0,
    photo_edits INT NOT NULL DEFAULT 0,
    bio_edits INT NOT NULL DEFAULT 0,
    prompt_edits INT NOT NULL DEFAULT 0,
    other_edits INT NOT NULL DEFAULT 0,
    coaching_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_profile_edit_summary_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,

    UNIQUE KEY uk_user_date (user_id, edit_date),
    INDEX idx_edit_summary_date (edit_date),
    INDEX idx_edit_summary_coaching (coaching_sent)
);

-- Profile coaching messages sent
CREATE TABLE profile_coaching_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    message_content VARCHAR(1000) NOT NULL,
    trigger_reason VARCHAR(200),
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    dismissed_at DATETIME,
    helpful_feedback BOOLEAN,

    CONSTRAINT fk_coaching_msg_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_coaching_user (user_id),
    INDEX idx_coaching_type (message_type),
    INDEX idx_coaching_dismissed (dismissed)
);
