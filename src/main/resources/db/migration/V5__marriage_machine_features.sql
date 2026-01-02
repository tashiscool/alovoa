-- V5: Marriage Machine Features
-- Implements OKCupid-style importance weighting, acceptable answers, and intro messages
-- These are the core features that made OKCupid/eHarmony successful for long-term relationships

-- ===================================================================
-- 1. ASSESSMENT RESPONSE ENHANCEMENTS (OKCupid-style matching)
-- ===================================================================

-- Add importance weighting to responses
-- Values: irrelevant, a_little, somewhat, very, mandatory
-- This is the KEY feature that made OKCupid work - users decide how much each question matters
ALTER TABLE user_assessment_response
ADD COLUMN IF NOT EXISTS importance VARCHAR(20) DEFAULT 'somewhat';

-- Add acceptable answers field (JSON array)
-- Stores which answers the user finds acceptable from a partner
-- e.g., for a 1-5 Likert: "[1,2]" means only 1 or 2 are acceptable
-- This enables OKCupid's "your answer vs acceptable answers" matching
ALTER TABLE user_assessment_response
ADD COLUMN IF NOT EXISTS acceptable_answers TEXT;

-- Optional explanation for answer (shown to matches)
-- Allows users to provide context: "I said this because..."
ALTER TABLE user_assessment_response
ADD COLUMN IF NOT EXISTS explanation VARCHAR(500);

-- Whether to show this Q&A on public profile
-- Default false = used only for matching, not displayed
ALTER TABLE user_assessment_response
ADD COLUMN IF NOT EXISTS public_visible BOOLEAN DEFAULT FALSE;

-- Index for finding public responses efficiently
CREATE INDEX IF NOT EXISTS idx_response_public
ON user_assessment_response(user_id, public_visible)
WHERE public_visible = TRUE;

-- ===================================================================
-- 2. MATCH WINDOW ENHANCEMENTS (Intro Messages + Match Details)
-- ===================================================================

-- Intro message from User A (before match confirmation)
-- This is the "personality leads" feature - lets you send ONE message before matching
-- Like OKCupid's original open messaging, but limited to match window
ALTER TABLE match_window
ADD COLUMN IF NOT EXISTS intro_message_from_a VARCHAR(500);

ALTER TABLE match_window
ADD COLUMN IF NOT EXISTS intro_message_from_a_sent_at TIMESTAMP;

-- Intro message from User B
ALTER TABLE match_window
ADD COLUMN IF NOT EXISTS intro_message_from_b VARCHAR(500);

ALTER TABLE match_window
ADD COLUMN IF NOT EXISTS intro_message_from_b_sent_at TIMESTAMP;

-- Cached match percentage for display in window
-- Using OKCupid's geometric mean formula: sqrt(satisfaction_a * satisfaction_b) * 100
ALTER TABLE match_window
ADD COLUMN IF NOT EXISTS match_percentage DOUBLE PRECISION;

-- Category breakdown JSON for detailed compatibility view
-- e.g., {"VALUES": 94.5, "LIFESTYLE": 78.2, "ATTACHMENT": 85.0}
ALTER TABLE match_window
ADD COLUMN IF NOT EXISTS match_category_breakdown TEXT;

-- Dealbreaker conflict flag
-- If true, match percentage is capped at 10% regardless of other scores
ALTER TABLE match_window
ADD COLUMN IF NOT EXISTS has_mandatory_conflict BOOLEAN DEFAULT FALSE;

-- ===================================================================
-- 3. IMPORTANCE WEIGHT LOOKUP TABLE (for consistent scoring)
-- ===================================================================

-- Pre-populate importance weights for reference
-- These match OKCupid's original weights exactly
CREATE TABLE IF NOT EXISTS importance_weights (
    importance_level VARCHAR(20) PRIMARY KEY,
    weight_value DOUBLE PRECISION NOT NULL,
    display_label VARCHAR(50) NOT NULL,
    description VARCHAR(200)
);

-- Insert the canonical OKCupid importance weights
INSERT INTO importance_weights (importance_level, weight_value, display_label, description) VALUES
('irrelevant', 0.0, 'Irrelevant', 'This question does not matter to me at all'),
('a_little', 1.0, 'A little important', 'Nice to have alignment, but not crucial'),
('somewhat', 10.0, 'Somewhat important', 'I prefer alignment on this'),
('very', 50.0, 'Very important', 'Strong preference for alignment'),
('mandatory', 250.0, 'Mandatory', 'Dealbreaker - must align on this')
ON DUPLICATE KEY UPDATE weight_value = VALUES(weight_value);

-- ===================================================================
-- 4. MATCH INSIGHTS TABLE (for explaining compatibility)
-- ===================================================================

CREATE TABLE IF NOT EXISTS match_insights (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_window_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    insight_text VARCHAR(500),
    is_strength BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (match_window_id) REFERENCES match_window(id) ON DELETE CASCADE,
    INDEX idx_insights_window (match_window_id),
    INDEX idx_insights_category (category)
);

-- ===================================================================
-- 5. UPDATE EXISTING DATA (set defaults)
-- ===================================================================

-- Set default importance for existing responses that don't have one
UPDATE user_assessment_response
SET importance = 'somewhat'
WHERE importance IS NULL;

-- ===================================================================
-- 6. COMMENTS FOR DOCUMENTATION
-- ===================================================================

-- This migration implements the "Marriage Machine" features based on research:
--
-- 1. IMPORTANCE WEIGHTING (eHarmony/OKCupid pattern):
--    - Users rate how important each question is to them
--    - Mandatory = 250 weight = dealbreaker if mismatched
--    - This is WHY eHarmony has 3.86% divorce vs 50% national average
--
-- 2. ACCEPTABLE ANSWERS (OKCupid original feature):
--    - Users select not just their answer, but which answers they'd accept
--    - Enables nuanced matching: "I'm a 4, but I'd date a 3-5"
--    - Geometric mean formula prevents averaging away dealbreakers
--
-- 3. INTRO MESSAGES (OKCupid's best feature, removed in 2017):
--    - Let personality lead before appearance
--    - The user's wife messaged him first because she could
--    - AURA brings this back within the 24-hour match window
--
-- 4. MATCH INSIGHTS (transparency like OKCupid 2004-2011):
--    - Show users WHY they match, not just a percentage
--    - Category breakdown builds trust in the algorithm
--    - Areas to discuss prevents surprises later
