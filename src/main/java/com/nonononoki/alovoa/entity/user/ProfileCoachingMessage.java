package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Profile coaching messages sent to users about their editing behavior.
 * Part of AURA's Profile Coach anti-optimization system.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_coaching_user", columnList = "user_id"),
    @Index(name = "idx_coaching_type", columnList = "message_type"),
    @Index(name = "idx_coaching_dismissed", columnList = "dismissed")
})
public class ProfileCoachingMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 50)
    private MessageType messageType;

    @Column(name = "message_content", nullable = false, length = 1000)
    private String messageContent;

    @Column(name = "trigger_reason", length = 200)
    private String triggerReason;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sent_at", nullable = false)
    private Date sentAt = new Date();

    @Column(nullable = false)
    private Boolean dismissed = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dismissed_at")
    private Date dismissedAt;

    @Column(name = "helpful_feedback")
    private Boolean helpfulFeedback;

    public enum MessageType {
        FREQUENT_EDITS,           // More than 5 edits in a day
        PHOTO_OPTIMIZATION,       // Frequent photo changes
        BIO_TWEAKING,             // Repeated bio edits
        GENERAL_ENCOURAGEMENT,    // Positive coaching
        AUTHENTICITY_REMINDER,    // Reminder to be authentic
        SUCCESS_TIP              // Tips for successful profiles
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (sentAt == null) {
            sentAt = new Date();
        }
    }
}
