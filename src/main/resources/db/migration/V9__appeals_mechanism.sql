-- V9: Appeals Mechanism for AURA Anti-Radicalization
-- Provides RESTRICTED users a path to redemption through a structured appeals process

-- Create user_appeal table
CREATE TABLE user_appeal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    appeal_type VARCHAR(50) NOT NULL,
    linked_report_id BIGINT,
    appeal_reason TEXT NOT NULL,
    supporting_statement MEDIUMTEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reviewed_by_id BIGINT,
    review_notes TEXT,
    outcome VARCHAR(50),
    probation_end_date DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_appeal_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_appeal_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES user(id) ON DELETE SET NULL,
    CONSTRAINT fk_appeal_report FOREIGN KEY (linked_report_id) REFERENCES user_accountability_report(id) ON DELETE SET NULL,

    INDEX idx_appeal_uuid (uuid),
    INDEX idx_appeal_user (user_id),
    INDEX idx_appeal_status (status),
    INDEX idx_appeal_type (appeal_type),
    INDEX idx_appeal_created (created_at)
);

-- Add appeal-related columns to user_reputation_score
ALTER TABLE user_reputation_score
    ADD COLUMN last_appealed_at DATETIME,
    ADD COLUMN appeal_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN probation_until DATETIME,
    ADD COLUMN time_decay_applied_at DATETIME;

-- Add index for probation queries
ALTER TABLE user_reputation_score
    ADD INDEX idx_reputation_probation (probation_until);

-- Add index for time decay scheduled task
ALTER TABLE user_reputation_score
    ADD INDEX idx_reputation_time_decay (time_decay_applied_at);
