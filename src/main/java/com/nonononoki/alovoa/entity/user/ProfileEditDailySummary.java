package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

/**
 * Daily summary of profile edits for quick lookups and coaching triggers.
 * Part of AURA's Profile Coach anti-optimization system.
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "edit_date"}),
       indexes = {
           @Index(name = "idx_edit_summary_date", columnList = "edit_date"),
           @Index(name = "idx_edit_summary_coaching", columnList = "coaching_sent")
       })
public class ProfileEditDailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "edit_date", nullable = false)
    private LocalDate editDate;

    @Column(name = "total_edits", nullable = false)
    private Integer totalEdits = 0;

    @Column(name = "photo_edits", nullable = false)
    private Integer photoEdits = 0;

    @Column(name = "bio_edits", nullable = false)
    private Integer bioEdits = 0;

    @Column(name = "prompt_edits", nullable = false)
    private Integer promptEdits = 0;

    @Column(name = "other_edits", nullable = false)
    private Integer otherEdits = 0;

    @Column(name = "coaching_sent", nullable = false)
    private Boolean coachingSent = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    /**
     * Check if edit count exceeds coaching threshold
     */
    public boolean exceedsThreshold(int threshold) {
        return totalEdits >= threshold;
    }

    /**
     * Increment the appropriate edit counter
     */
    public void incrementEdit(ProfileEditEvent.EditType editType) {
        totalEdits++;

        switch (editType) {
            case PHOTO_ADD:
            case PHOTO_DELETE:
            case PHOTO_REORDER:
            case PROFILE_PICTURE:
                photoEdits++;
                break;
            case BIO_UPDATE:
                bioEdits++;
                break;
            case PROMPT_ADD:
            case PROMPT_UPDATE:
            case PROMPT_DELETE:
                promptEdits++;
                break;
            default:
                otherEdits++;
                break;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
        if (editDate == null) {
            editDate = LocalDate.now();
        }
    }
}
