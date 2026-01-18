package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.VideoIntroWatch;
import com.nonononoki.alovoa.model.UserDto;
import com.nonononoki.alovoa.repo.VideoIntroWatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * Service for managing video-first display functionality.
 * Ensures users watch video introductions before seeing photos.
 */
@Service
public class VideoFirstService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoFirstService.class);

    @Autowired
    private VideoIntroWatchRepository watchRepo;

    @Autowired
    private AuthService authService;

    /**
     * Check if viewer has watched profile owner's video introduction
     */
    public boolean hasWatchedVideo(User viewer, User profileOwner) {
        return watchRepo.existsByViewerAndProfileOwner(viewer, profileOwner);
    }

    /**
     * Check if viewer has fully completed watching
     */
    public boolean hasCompletedWatching(User viewer, User profileOwner) {
        return watchRepo.hasCompletedWatching(viewer, profileOwner);
    }

    /**
     * Record that a user started watching a video
     */
    @Transactional
    public void recordVideoWatch(User viewer, User profileOwner) {
        Optional<VideoIntroWatch> existing = watchRepo.findByViewerAndProfileOwner(viewer, profileOwner);

        if (existing.isEmpty()) {
            VideoIntroWatch watch = new VideoIntroWatch();
            watch.setViewer(viewer);
            watch.setProfileOwner(profileOwner);
            watch.setWatchedAt(new Date());
            watch.setCompleted(false);
            watchRepo.save(watch);
            LOGGER.debug("Recorded video watch start: viewer={}, owner={}", viewer.getId(), profileOwner.getId());
        }
    }

    /**
     * Update watch progress (duration and completion status)
     */
    @Transactional
    public void updateWatchProgress(User viewer, User profileOwner, int durationSeconds, boolean completed) {
        Optional<VideoIntroWatch> existing = watchRepo.findByViewerAndProfileOwner(viewer, profileOwner);

        if (existing.isPresent()) {
            VideoIntroWatch watch = existing.get();
            watch.setWatchDurationSeconds(durationSeconds);
            if (completed && !watch.getCompleted()) {
                watch.setCompleted(true);
                LOGGER.info("User {} completed watching video for user {}", viewer.getId(), profileOwner.getId());
            }
            watchRepo.save(watch);
        } else {
            // Record new watch with progress
            VideoIntroWatch watch = new VideoIntroWatch();
            watch.setViewer(viewer);
            watch.setProfileOwner(profileOwner);
            watch.setWatchedAt(new Date());
            watch.setWatchDurationSeconds(durationSeconds);
            watch.setCompleted(completed);
            watchRepo.save(watch);
        }
    }

    /**
     * Populate the videoWatched field in UserDto based on current user
     */
    public void populateVideoWatchedStatus(UserDto dto, User viewer, User profileOwner) {
        if (dto.isHasVideoIntro()) {
            dto.setVideoWatched(hasWatchedVideo(viewer, profileOwner));
        } else {
            dto.setVideoWatched(true); // No video, so considered "watched"
        }
    }

    /**
     * Determine if photos should be blurred for the viewer
     * Photos are blurred if:
     * 1. Profile owner has a video intro
     * 2. Profile owner requires video-first viewing
     * 3. Viewer has not watched the video
     */
    public boolean shouldBlurPhotos(User viewer, User profileOwner) {
        // No video intro means photos are visible
        if (profileOwner.getVideoIntroduction() == null) {
            return false;
        }

        // If owner doesn't require video-first, photos are visible
        if (!profileOwner.isRequireVideoFirst()) {
            return false;
        }

        // If viewer has watched the video, photos are visible
        return !hasWatchedVideo(viewer, profileOwner);
    }

    /**
     * Get watch statistics for a profile
     */
    public WatchStats getWatchStats(User profileOwner) {
        long totalWatches = watchRepo.countByProfileOwner(profileOwner);
        long completedWatches = watchRepo.countByProfileOwnerAndCompletedTrue(profileOwner);

        return new WatchStats(totalWatches, completedWatches);
    }

    /**
     * Statistics about video watches
     */
    public static class WatchStats {
        public final long totalWatches;
        public final long completedWatches;
        public final double completionRate;

        public WatchStats(long totalWatches, long completedWatches) {
            this.totalWatches = totalWatches;
            this.completedWatches = completedWatches;
            this.completionRate = totalWatches > 0 ? (double) completedWatches / totalWatches : 0;
        }
    }
}
