package com.nonononoki.alovoa.entity.user;

import java.util.Date;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_behavior_user_date", columnList = "user_id, createdAt")
})
public class UserBehaviorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BehaviorType behaviorType;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String eventData;

    private Double reputationImpact;

    @Column(nullable = false)
    private Date createdAt = new Date();

    public enum BehaviorType {
        // Positive behaviors
        THOUGHTFUL_MESSAGE,
        PROMPT_RESPONSE,
        SCHEDULED_DATE,
        COMPLETED_DATE,
        POSITIVE_FEEDBACK,
        GRACEFUL_DECLINE,
        PROFILE_COMPLETE,
        VIDEO_VERIFIED,

        // Negative behaviors
        LOW_EFFORT_MESSAGE,
        SLOW_RESPONSE,
        GHOSTING,
        NO_SHOW,
        NEGATIVE_FEEDBACK,
        REPORTED,
        REPORT_UPHELD,
        INAPPROPRIATE_CONTENT,
        MISREPRESENTATION
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}
