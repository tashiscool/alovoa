package com.nonononoki.alovoa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.user.Conversation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * 24-hour match decision window.
 * When two users match, they have 24 hours to decide to proceed.
 * This prevents endless chat purgatory and ghosting.
 *
 * Flow:
 * 1. Match created -> window opens (24 hours)
 * 2. Both users must "confirm interest" within window
 * 3. If both confirm -> proceed to conversation/video date scheduling
 * 4. If either declines or expires -> match archived
 * 5. Users can extend window once (12 hours) if they need more time
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_a_id", "user_b_id"}),
    indexes = {
        @Index(name = "idx_window_user_a", columnList = "user_a_id"),
        @Index(name = "idx_window_user_b", columnList = "user_b_id"),
        @Index(name = "idx_window_status", columnList = "status"),
        @Index(name = "idx_window_expires", columnList = "expiresAt")
    }
)
public class MatchWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    /**
     * Current status of the match window
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WindowStatus status = WindowStatus.PENDING_BOTH;

    /**
     * Has user A confirmed interest?
     */
    private boolean userAConfirmed = false;

    /**
     * Has user B confirmed interest?
     */
    private boolean userBConfirmed = false;

    /**
     * When user A confirmed (null if not yet)
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date userAConfirmedAt;

    /**
     * When user B confirmed (null if not yet)
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date userBConfirmedAt;

    /**
     * When this window expires
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date expiresAt;

    /**
     * Whether an extension has been used
     */
    private boolean extensionUsed = false;

    /**
     * Who requested the extension (if any)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extension_requested_by")
    private User extensionRequestedBy;

    /**
     * Compatibility score between users (cached for display)
     */
    private Double compatibilityScore;

    /**
     * Reference to the conversation created after both confirm
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // === ENUMS ===

    public enum WindowStatus {
        PENDING_BOTH,       // Neither has confirmed
        PENDING_USER_A,     // User B confirmed, waiting on A
        PENDING_USER_B,     // User A confirmed, waiting on B
        CONFIRMED,          // Both confirmed - proceed to conversation
        DECLINED_BY_A,      // User A declined
        DECLINED_BY_B,      // User B declined
        EXPIRED,            // Window expired without both confirming
        EXTENSION_PENDING   // Extension requested, waiting for approval
    }

    // === CONSTANTS ===

    public static final int INITIAL_WINDOW_HOURS = 24;
    public static final int EXTENSION_HOURS = 12;

    // === LIFECYCLE ===

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        createdAt = new Date();
        updatedAt = new Date();
        if (expiresAt == null) {
            // Default 24-hour window
            expiresAt = new Date(System.currentTimeMillis() + (INITIAL_WINDOW_HOURS * 60 * 60 * 1000L));
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }

    // === HELPER METHODS ===

    /**
     * Check if this window has expired
     */
    public boolean isExpired() {
        return new Date().after(expiresAt);
    }

    /**
     * Get hours remaining in window
     */
    public int getHoursRemaining() {
        long diff = expiresAt.getTime() - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) (diff / (60 * 60 * 1000));
    }

    /**
     * Get minutes remaining in window
     */
    public int getMinutesRemaining() {
        long diff = expiresAt.getTime() - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) (diff / (60 * 1000));
    }

    /**
     * Check if a specific user has confirmed
     */
    public boolean hasUserConfirmed(User user) {
        if (user.getId().equals(userA.getId())) {
            return userAConfirmed;
        } else if (user.getId().equals(userB.getId())) {
            return userBConfirmed;
        }
        return false;
    }

    /**
     * Check if both users have confirmed
     */
    public boolean isBothConfirmed() {
        return userAConfirmed && userBConfirmed;
    }

    /**
     * Get the other user in the match
     */
    public User getOtherUser(User currentUser) {
        if (currentUser.getId().equals(userA.getId())) {
            return userB;
        }
        return userA;
    }

    /**
     * Can this window be extended?
     */
    public boolean canExtend() {
        return !extensionUsed &&
               (status == WindowStatus.PENDING_BOTH ||
                status == WindowStatus.PENDING_USER_A ||
                status == WindowStatus.PENDING_USER_B);
    }
}
