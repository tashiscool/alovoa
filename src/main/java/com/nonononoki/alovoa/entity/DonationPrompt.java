package com.nonononoki.alovoa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Tracks donation prompts shown to users.
 * Part of the donation-only monetization model.
 *
 * Prompts are shown:
 * - After successful matches (AFTER_MATCH)
 * - After completed video dates (AFTER_DATE)
 * - Monthly for active users (MONTHLY)
 * - When using certain features (FEATURE_USE)
 *
 * These are non-blocking - all features work without donating.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "idx_donation_prompt_user", columnList = "user_id"),
    @Index(name = "idx_donation_prompt_type", columnList = "prompt_type")
})
public class DonationPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_type", nullable = false)
    private PromptType promptType;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "shown_at", nullable = false)
    private Date shownAt;

    /**
     * User dismissed without donating
     */
    private boolean dismissed = false;

    /**
     * User made a donation after this prompt
     */
    private boolean donated = false;

    /**
     * Amount donated (if any)
     */
    @Column(name = "donation_amount", precision = 10, scale = 2)
    private BigDecimal donationAmount;

    // === ENUMS ===

    public enum PromptType {
        AFTER_MATCH,        // Shown after mutual match confirmation
        AFTER_DATE,         // Shown after completing a good video date
        MONTHLY,            // Monthly prompt for active users
        FEATURE_USE,        // Shown when using certain features
        FIRST_LIKE,         // First time someone likes them
        MILESTONE,          // Hit a milestone (e.g., 10 matches)
        RELATIONSHIP_EXIT,  // User marked as "in relationship" and about to leave
        ANNUAL_CAMPAIGN     // Wikipedia-style annual fundraising campaign
    }

    @PrePersist
    protected void onCreate() {
        if (shownAt == null) {
            shownAt = new Date();
        }
    }
}
