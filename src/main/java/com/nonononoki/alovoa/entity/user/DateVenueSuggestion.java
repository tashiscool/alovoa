package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * AI-generated date venue suggestions based on shared interests.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_dvs_users", columnList = "user_a_id, user_b_id")
})
public class DateVenueSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "venue_category", nullable = false, length = 100)
    private String venueCategory;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Lob
    @Column(name = "venue_description", columnDefinition = "TEXT")
    private String venueDescription;

    @Lob
    @Column(name = "matching_interests", columnDefinition = "TEXT")
    private String matchingInterests;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "suggested_at", nullable = false)
    private Date suggestedAt = new Date();

    private Boolean dismissed = false;

    private Boolean accepted = false;

    @PrePersist
    protected void onCreate() {
        if (suggestedAt == null) {
            suggestedAt = new Date();
        }
    }
}
