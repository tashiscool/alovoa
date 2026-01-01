package com.nonononoki.alovoa.model;

/**
 * Enum representing the sequential steps in the user intake/onboarding flow.
 * Steps must be completed in order: QUESTIONS -> VIDEO -> PHOTOS
 */
public enum IntakeStep {
    /**
     * Step 1: Answer 10 core questions
     */
    QUESTIONS,

    /**
     * Step 2: Upload video introduction (requires questions to be complete)
     */
    VIDEO,

    /**
     * Step 3: Upload photos (requires video to be complete)
     */
    PHOTOS,

    /**
     * Optional step: Political/economic assessment (not part of required sequence)
     */
    POLITICAL_ASSESSMENT
}
