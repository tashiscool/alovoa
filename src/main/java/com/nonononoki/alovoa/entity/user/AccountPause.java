package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Tracks account pauses for protective intervention.
 * This is NOT punishment - it's a protective measure with clear recovery path.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_pause_user", columnList = "user_id"),
    @Index(name = "idx_pause_type", columnList = "pause_type")
})
public class AccountPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    @Column(name = "pause_reason", nullable = false, length = 100)
    private String pauseReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "pause_type", nullable = false, length = 50)
    private PauseType pauseType;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "paused_at", nullable = false)
    private Date pausedAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "pause_until")
    private Date pauseUntil;

    @Lob
    @Column(name = "resources_provided", columnDefinition = "TEXT")
    private String resourcesProvided;

    @Column(name = "can_appeal", nullable = false)
    private Boolean canAppeal = true;

    @Column(name = "appeal_submitted", nullable = false)
    private Boolean appealSubmitted = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "resumed_at")
    private Date resumedAt;

    @Column(name = "resumed_reason", length = 200)
    private String resumedReason;

    public enum PauseType {
        PROTECTIVE_BREAK,       // Tier 3 vocabulary - protective, not punitive
        COOLING_OFF,            // User requested break
        REVIEW_PENDING,         // Under review for reports
        VOLUNTARY,              // User chose to pause
        SYSTEM_RECOMMENDED      // System suggested break
    }

    /**
     * Check if pause has expired
     */
    public boolean isExpired() {
        if (pauseUntil == null) return false;
        return new Date().after(pauseUntil);
    }

    /**
     * Check if pause is still active
     */
    public boolean isActive() {
        return resumedAt == null && !isExpired();
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (pausedAt == null) {
            pausedAt = new Date();
        }
    }
}
