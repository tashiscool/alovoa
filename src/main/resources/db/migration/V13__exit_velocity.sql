-- V13: Exit Velocity Tracking
-- The primary success metric for AURA - measuring how quickly users
-- find meaningful relationships and positively exit the platform.
--
-- Philosophy: A successful dating platform should want users to leave
-- because they found love, not stay forever because they're addicted.

CREATE TABLE exit_velocity_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    partner_user_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    exit_reason VARCHAR(100),
    relationship_formed BOOLEAN NOT NULL DEFAULT FALSE,
    days_to_relationship INT,
    days_active_to_relationship INT,
    satisfaction_rating INT,
    feedback TEXT,
    would_recommend BOOLEAN,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exit_date DATE,

    CONSTRAINT fk_exit_velocity_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_exit_velocity_user (user_id),
    INDEX idx_exit_velocity_type (event_type),
    INDEX idx_exit_velocity_formed (relationship_formed),
    INDEX idx_exit_velocity_date (exit_date)
);

-- Platform-wide exit velocity metrics (aggregated daily)
CREATE TABLE exit_velocity_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_date DATE NOT NULL UNIQUE,
    total_exits INT NOT NULL DEFAULT 0,
    positive_exits INT NOT NULL DEFAULT 0,
    relationships_formed INT NOT NULL DEFAULT 0,
    avg_days_to_relationship DOUBLE,
    median_days_to_relationship DOUBLE,
    avg_satisfaction DOUBLE,
    recommendation_rate DOUBLE,
    active_users INT NOT NULL DEFAULT 0,
    new_users INT NOT NULL DEFAULT 0,
    churned_users INT NOT NULL DEFAULT 0,
    calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_exit_metrics_date (metric_date)
);

-- User activity tracking for exit velocity calculation
ALTER TABLE user ADD COLUMN first_active_date DATE AFTER require_video_first;
ALTER TABLE user ADD COLUMN last_meaningful_activity DATE AFTER first_active_date;
ALTER TABLE user ADD COLUMN relationship_formed_date DATE AFTER last_meaningful_activity;
ALTER TABLE user ADD COLUMN exit_survey_completed BOOLEAN NOT NULL DEFAULT FALSE AFTER relationship_formed_date;
