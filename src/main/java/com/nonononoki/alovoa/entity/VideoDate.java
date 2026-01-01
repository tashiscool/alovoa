package com.nonononoki.alovoa.entity;

import java.util.Date;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class VideoDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    private Date scheduledAt;
    private Date startedAt;
    private Date endedAt;
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DateStatus status = DateStatus.PROPOSED;

    @Column(length = 500)
    private String roomUrl;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String userAFeedback;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String userBFeedback;

    @Column(nullable = false)
    private Date createdAt = new Date();

    // === Calendar Integration ===

    @Column(length = 255)
    private String googleCalendarEventId;

    @Column(length = 255)
    private String appleCalendarEventId;

    @Column(length = 255)
    private String outlookCalendarEventId;

    @Column(length = 255)
    private String icalUid;

    private boolean userACalendarSynced = false;
    private boolean userBCalendarSynced = false;

    private boolean reminderSent = false;

    @Temporal(TemporalType.TIMESTAMP)
    private Date reminderSentAt;

    public enum DateStatus {
        PROPOSED,
        ACCEPTED,
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        NO_SHOW_A,
        NO_SHOW_B,
        EXPIRED
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}
