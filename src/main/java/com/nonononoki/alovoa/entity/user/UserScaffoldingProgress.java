package com.nonononoki.alovoa.entity.user;

import java.util.Date;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks a user's progress through the profile scaffolding flow.
 * This enables users to get matchable in ~10 minutes instead of 2-3 hours
 * by recording video segments that AI analyzes to infer their assessment profile.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_scaffolding_progress",
    indexes = {
        @Index(name = "idx_scaffolding_status", columnList = "status")
    })
public class UserScaffoldingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Segment completion tracking
    @Column(name = "segments_completed")
    private Integer segmentsCompleted = 0;

    @Column(name = "segments_required")
    private Integer segmentsRequired = 4;

    // Overall status of the scaffolding process
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ScaffoldingStatus status = ScaffoldingStatus.NOT_STARTED;

    // Inference status
    @Column(name = "inference_generated")
    private Boolean inferenceGenerated = false;

    @Column(name = "inference_reviewed")
    private Boolean inferenceReviewed = false;

    @Column(name = "inference_confirmed")
    private Boolean inferenceConfirmed = false;

    // User adjustments stored as JSON
    // Format: {"openness": 75, "conscientiousness": 60, ...}
    @Lob
    @Column(name = "user_adjustments", columnDefinition = "TEXT")
    private String userAdjustments;

    // Timestamps
    @Column(name = "started_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date startedAt;

    @Column(name = "completed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date completedAt;

    @Column(name = "confirmed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date confirmedAt;

    public enum ScaffoldingStatus {
        NOT_STARTED,        // User hasn't begun recording segments
        RECORDING,          // User is recording video segments
        SEGMENTS_COMPLETE,  // All required segments uploaded
        ANALYZING,          // AI is analyzing segments
        REVIEW_PENDING,     // Waiting for user to review inferred profile
        ADJUSTING,          // User is making adjustments to inferred scores
        CONFIRMED,          // User confirmed the scaffolded profile
        CANCELLED           // User abandoned the process
    }

    /**
     * Check if all required segments have been recorded
     */
    public boolean allSegmentsRecorded() {
        return segmentsCompleted != null && segmentsRequired != null
            && segmentsCompleted >= segmentsRequired;
    }

    /**
     * Check if the scaffolding process is complete and confirmed
     */
    public boolean isComplete() {
        return status == ScaffoldingStatus.CONFIRMED
            && Boolean.TRUE.equals(inferenceConfirmed);
    }

    /**
     * Check if user can start matching based on scaffolded profile
     */
    public boolean isMatchable() {
        return isComplete();
    }

    /**
     * Increment the completed segment count
     */
    public void incrementSegmentsCompleted() {
        if (segmentsCompleted == null) {
            segmentsCompleted = 0;
        }
        segmentsCompleted++;

        if (allSegmentsRecorded() && status == ScaffoldingStatus.RECORDING) {
            status = ScaffoldingStatus.SEGMENTS_COMPLETE;
            completedAt = new Date();
        }
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null && status != ScaffoldingStatus.NOT_STARTED) {
            startedAt = new Date();
        }
    }
}
