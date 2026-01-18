-- V14: Intervention System for Radicalization Prevention
-- Completes the intervention pipeline with actual message delivery
-- and account pause/recovery functionality

-- Add new notification types for interventions
ALTER TABLE user_notification
    ADD COLUMN notification_type VARCHAR(50) DEFAULT 'USER_LIKE' AFTER content,
    ADD COLUMN read_status BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN action_taken VARCHAR(50),
    ADD COLUMN action_taken_at DATETIME;

-- Create intervention tracking table
CREATE TABLE intervention_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    radicalization_event_id BIGINT,
    intervention_tier INT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    message_content TEXT NOT NULL,
    delivery_channel VARCHAR(50) NOT NULL,
    delivered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME,
    dismissed_at DATETIME,
    resources_clicked BOOLEAN DEFAULT FALSE,
    user_response VARCHAR(500),

    CONSTRAINT fk_intervention_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_intervention_event FOREIGN KEY (radicalization_event_id) REFERENCES radicalization_event(id) ON DELETE SET NULL,

    INDEX idx_intervention_user (user_id),
    INDEX idx_intervention_tier (intervention_tier),
    INDEX idx_intervention_delivered (delivered_at)
);

-- Account pause tracking for Tier 3 interventions
CREATE TABLE account_pause (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE,
    pause_reason VARCHAR(100) NOT NULL,
    pause_type VARCHAR(50) NOT NULL,
    paused_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pause_until DATETIME,
    resources_provided TEXT,
    can_appeal BOOLEAN NOT NULL DEFAULT TRUE,
    appeal_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    resumed_at DATETIME,
    resumed_reason VARCHAR(200),

    CONSTRAINT fk_pause_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_pause_user (user_id),
    INDEX idx_pause_type (pause_type)
);

-- Add pause status to user
ALTER TABLE user ADD COLUMN account_paused BOOLEAN NOT NULL DEFAULT FALSE AFTER exit_survey_completed;
ALTER TABLE user ADD COLUMN pause_reason VARCHAR(100) AFTER account_paused;

-- Mental health resources table
CREATE TABLE mental_health_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    contact_info VARCHAR(500),
    url VARCHAR(500),
    country_code VARCHAR(10),
    language VARCHAR(20) DEFAULT 'en',
    available_24_7 BOOLEAN DEFAULT FALSE,
    priority INT DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    INDEX idx_resource_type (resource_type),
    INDEX idx_resource_country (country_code)
);

-- Seed mental health resources
INSERT INTO mental_health_resource (resource_type, name, description, contact_info, url, country_code, available_24_7, priority) VALUES
('CRISIS_HOTLINE', 'National Suicide Prevention Lifeline', 'Free, confidential support for people in distress', '988', 'https://988lifeline.org', 'US', TRUE, 100),
('CRISIS_TEXT', 'Crisis Text Line', 'Text-based crisis support', 'Text HOME to 741741', 'https://www.crisistextline.org', 'US', TRUE, 95),
('MENTAL_HEALTH', 'SAMHSA National Helpline', 'Treatment referral and information service', '1-800-662-4357', 'https://www.samhsa.gov/find-help/national-helpline', 'US', TRUE, 90),
('CRISIS_HOTLINE', 'Samaritans', 'Emotional support for anyone in distress', '116 123', 'https://www.samaritans.org', 'GB', TRUE, 100),
('CRISIS_HOTLINE', 'Lifeline Australia', 'Crisis support and suicide prevention', '13 11 14', 'https://www.lifeline.org.au', 'AU', TRUE, 100),
('INTERNATIONAL', 'International Association for Suicide Prevention', 'Global crisis center directory', NULL, 'https://www.iasp.info/resources/Crisis_Centres/', NULL, FALSE, 80),
('ONLINE_CHAT', 'IMAlive', 'Online chat crisis support', NULL, 'https://www.imalive.org', 'US', TRUE, 85),
('MEN_SUPPORT', 'CALM (Campaign Against Living Miserably)', 'Support for men struggling with life', '0800 58 58 58', 'https://www.thecalmzone.net', 'GB', FALSE, 88);
