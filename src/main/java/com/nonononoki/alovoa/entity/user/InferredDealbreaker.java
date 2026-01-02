package com.nonononoki.alovoa.entity.user;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI-detected dealbreakers inferred from video/voice content.
 * These are presented to the user for confirmation during profile scaffolding.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inferred_dealbreaker",
    indexes = {
        @Index(name = "idx_inferred_db_user", columnList = "user_id")
    })
public class InferredDealbreaker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private UserVideoSegment segment;

    // Dealbreaker identifier (e.g., "smoking", "kids_must_have", "religious_match")
    @Column(name = "dealbreaker_key", nullable = false, length = 50)
    private String dealbreakerKey;

    // AI's confidence in this inference (0.0 to 1.0)
    @Column
    private Double confidence;

    // Quote from transcript that led to this inference
    @Lob
    @Column(name = "source_quote", columnDefinition = "TEXT")
    private String sourceQuote;

    // User confirmation status
    @Column
    private Boolean confirmed = false;

    @Column
    private Boolean rejected = false;

    /**
     * Check if user has reviewed this dealbreaker
     */
    public boolean isReviewed() {
        return Boolean.TRUE.equals(confirmed) || Boolean.TRUE.equals(rejected);
    }

    /**
     * Get human-readable label for this dealbreaker
     */
    public String getLabel() {
        return DEALBREAKER_LABELS.getOrDefault(dealbreakerKey, dealbreakerKey);
    }

    // Standard dealbreaker keys and their labels
    public static final java.util.Map<String, String> DEALBREAKER_LABELS = java.util.Map.ofEntries(
        java.util.Map.entry("smoking", "No smoking"),
        java.util.Map.entry("no_smoking", "No smoking"),
        java.util.Map.entry("drugs", "No drug use"),
        java.util.Map.entry("no_drugs", "No drug use"),
        java.util.Map.entry("kids_must_have", "Must want children"),
        java.util.Map.entry("kids_must_not_have", "Must not want children"),
        java.util.Map.entry("kids_ok_with_existing", "OK with existing children"),
        java.util.Map.entry("religious_match", "Shared religious values"),
        java.util.Map.entry("political_match", "Shared political values"),
        java.util.Map.entry("monogamy_required", "Monogamous relationship"),
        java.util.Map.entry("marriage_oriented", "Marriage-focused"),
        java.util.Map.entry("no_alcohol", "No alcohol"),
        java.util.Map.entry("pets_required", "Must love pets"),
        java.util.Map.entry("fitness_important", "Active lifestyle"),
        java.util.Map.entry("education_match", "Similar education level"),
        java.util.Map.entry("location_flexibility", "Open to relocation"),
        java.util.Map.entry("career_oriented", "Career-focused partner")
    );
}
