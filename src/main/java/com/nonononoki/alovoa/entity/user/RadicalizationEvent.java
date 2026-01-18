package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Tracks detected radicalization vocabulary for early intervention.
 * Part of AURA's anti-radicalization system based on documentary analysis
 * of the looksmaxing-to-extremism pipeline.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_rad_event_user", columnList = "user_id"),
    @Index(name = "idx_rad_event_tier", columnList = "tier"),
    @Index(name = "idx_rad_event_created", columnList = "createdAt"),
    @Index(name = "idx_rad_event_intervention", columnList = "interventionSent")
})
public class RadicalizationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /**
     * Tier level of detected vocabulary (1=monitoring, 2=concern, 3=urgent)
     */
    @Column(nullable = false)
    private Integer tier;

    /**
     * Source of the detected content
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentSource contentSource;

    /**
     * The specific terms detected (comma-separated)
     */
    @Column(length = 500)
    private String detectedTerms;

    /**
     * Number of terms detected in this content
     */
    private Integer termCount = 1;

    /**
     * Hash of the original content (for deduplication, not stored verbatim for privacy)
     */
    @Column(length = 64)
    @JsonIgnore
    private String contentHash;

    /**
     * Whether a supportive intervention has been sent
     */
    @Column(nullable = false)
    private Boolean interventionSent = false;

    /**
     * Type of intervention sent, if any
     */
    @Enumerated(EnumType.STRING)
    private InterventionType interventionType;

    /**
     * When intervention was sent
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date interventionSentAt;

    /**
     * Whether user engaged with intervention resources
     */
    private Boolean resourcesAccessed = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt = new Date();

    // Enums

    public enum ContentSource {
        PROFILE_BIO,
        PROFILE_PROMPT,
        MESSAGE,
        SUPPORT_TICKET,
        REPORT_REASON,
        APPEAL_STATEMENT
    }

    public enum InterventionType {
        NONE,
        LOGGED_ONLY,          // Tier 1: just logged for monitoring
        GENTLE_MESSAGE,       // Tier 2: supportive message
        RESOURCE_OFFER,       // Tier 2: offered mental health resources
        URGENT_OUTREACH,      // Tier 3: immediate support outreach
        CRISIS_REFERRAL       // Tier 3: connected to crisis services
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}
