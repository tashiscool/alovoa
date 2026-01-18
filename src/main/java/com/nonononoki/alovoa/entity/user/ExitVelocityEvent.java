package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

/**
 * Tracks exit velocity events - when users leave the platform and why.
 * Part of AURA's success metric system.
 *
 * Philosophy: A dating platform succeeds when users find meaningful
 * relationships and leave, not when they stay engaged forever.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_exit_velocity_user", columnList = "user_id"),
    @Index(name = "idx_exit_velocity_type", columnList = "event_type"),
    @Index(name = "idx_exit_velocity_formed", columnList = "relationship_formed"),
    @Index(name = "idx_exit_velocity_date", columnList = "exit_date")
})
public class ExitVelocityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_user_id")
    @JsonIgnore
    private User partnerUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private ExitEventType eventType;

    @Column(name = "exit_reason", length = 100)
    private String exitReason;

    /**
     * Whether this exit was due to forming a relationship
     */
    @Column(name = "relationship_formed", nullable = false)
    private Boolean relationshipFormed = false;

    /**
     * Days from account creation to relationship formation
     */
    @Column(name = "days_to_relationship")
    private Integer daysToRelationship;

    /**
     * Days from first meaningful activity to relationship
     */
    @Column(name = "days_active_to_relationship")
    private Integer daysActiveToRelationship;

    /**
     * User satisfaction rating (1-5)
     */
    @Column(name = "satisfaction_rating")
    private Integer satisfactionRating;

    /**
     * Free-form feedback from exit survey
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String feedback;

    /**
     * Would recommend the platform to others
     */
    @Column(name = "would_recommend")
    private Boolean wouldRecommend;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    @Column(name = "exit_date")
    private LocalDate exitDate;

    public enum ExitEventType {
        RELATIONSHIP_FORMED,        // User found a partner on the platform
        RELATIONSHIP_EXTERNAL,      // User found someone outside the platform
        TAKING_BREAK,               // User is pausing, may return
        FRUSTRATION,                // User left due to negative experience
        ACCOUNT_DELETED,            // User deleted their account
        INACTIVE_30_DAYS,           // User became inactive (system detected)
        INACTIVE_90_DAYS,           // Long-term inactive
        PLATFORM_ISSUE,             // Technical or policy issues
        OTHER                       // Other reasons
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
        if (exitDate == null) {
            exitDate = LocalDate.now();
        }
    }
}
