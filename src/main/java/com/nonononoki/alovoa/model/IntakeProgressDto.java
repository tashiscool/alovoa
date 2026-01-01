package com.nonononoki.alovoa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * DTO representing the user's intake/onboarding progress.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakeProgressDto {

    /**
     * Whether the 10 core questions have been answered
     */
    private boolean questionsComplete;

    /**
     * Number of questions answered out of 10
     */
    private int questionsAnswered;

    /**
     * Whether the video introduction has been uploaded
     */
    private boolean videoIntroComplete;

    /**
     * Whether the video has been analyzed by AI
     */
    private boolean videoAnalyzed;

    /**
     * Whether audio intro has been uploaded (optional)
     */
    private boolean audioIntroComplete;

    /**
     * Whether at least one picture has been uploaded
     */
    private boolean picturesComplete;

    /**
     * Number of pictures uploaded
     */
    private int picturesCount;

    /**
     * Whether all required steps are complete
     */
    private boolean intakeComplete;

    /**
     * Overall completion percentage (0-100)
     */
    private int completionPercentage;

    /**
     * When the intake process started
     */
    private Date startedAt;

    /**
     * When the intake was completed (null if not complete)
     */
    private Date completedAt;

    /**
     * Next required step to complete
     */
    private String nextStep;

    /**
     * Current step user should be on (QUESTIONS, VIDEO, PHOTOS, or null if complete)
     */
    private IntakeStep currentStep;

    /**
     * Whether user can proceed to the next step
     */
    private boolean canProceedToNext;

    /**
     * Reason why user is blocked from proceeding (null if not blocked)
     */
    private String blockedReason;

    /**
     * Calculate completion percentage based on required steps
     */
    public static int calculatePercentage(boolean questions, boolean video, boolean pictures) {
        int completed = 0;
        if (questions) completed++;
        if (video) completed++;
        if (pictures) completed++;
        return (completed * 100) / 3; // 3 required steps
    }

    /**
     * Determine the next required step
     */
    public static String determineNextStep(boolean questions, boolean video, boolean pictures) {
        if (!questions) return "ANSWER_QUESTIONS";
        if (!video) return "UPLOAD_VIDEO";
        if (!pictures) return "UPLOAD_PICTURES";
        return "COMPLETE";
    }
}
