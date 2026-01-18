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
 * Tracks relationship milestones for check-ins.
 * Part of the exit velocity tracking system.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_rm_users", columnList = "user_a_id, user_b_id"),
    @Index(name = "idx_rm_milestone_type", columnList = "milestone_type"),
    @Index(name = "idx_rm_date", columnList = "milestone_date")
})
public class RelationshipMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_a_id", nullable = false)
    @JsonIgnore
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_b_id", nullable = false)
    @JsonIgnore
    private User userB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    @JsonIgnore
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "milestone_type", nullable = false, length = 50)
    private MilestoneType milestoneType;

    @Column(name = "milestone_date", nullable = false)
    private LocalDate milestoneDate;

    @Column(name = "check_in_sent")
    private Boolean checkInSent = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "check_in_sent_at")
    private Date checkInSentAt;

    @Lob
    @Column(name = "user_a_response", columnDefinition = "TEXT")
    private String userAResponse;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "user_a_responded_at")
    private Date userARespondedAt;

    @Lob
    @Column(name = "user_b_response", columnDefinition = "TEXT")
    private String userBResponse;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "user_b_responded_at")
    private Date userBRespondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_status", length = 50)
    private RelationshipStatus relationshipStatus;

    @Column(name = "still_together")
    private Boolean stillTogether;

    @Column(name = "left_platform_together")
    private Boolean leftPlatformTogether = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    public enum MilestoneType {
        FIRST_VIDEO_DATE,           // Completed first video call
        FIRST_REAL_DATE,            // Met in person
        SEVEN_DAYS,                 // 1 week since matching
        THIRTY_DAYS,                // 30 days check-in
        SIXTY_DAYS,                 // 2 month check-in
        NINETY_DAYS,                // 3 month check-in
        RELATIONSHIP_CONFIRMED,     // Both confirmed dating
        LEFT_PLATFORM_TOGETHER      // Success - both leaving together
    }

    public enum RelationshipStatus {
        STILL_GETTING_TO_KNOW,
        DATING_CASUALLY,
        DATING_EXCLUSIVELY,
        IN_RELATIONSHIP,
        ENDED_AMICABLY,
        ENDED_POORLY,
        NO_RESPONSE
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
