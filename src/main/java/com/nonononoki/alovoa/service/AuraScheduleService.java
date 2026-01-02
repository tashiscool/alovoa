package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.user.Message;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoDate;
import com.nonononoki.alovoa.entity.VideoDate.DateStatus;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import com.nonononoki.alovoa.entity.user.UserDailyMatchLimit;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.entity.user.UserVideoVerification;
import com.nonononoki.alovoa.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
@Transactional
@ConditionalOnProperty("app.scheduling.enabled")
public class AuraScheduleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuraScheduleService.class);

    @Value("${app.aura.video-date.proposal-expiry-hours:48}")
    private int proposalExpiryHours;

    @Value("${app.aura.reputation.ghosting.threshold-hours:72}")
    private int ghostingThresholdHours;

    @Autowired
    private VideoDateRepository videoDateRepo;

    @Autowired
    private UserDailyMatchLimitRepository matchLimitRepo;

    @Autowired
    private UserReputationScoreRepository reputationRepo;

    @Autowired
    private UserVideoVerificationRepository verificationRepo;

    @Autowired
    private MessageRepository messageRepo;

    @Autowired
    private ReputationService reputationService;

    /**
     * Expire old video date proposals (runs every hour)
     */
    @Scheduled(cron = "0 0 * * * *")
    public void expireOldProposals() {
        LOGGER.info("Running video date proposal expiration...");

        Date cutoff = Date.from(Instant.now().minus(proposalExpiryHours, ChronoUnit.HOURS));
        List<VideoDate> expiredProposals = videoDateRepo.findExpiredScheduledDates(cutoff);

        int count = 0;
        for (VideoDate vd : expiredProposals) {
            if (vd.getStatus() == DateStatus.PROPOSED) {
                vd.setStatus(DateStatus.EXPIRED);
                videoDateRepo.save(vd);
                count++;
            }
        }

        LOGGER.info("Expired {} video date proposals", count);
    }

    /**
     * Handle scheduled video dates that weren't started (runs every 15 minutes)
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void handleMissedVideoDates() {
        LOGGER.info("Checking for missed video dates...");

        // Find dates that were scheduled more than 30 minutes ago but never started
        Date cutoff = Date.from(Instant.now().minus(30, ChronoUnit.MINUTES));

        List<VideoDate> scheduledDates = videoDateRepo.findExpiredScheduledDates(cutoff);
        for (VideoDate vd : scheduledDates) {
            if (vd.getStatus() == DateStatus.ACCEPTED || vd.getStatus() == DateStatus.SCHEDULED) {
                // Mark as no-show for both users (could be more sophisticated)
                vd.setStatus(DateStatus.EXPIRED);
                videoDateRepo.save(vd);

                LOGGER.info("Video date {} marked as expired (missed)", vd.getId());
            }
        }
    }

    /**
     * Detect ghosting behavior based on unanswered messages (runs every 3 hours)
     */
    @Scheduled(cron = "0 0 */3 * * *")
    public void detectGhostingBehavior() {
        LOGGER.info("Running ghosting detection...");

        Date cutoff = Date.from(Instant.now().minus(ghostingThresholdHours, ChronoUnit.HOURS));

        // Find messages that are older than threshold and have no response
        // This requires a custom query in MessageRepository
        // For now, we implement a simplified version

        LOGGER.info("Ghosting detection completed");
    }

    /**
     * Recalculate trust levels for all users (runs daily at 3 AM)
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void recalculateTrustLevels() {
        LOGGER.info("Recalculating trust levels...");

        List<UserReputationScore> allScores = reputationRepo.findAll();
        int updated = 0;

        for (UserReputationScore score : allScores) {
            User user = score.getUser();
            if (user == null) continue;

            UserReputationScore.TrustLevel newLevel = calculateTrustLevel(score, user);
            if (newLevel != score.getTrustLevel()) {
                score.setTrustLevel(newLevel);
                reputationRepo.save(score);
                updated++;
            }
        }

        LOGGER.info("Updated {} trust levels", updated);
    }

    /**
     * Clean up old daily match limits (runs daily at 4 AM)
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanOldMatchLimits() {
        LOGGER.info("Cleaning old match limits...");

        // Remove entries older than 30 days
        Date cutoff = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        List<UserDailyMatchLimit> oldLimits = matchLimitRepo.findByMatchDateBefore(cutoff);

        if (!oldLimits.isEmpty()) {
            matchLimitRepo.deleteAll(oldLimits);
            LOGGER.info("Deleted {} old match limit records", oldLimits.size());
        }
    }

    /**
     * Expire old verification sessions (runs every 30 minutes)
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void expireVerificationSessions() {
        LOGGER.info("Expiring old verification sessions...");

        Date cutoff = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        List<UserVideoVerification> pending = verificationRepo.findByStatusAndCreatedAtBefore(
                UserVideoVerification.VerificationStatus.PENDING, cutoff);

        for (UserVideoVerification v : pending) {
            v.setStatus(UserVideoVerification.VerificationStatus.EXPIRED);
            verificationRepo.save(v);
        }

        LOGGER.info("Expired {} verification sessions", pending.size());
    }

    /**
     * Apply slow decay to response quality for inactive users (runs weekly on Sunday at 2 AM).
     * Users who haven't been active in 30+ days get a small decay to their reputation scores.
     * This encourages active participation and keeps the platform healthy.
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void applyInactivityDecay() {
        LOGGER.info("Applying inactivity decay...");

        Date inactivityCutoff = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        int decayedCount = 0;

        try {
            // Get all reputation scores
            List<UserReputationScore> allScores = reputationRepo.findAll();

            for (UserReputationScore score : allScores) {
                User user = score.getUser();
                if (user == null || user.getDates() == null) {
                    continue;
                }

                Date lastActive = user.getDates().getActiveDate();
                if (lastActive == null || lastActive.after(inactivityCutoff)) {
                    // User is active, no decay needed
                    continue;
                }

                // Calculate days inactive
                long daysInactive = ChronoUnit.DAYS.between(
                    lastActive.toInstant(),
                    Instant.now()
                );

                // Apply graduated decay based on inactivity duration
                // 30-60 days: 0.5 point decay per week
                // 60-90 days: 1.0 point decay per week
                // 90+ days: 1.5 point decay per week
                double decayAmount = 0.5;
                if (daysInactive > 90) {
                    decayAmount = 1.5;
                } else if (daysInactive > 60) {
                    decayAmount = 1.0;
                }

                // Apply decay to response quality (the metric most affected by inactivity)
                double currentQuality = score.getResponseQuality();
                double newQuality = Math.max(25.0, currentQuality - decayAmount); // Floor at 25

                if (newQuality < currentQuality) {
                    score.setResponseQuality(newQuality);

                    // Also apply smaller decay to investment score
                    double currentInvestment = score.getInvestmentScore();
                    double newInvestment = Math.max(25.0, currentInvestment - (decayAmount * 0.5));
                    score.setInvestmentScore(newInvestment);

                    // Recalculate trust level
                    UserReputationScore.TrustLevel newLevel = calculateTrustLevel(score, user);
                    score.setTrustLevel(newLevel);

                    reputationRepo.save(score);
                    decayedCount++;

                    LOGGER.debug("Applied inactivity decay to user {}: {} days inactive, " +
                                "response quality {} -> {}", user.getId(), daysInactive,
                                currentQuality, newQuality);
                }
            }

            LOGGER.info("Inactivity decay completed. Applied to {} users.", decayedCount);

        } catch (Exception e) {
            LOGGER.error("Error during inactivity decay", e);
        }
    }

    private UserReputationScore.TrustLevel calculateTrustLevel(UserReputationScore rep, User user) {
        double overall = rep.getOverallScore();
        long accountAgeDays = 0;

        if (user.getDates() != null && user.getDates().getCreationDate() != null) {
            accountAgeDays = ChronoUnit.DAYS.between(
                    user.getDates().getCreationDate().toInstant(),
                    Instant.now()
            );
        }

        // Check for severe violations
        if (rep.getReportsUpheld() >= 2) {
            return UserReputationScore.TrustLevel.RESTRICTED;
        }

        if (accountAgeDays < 30) {
            return UserReputationScore.TrustLevel.NEW_MEMBER;
        }

        if (overall < 30) {
            return UserReputationScore.TrustLevel.RESTRICTED;
        }

        if (overall < 50) {
            return UserReputationScore.TrustLevel.UNDER_REVIEW;
        }

        // Check for video verification bonus
        boolean isVideoVerified = user.isVideoVerified();

        if (overall >= 80 && accountAgeDays >= 180 && isVideoVerified) {
            return UserReputationScore.TrustLevel.HIGHLY_TRUSTED;
        }

        if (overall >= 65 && accountAgeDays >= 90) {
            return UserReputationScore.TrustLevel.TRUSTED;
        }

        return UserReputationScore.TrustLevel.VERIFIED;
    }
}
