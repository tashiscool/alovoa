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
@Table(name = "user_intake_progress")
public class UserIntakeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "questions_complete")
    private Boolean questionsComplete = false;

    @Column(name = "video_intro_complete")
    private Boolean videoIntroComplete = false;

    @Column(name = "audio_intro_complete")
    private Boolean audioIntroComplete = false;

    @Column(name = "pictures_complete")
    private Boolean picturesComplete = false;

    @Column(name = "intake_complete")
    private Boolean intakeComplete = false;

    @Column(name = "started_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date startedAt;

    @Column(name = "completed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date completedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = new Date();
        }
    }

    /**
     * Check if all required intake steps are complete.
     * Required: questions, video intro, pictures
     * Optional: audio intro
     */
    public void updateIntakeComplete() {
        // Audio is optional, so not required for intake completion
        this.intakeComplete = Boolean.TRUE.equals(questionsComplete)
                && Boolean.TRUE.equals(videoIntroComplete)
                && Boolean.TRUE.equals(picturesComplete);

        if (this.intakeComplete && this.completedAt == null) {
            this.completedAt = new Date();
        }
    }
}
