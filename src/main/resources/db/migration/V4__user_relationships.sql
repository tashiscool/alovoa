-- ============================================
-- V4: User Relationships (Linked Profiles)
-- ============================================
-- Allows users to link their profiles together
-- Similar to Facebook's "In a relationship with..." feature
-- ============================================

CREATE TABLE IF NOT EXISTS user_relationship (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE NOT NULL,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP NULL,
    anniversary_date DATE NULL,
    is_public BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (user1_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (user2_id) REFERENCES user(id) ON DELETE CASCADE,

    -- Prevent duplicate relationships between same users
    UNIQUE KEY uk_user_relationship (user1_id, user2_id),

    -- Indexes for common queries
    INDEX idx_relationship_user1 (user1_id),
    INDEX idx_relationship_user2 (user2_id),
    INDEX idx_relationship_status (status),
    INDEX idx_relationship_type (type)
);
