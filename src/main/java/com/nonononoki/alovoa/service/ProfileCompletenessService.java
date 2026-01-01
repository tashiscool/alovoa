package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideo;
import com.nonononoki.alovoa.model.ProfileCompletenessDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to calculate user profile completeness.
 * Helps users understand what's missing from their profile and how to improve it.
 */
@Service
public class ProfileCompletenessService {

    // Point values for each profile element (total = 100)
    private static final int POINTS_PROFILE_PICTURE = 20;
    private static final int POINTS_DESCRIPTION = 15;
    private static final int POINTS_VIDEO_INTRO = 20;
    private static final int POINTS_AUDIO_INTRO = 5;
    private static final int POINTS_INTERESTS = 10;
    private static final int POINTS_PROFILE_DETAILS = 10;
    private static final int POINTS_VIDEO_VERIFIED = 10;
    private static final int POINTS_ASSESSMENT = 10;

    // Minimum description length to be considered complete
    private static final int MIN_DESCRIPTION_LENGTH = 50;

    // Minimum number of interests to be considered complete
    private static final int MIN_INTERESTS_COUNT = 3;

    /**
     * Calculate the completeness of a user's profile.
     *
     * @param user The user whose profile to evaluate
     * @return ProfileCompletenessDto with detailed breakdown
     */
    public ProfileCompletenessDto calculateCompleteness(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        List<String> missing = new ArrayList<>();
        List<String> completed = new ArrayList<>();
        int score = 0;
        int maxScore = 0;

        // Profile picture (20 points)
        maxScore += POINTS_PROFILE_PICTURE;
        boolean hasProfilePicture = user.getProfilePicture() != null;
        if (hasProfilePicture) {
            score += POINTS_PROFILE_PICTURE;
            completed.add("Profile picture");
        } else {
            missing.add("Add a profile picture");
        }

        // Description/Bio (15 points)
        maxScore += POINTS_DESCRIPTION;
        boolean hasDescription = user.getDescription() != null &&
                user.getDescription().trim().length() >= MIN_DESCRIPTION_LENGTH;
        if (hasDescription) {
            score += POINTS_DESCRIPTION;
            completed.add("Bio/description");
        } else {
            missing.add("Write a bio (at least " + MIN_DESCRIPTION_LENGTH + " characters)");
        }

        // Video intro (20 points)
        maxScore += POINTS_VIDEO_INTRO;
        UserVideo introVideo = getIntroVideo(user);
        boolean hasVideoIntro = introVideo != null;
        if (hasVideoIntro) {
            score += POINTS_VIDEO_INTRO;
            completed.add("Video introduction");
        } else {
            missing.add("Upload a video introduction");
        }

        // Audio intro (5 points)
        maxScore += POINTS_AUDIO_INTRO;
        boolean hasAudioIntro = user.getAudio() != null;
        if (hasAudioIntro) {
            score += POINTS_AUDIO_INTRO;
            completed.add("Audio introduction");
        } else {
            missing.add("Upload an audio introduction");
        }

        // Interests (10 points)
        maxScore += POINTS_INTERESTS;
        boolean hasInterests = user.getInterests() != null &&
                user.getInterests().size() >= MIN_INTERESTS_COUNT;
        if (hasInterests) {
            score += POINTS_INTERESTS;
            completed.add("Interests (at least " + MIN_INTERESTS_COUNT + ")");
        } else {
            missing.add("Add at least " + MIN_INTERESTS_COUNT + " interests");
        }

        // Profile details (10 points)
        maxScore += POINTS_PROFILE_DETAILS;
        boolean hasProfileDetails = hasBasicProfileDetails(user);
        if (hasProfileDetails) {
            score += POINTS_PROFILE_DETAILS;
            completed.add("Profile details (height, education, etc.)");
        } else {
            missing.add("Complete profile details (height, education, lifestyle)");
        }

        // Video verified (10 points)
        maxScore += POINTS_VIDEO_VERIFIED;
        boolean isVideoVerified = user.isVideoVerified();
        if (isVideoVerified) {
            score += POINTS_VIDEO_VERIFIED;
            completed.add("Video verification");
        } else {
            missing.add("Complete video verification");
        }

        // Assessment complete (10 points)
        maxScore += POINTS_ASSESSMENT;
        boolean hasAssessment = hasCompletedAssessment(user);
        if (hasAssessment) {
            score += POINTS_ASSESSMENT;
            completed.add("Personality assessment");
        } else {
            missing.add("Complete personality assessment");
        }

        // Calculate percentage
        int overallPercent = (score * 100) / maxScore;

        // Count additional photos
        int additionalPhotosCount = 0;
        if (user.getImages() != null) {
            additionalPhotosCount = user.getImages().size();
        }

        // Check if basic preferences are set
        boolean hasPreferences = user.getPreferedGenders() != null &&
                !user.getPreferedGenders().isEmpty() &&
                user.getPreferedMinAge() > 0 &&
                user.getPreferedMaxAge() > 0;

        // Build and return DTO
        return ProfileCompletenessDto.builder()
                .overallPercent(overallPercent)
                .hasProfilePicture(hasProfilePicture)
                .hasDescription(hasDescription)
                .hasVideoIntro(hasVideoIntro)
                .hasAudioIntro(hasAudioIntro)
                .hasInterests(hasInterests)
                .hasProfileDetails(hasProfileDetails)
                .isVideoVerified(isVideoVerified)
                .hasAssessmentComplete(hasAssessment)
                .missingItems(missing)
                .completedItems(completed)
                .additionalPhotosCount(additionalPhotosCount)
                .hasPreferences(hasPreferences)
                .build();
    }

    /**
     * Get the user's intro video if it exists.
     *
     * @param user The user
     * @return The intro video or null
     */
    private UserVideo getIntroVideo(User user) {
        if (user.getVideos() == null || user.getVideos().isEmpty()) {
            return null;
        }
        return user.getVideos().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsIntro()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if user has filled out basic profile details.
     *
     * @param user The user
     * @return true if profile details are reasonably complete
     */
    private boolean hasBasicProfileDetails(User user) {
        if (user.getProfileDetails() == null) {
            return false;
        }

        // At least 3 of the following should be filled out:
        // height, body type, education, occupation, diet, pets
        int detailsCount = 0;

        if (user.getProfileDetails().getHeightCm() != null) {
            detailsCount++;
        }
        if (user.getProfileDetails().getBodyType() != null) {
            detailsCount++;
        }
        if (user.getProfileDetails().getEducation() != null) {
            detailsCount++;
        }
        if (user.getProfileDetails().getOccupation() != null &&
                !user.getProfileDetails().getOccupation().trim().isEmpty()) {
            detailsCount++;
        }
        if (user.getProfileDetails().getDiet() != null) {
            detailsCount++;
        }
        if (user.getProfileDetails().getPets() != null) {
            detailsCount++;
        }

        return detailsCount >= 3;
    }

    /**
     * Check if user has completed personality assessment.
     *
     * @param user The user
     * @return true if assessment is complete
     */
    private boolean hasCompletedAssessment(User user) {
        if (user.getPersonalityProfile() == null) {
            return false;
        }
        return user.getPersonalityProfile().isComplete();
    }
}
