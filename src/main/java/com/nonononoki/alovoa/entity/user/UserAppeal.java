package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * User Appeal - allows RESTRICTED users to appeal their status.
 * Part of the AURA Anti-Radicalization system to provide recovery paths.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_appeal_uuid", columnList = "uuid"),
    @Index(name = "idx_appeal_user", columnList = "user_id"),
    @Index(name = "idx_appeal_status", columnList = "status"),
    @Index(name = "idx_appeal_type", columnList = "appealType"),
    @Index(name = "idx_appeal_created", columnList = "createdAt")
})
public class UserAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    /**
     * The user submitting the appeal
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /**
     * Type of appeal being submitted
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppealType appealType;

    /**
     * Linked report if this is a REPORT_APPEAL
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_report_id")
    @JsonIgnore
    private UserAccountabilityReport linkedReport;

    /**
     * User's reason for appealing
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String appealReason;

    /**
     * Additional supporting statement from user
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String supportingStatement;

    /**
     * Current status of the appeal
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppealStatus status = AppealStatus.PENDING;

    /**
     * Admin who reviewed this appeal
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    @JsonIgnore
    private User reviewedBy;

    /**
     * Admin's notes on the review decision
     */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String reviewNotes;

    /**
     * Outcome of the appeal
     */
    @Enumerated(EnumType.STRING)
    private AppealOutcome outcome;

    /**
     * End date of probation period if appeal approved
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date probationEndDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    private Date reviewedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date updatedAt = new Date();

    // Enums

    public enum AppealType {
        /**
         * Appeal of overall RESTRICTED status due to reputation score
         */
        REPUTATION_APPEAL,

        /**
         * Appeal of a specific accountability report
         */
        REPORT_APPEAL
    }

    public enum AppealStatus {
        /**
         * Appeal submitted, awaiting review
         */
        PENDING,

        /**
         * Currently being reviewed by admin
         */
        UNDER_REVIEW,

        /**
         * Appeal approved - user moves to probation
         */
        APPROVED,

        /**
         * Appeal denied - status unchanged
         */
        DENIED,

        /**
         * Appeal expired (no review in 30 days)
         */
        EXPIRED,

        /**
         * Appeal withdrawn by user
         */
        WITHDRAWN
    }

    public enum AppealOutcome {
        /**
         * Appeal approved - user enters probation period
         */
        PROBATION_GRANTED,

        /**
         * Appeal approved - report removed from profile
         */
        REPORT_REMOVED,

        /**
         * Appeal approved - reputation impact reversed
         */
        REPUTATION_RESTORED,

        /**
         * Appeal denied - evidence supports original action
         */
        EVIDENCE_UPHELD,

        /**
         * Appeal denied - insufficient justification
         */
        INSUFFICIENT_JUSTIFICATION,

        /**
         * Appeal denied - pattern of behavior indicates risk
         */
        PATTERN_DETECTED
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
        if (updatedAt == null) {
            updatedAt = new Date();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}
