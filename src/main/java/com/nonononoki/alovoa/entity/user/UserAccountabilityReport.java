package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Public Accountability Report - visible on user profiles.
 * Unlike private UserReports (for admin review), these are public feedback
 * that appears in the "Feedback from Others" tab on profiles.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_accountability_subject", columnList = "subject_id"),
    @Index(name = "idx_accountability_reporter", columnList = "reporter_id"),
    @Index(name = "idx_accountability_status", columnList = "status"),
    @Index(name = "idx_accountability_category", columnList = "category")
})
public class UserAccountabilityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    /**
     * The user being reported on (subject of accountability)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    @JsonIgnore
    private User subject;

    /**
     * The user submitting the report
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    @JsonIgnore
    private User reporter;

    /**
     * Category of the accountability report
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountabilityCategory category;

    /**
     * Specific behavior type within category
     */
    @Enumerated(EnumType.STRING)
    private BehaviorType behaviorType;

    /**
     * Report title/summary
     */
    @Column(length = 200)
    private String title;

    /**
     * Detailed description of the behavior
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String description;

    /**
     * Reporter's anonymity preference
     */
    private boolean anonymous = false;

    /**
     * Current status of the report
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.PENDING_VERIFICATION;

    /**
     * Whether screenshot evidence has been verified against message database
     */
    private boolean evidenceVerified = false;

    /**
     * Admin/system notes about verification
     */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String verificationNotes;

    /**
     * Evidence screenshots/files attached to this report
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "report")
    private List<ReportEvidence> evidence;

    /**
     * Date range when the behavior occurred
     */
    @Temporal(TemporalType.DATE)
    private Date incidentStartDate;

    @Temporal(TemporalType.DATE)
    private Date incidentEndDate;

    /**
     * Whether this was from a matched conversation
     */
    private boolean fromMatch = false;

    /**
     * Conversation ID if from a match (for verification)
     */
    @JsonIgnore
    private Long conversationId;

    /**
     * Subject's response to this report
     */
    @Column(columnDefinition = "TEXT")
    private String subjectResponse;

    /**
     * Date subject responded
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date subjectResponseDate;

    /**
     * Visibility of this report
     */
    @Enumerated(EnumType.STRING)
    private ReportVisibility visibility = ReportVisibility.HIDDEN;

    /**
     * How many users found this report helpful
     */
    private int helpfulCount = 0;

    /**
     * How many users flagged this report as inappropriate
     */
    private int flaggedCount = 0;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    private Date verifiedAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date publishedAt;

    /**
     * Impact on subject's reputation if verified
     */
    private Double reputationImpact;

    // Enums

    public enum AccountabilityCategory {
        GHOSTING,           // Disappeared without explanation
        DISHONESTY,         // Lies about self, catfishing
        DISRESPECT,         // Rude, demeaning behavior
        HARASSMENT,         // Persistent unwanted contact
        MANIPULATION,       // Love bombing, gaslighting
        BOUNDARY_VIOLATION, // Ignored stated boundaries
        DATE_NO_SHOW,       // Didn't show up to planned date
        POSITIVE_EXPERIENCE // Good feedback (for balance)
    }

    public enum BehaviorType {
        // Ghosting subtypes
        UNMATCHED_WITHOUT_EXPLANATION,
        STOPPED_RESPONDING,
        STOOD_UP_ON_DATE,

        // Dishonesty subtypes
        FAKE_PHOTOS,
        LIED_ABOUT_INTENTIONS,
        MISREPRESENTED_RELATIONSHIP_STATUS,

        // Disrespect subtypes
        INSULTS,
        BODY_SHAMING,
        CRUDE_BEHAVIOR,

        // Harassment subtypes
        SPAM_MESSAGING,
        CONTACTED_OUTSIDE_APP,
        REFUSED_TO_ACCEPT_REJECTION,

        // Manipulation subtypes
        LOVE_BOMBING,
        GASLIGHTING,
        GUILT_TRIPPING,

        // Positive subtypes
        RESPECTFUL_COMMUNICATOR,
        HONEST_AND_AUTHENTIC,
        GRACEFUL_REJECTION,
        GREAT_DATE
    }

    public enum ReportStatus {
        PENDING_VERIFICATION,  // Awaiting evidence review
        EVIDENCE_INSUFFICIENT, // Not enough proof
        VERIFIED,              // Evidence confirmed
        DISPUTED,              // Subject contested, under review
        PUBLISHED,             // Visible on profile
        RETRACTED,             // Reporter withdrew
        REMOVED                // Admin removed (false report)
    }

    public enum ReportVisibility {
        HIDDEN,           // Not visible (pending/removed)
        MATCHES_ONLY,     // Only visible to matched users
        MUTUAL_LIKES,     // Visible after mutual like
        PUBLIC            // Visible to all authenticated users
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
