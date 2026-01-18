package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoDate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Tracks real-world (in-person) dates after video dates.
 * Part of the "bridge to real world" system.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_rwd_users", columnList = "user_a_id, user_b_id"),
    @Index(name = "idx_rwd_status", columnList = "status")
})
public class RealWorldDate {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_date_id")
    @JsonIgnore
    private VideoDate videoDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date scheduledAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_type", nullable = false, length = 50)
    private DateType dateType;

    @Column(name = "venue_category", length = 100)
    private String venueCategory;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Column(name = "location_city", length = 100)
    private String locationCity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DateStatus status = DateStatus.SUGGESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_by_user_id")
    @JsonIgnore
    private User suggestedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    public enum DateType {
        COFFEE,
        DINNER,
        ACTIVITY,
        OUTDOOR,
        CULTURAL,
        CASUAL_HANGOUT,
        OTHER
    }

    public enum DateStatus {
        SUGGESTED,
        AGREED,
        SCHEDULED,
        COMPLETED,
        CANCELLED,
        NO_SHOW
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
