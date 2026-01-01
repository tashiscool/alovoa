-- V4: Political Assessment Updates
-- Adds:
-- - Wealth contribution view (gating question)
-- - Frozen sperm verification fields
-- - Wants kids field

-- Add wealth contribution view column
ALTER TABLE user_political_assessment
    ADD COLUMN IF NOT EXISTS wealth_contribution_view VARCHAR(50);

-- Add frozen sperm verification columns
ALTER TABLE user_political_assessment
    ADD COLUMN IF NOT EXISTS frozen_sperm_status VARCHAR(50);

ALTER TABLE user_political_assessment
    ADD COLUMN IF NOT EXISTS frozen_sperm_verification_url VARCHAR(500);

ALTER TABLE user_political_assessment
    ADD COLUMN IF NOT EXISTS frozen_sperm_verified_at TIMESTAMP NULL;

ALTER TABLE user_political_assessment
    ADD COLUMN IF NOT EXISTS wants_kids BOOLEAN;

-- Add index for wealth contribution view (used in gating logic)
CREATE INDEX IF NOT EXISTS idx_political_wealth_contrib
    ON user_political_assessment(wealth_contribution_view);

-- Update gate_status enum to include REDIRECT_RAYA
-- (MySQL/MariaDB will handle this automatically if using VARCHAR)

-- Update rejection_reason enum to include ABOVE_MEDIAN_WEALTH_DEFENDER
-- (MySQL/MariaDB will handle this automatically if using VARCHAR)
