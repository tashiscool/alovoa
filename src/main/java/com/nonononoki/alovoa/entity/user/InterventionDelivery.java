package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Tracks delivery of intervention messages to users.
 * Part of AURA's radicalization prevention system.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_intervention_user", columnList = "user_id"),
    @Index(name = "idx_intervention_tier", columnList = "intervention_tier"),
    @Index(name = "idx_intervention_delivered", columnList = "delivered_at")
})
public class InterventionDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "radicalization_event_id")
    @JsonIgnore
    private RadicalizationEvent radicalizationEvent;

    @Column(name = "intervention_tier", nullable = false)
    private Integer interventionTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 50)
    private MessageType messageType;

    @Lob
    @Column(name = "message_content", nullable = false, columnDefinition = "TEXT")
    private String messageContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_channel", nullable = false, length = 50)
    private DeliveryChannel deliveryChannel;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "delivered_at", nullable = false)
    private Date deliveredAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "read_at")
    private Date readAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dismissed_at")
    private Date dismissedAt;

    @Column(name = "resources_clicked")
    private Boolean resourcesClicked = false;

    @Column(name = "user_response", length = 500)
    private String userResponse;

    public enum MessageType {
        GENTLE_REDIRECT,        // Tier 1: Gentle encouragement
        SUPPORTIVE_OUTREACH,    // Tier 2: Checking in
        CRISIS_INTERVENTION,    // Tier 3: Immediate support
        ACCOUNT_PAUSE_NOTICE,   // Account paused notification
        RECOVERY_WELCOME,       // Welcome back after pause
        RESOURCE_OFFER          // Mental health resources
    }

    public enum DeliveryChannel {
        IN_APP_NOTIFICATION,
        IN_APP_MODAL,
        EMAIL,
        PUSH_NOTIFICATION
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (deliveredAt == null) {
            deliveredAt = new Date();
        }
    }
}
