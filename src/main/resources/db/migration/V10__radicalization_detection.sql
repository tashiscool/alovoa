-- V10: Radicalization Detection System
-- Part of AURA Anti-Radicalization augmentations
-- Detects vocabulary patterns for early intervention and support

CREATE TABLE radicalization_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tier INT NOT NULL,
    content_source VARCHAR(50) NOT NULL,
    detected_terms VARCHAR(500),
    term_count INT DEFAULT 1,
    content_hash VARCHAR(64),
    intervention_sent BOOLEAN NOT NULL DEFAULT FALSE,
    intervention_type VARCHAR(50),
    intervention_sent_at DATETIME,
    resources_accessed BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rad_event_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_rad_event_user (user_id),
    INDEX idx_rad_event_tier (tier),
    INDEX idx_rad_event_created (created_at),
    INDEX idx_rad_event_intervention (intervention_sent),
    INDEX idx_rad_event_hash (user_id, content_hash)
);
