package com.nonononoki.alovoa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Waitlist signup from landing page.
 * Captures interest before launch.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "idx_waitlist_email", columnList = "email"),
    @Index(name = "idx_waitlist_status", columnList = "status"),
    @Index(name = "idx_waitlist_location", columnList = "location")
})
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Seeking seeking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "invite_code", length = 20)
    private String inviteCode;

    @Column(name = "referred_by")
    private String referredBy;

    @Column(name = "invite_codes_remaining")
    private int inviteCodesRemaining = 3;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "signed_up_at", nullable = false)
    private Date signedUpAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "invited_at")
    private Date invitedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "registered_at")
    private Date registeredAt;

    // Priority score (higher = earlier access)
    // Women get +100, referrals get +10 per referral
    @Column(name = "priority_score")
    private int priorityScore = 0;

    // Source tracking
    @Column(length = 50)
    private String source;

    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    // === ENUMS ===

    public enum Gender {
        WOMAN,
        MAN,
        NONBINARY,
        OTHER
    }

    public enum Seeking {
        MEN,
        WOMEN,
        EVERYONE
    }

    public enum Location {
        DC,
        ARLINGTON,
        ALEXANDRIA,
        NOVA_OTHER,
        MARYLAND,
        OTHER
    }

    public enum Status {
        PENDING,      // On waitlist
        INVITED,      // Sent invite email
        REGISTERED,   // Completed registration
        DECLINED,     // Declined invite
        BOUNCED       // Email bounced
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (signedUpAt == null) {
            signedUpAt = new Date();
        }
        // Women get priority access
        if (gender == Gender.WOMAN) {
            priorityScore += 100;
        }
    }

    /**
     * Generate a unique invite code for this user to share.
     */
    public String generateInviteCode() {
        if (inviteCode == null) {
            inviteCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        return inviteCode;
    }
}
