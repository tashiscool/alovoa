-- V5: OKCupid 2016 Feature Parity
-- Adds:
-- - Extended profile details (height, body type, ethnicity, diet, pets, education, etc.)
-- - Profile visitor tracking
-- - Search/filter indexes

-- ============================================
-- Extended Profile Details
-- ============================================
CREATE TABLE IF NOT EXISTS user_profile_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNIQUE,

    -- Physical attributes
    height_cm INT,
    body_type VARCHAR(50),
    ethnicity VARCHAR(50),

    -- Lifestyle
    diet VARCHAR(50),
    pets VARCHAR(50),
    pet_details VARCHAR(255),

    -- Background
    education VARCHAR(50),
    occupation VARCHAR(255),
    employer VARCHAR(255),
    languages VARCHAR(500),

    -- Astrology
    zodiac_sign VARCHAR(20),

    -- Response behavior (calculated)
    response_rate VARCHAR(50),

    CONSTRAINT fk_profile_details_user
        FOREIGN KEY (user_id) REFERENCES user(id)
        ON DELETE CASCADE
);

-- Indexes for profile search filters
CREATE INDEX IF NOT EXISTS idx_profile_height ON user_profile_details(height_cm);
CREATE INDEX IF NOT EXISTS idx_profile_body_type ON user_profile_details(body_type);
CREATE INDEX IF NOT EXISTS idx_profile_ethnicity ON user_profile_details(ethnicity);
CREATE INDEX IF NOT EXISTS idx_profile_diet ON user_profile_details(diet);
CREATE INDEX IF NOT EXISTS idx_profile_education ON user_profile_details(education);

-- ============================================
-- Profile Visitor Tracking
-- ============================================
CREATE TABLE IF NOT EXISTS user_profile_visit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    visited_user_id BIGINT NOT NULL,
    visitor_id BIGINT NOT NULL,
    visited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    visit_count INT DEFAULT 1,
    last_visit_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_visit_visited_user
        FOREIGN KEY (visited_user_id) REFERENCES user(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_visitor
        FOREIGN KEY (visitor_id) REFERENCES user(id)
        ON DELETE CASCADE,

    -- Unique constraint: one record per visitor-visited pair
    CONSTRAINT uk_visitor_visited UNIQUE (visitor_id, visited_user_id)
);

-- Indexes for visitor queries
CREATE INDEX IF NOT EXISTS idx_visit_visited ON user_profile_visit(visited_user_id);
CREATE INDEX IF NOT EXISTS idx_visit_visitor ON user_profile_visit(visitor_id);
CREATE INDEX IF NOT EXISTS idx_visit_date ON user_profile_visit(last_visit_at);

-- ============================================
-- Full-text search support for profiles
-- ============================================
-- Add fulltext index on description for keyword search
-- (Only if not already exists - may need to be run separately)
-- ALTER TABLE user ADD FULLTEXT INDEX idx_user_description (description);

-- ============================================
-- Enemy % / Incompatibility scoring support
-- ============================================
-- Add column to store pre-computed incompatibility scores
ALTER TABLE compatibility_score
    ADD COLUMN IF NOT EXISTS enemy_score DOUBLE DEFAULT 0.0;

-- Index for enemy score queries
CREATE INDEX IF NOT EXISTS idx_compat_enemy_score
    ON compatibility_score(enemy_score);
