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
 * Post-date feedback for both video and real-world dates.
 * Helps improve matching and safety.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_pdf_from", columnList = "from_user_id"),
    @Index(name = "idx_pdf_about", columnList = "about_user_id")
})
public class PostDateFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "real_world_date_id")
    @JsonIgnore
    private RealWorldDate realWorldDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_date_id")
    @JsonIgnore
    private VideoDate videoDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    @JsonIgnore
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "about_user_id", nullable = false)
    @JsonIgnore
    private User aboutUser;

    // Overall experience (1-5)
    @Column(name = "overall_rating")
    private Integer overallRating;

    @Column(name = "would_see_again")
    private Boolean wouldSeeAgain;

    @Column(name = "chemistry_rating")
    private Integer chemistryRating;

    @Column(name = "conversation_rating")
    private Integer conversationRating;

    // Safety and respect
    @Column(name = "felt_safe")
    private Boolean feltSafe = true;

    @Column(name = "was_respectful")
    private Boolean wasRespectful = true;

    // Specific feedback
    @Lob
    @Column(columnDefinition = "TEXT")
    private String highlights;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String concerns;

    @Lob
    @Column(name = "private_notes", columnDefinition = "TEXT")
    private String privateNotes;

    // Met expectations
    @Column(name = "photos_accurate")
    private Boolean photosAccurate;

    @Column(name = "profile_accurate")
    private Boolean profileAccurate;

    // Future plans
    @Column(name = "planning_second_date")
    private Boolean planningSecondDate;

    @Column(name = "exchanged_contact")
    private Boolean exchangedContact;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "submitted_at", nullable = false)
    private Date submittedAt = new Date();

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (submittedAt == null) {
            submittedAt = new Date();
        }
    }
}
