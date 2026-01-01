package com.nonononoki.alovoa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing the user's profile completeness.
 * Tracks what profile elements are complete and which are missing,
 * helping users understand how to improve their profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCompletenessDto {

    /**
     * Overall completion percentage (0-100)
     */
    private int overallPercent;

    /**
     * Whether user has a profile picture
     */
    private boolean hasProfilePicture;

    /**
     * Whether user has a bio/description (50+ characters)
     */
    private boolean hasDescription;

    /**
     * Whether user has uploaded a video introduction
     */
    private boolean hasVideoIntro;

    /**
     * Whether user has uploaded an audio introduction
     */
    private boolean hasAudioIntro;

    /**
     * Whether user has added interests
     */
    private boolean hasInterests;

    /**
     * Whether user has filled out profile details (height, education, etc.)
     */
    private boolean hasProfileDetails;

    /**
     * Whether user has completed video verification
     */
    private boolean isVideoVerified;

    /**
     * Whether user has completed personality assessment
     */
    private boolean hasAssessmentComplete;

    /**
     * List of items that are missing from the profile
     * Examples: ["Add a profile picture", "Complete video intro"]
     */
    @Builder.Default
    private List<String> missingItems = new ArrayList<>();

    /**
     * List of items that have been completed
     * Examples: ["Profile picture", "Bio/description"]
     */
    @Builder.Default
    private List<String> completedItems = new ArrayList<>();

    /**
     * Number of additional photos uploaded (beyond profile picture)
     */
    private int additionalPhotosCount;

    /**
     * Whether user has filled out basic preferences (age range, gender preferences)
     */
    private boolean hasPreferences;
}
