package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.user.Message;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent.BehaviorType;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.entity.user.UserReputationScore.TrustLevel;
import com.nonononoki.alovoa.entity.user.UserAppeal;
import com.nonononoki.alovoa.entity.user.UserAppeal.AppealType;
import com.nonononoki.alovoa.entity.user.UserAppeal.AppealStatus;
import com.nonononoki.alovoa.entity.user.UserAppeal.AppealOutcome;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.MessageRepository;
import com.nonononoki.alovoa.repo.UserBehaviorEventRepository;
import com.nonononoki.alovoa.repo.UserReputationScoreRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.UserAppealRepository;
import com.nonononoki.alovoa.repo.UserAccountabilityReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReputationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReputationService.class);

    private static final Map<BehaviorType, Double> BEHAVIOR_IMPACTS = Map.ofEntries(
            // Positive
            Map.entry(BehaviorType.THOUGHTFUL_MESSAGE, 0.5),
            Map.entry(BehaviorType.PROMPT_RESPONSE, 0.3),
            Map.entry(BehaviorType.SCHEDULED_DATE, 1.0),
            Map.entry(BehaviorType.COMPLETED_DATE, 2.0),
            Map.entry(BehaviorType.POSITIVE_FEEDBACK, 2.5),
            Map.entry(BehaviorType.GRACEFUL_DECLINE, 0.5),
            Map.entry(BehaviorType.PROFILE_COMPLETE, 1.0),
            Map.entry(BehaviorType.VIDEO_VERIFIED, 3.0),
            // Negative
            Map.entry(BehaviorType.LOW_EFFORT_MESSAGE, -0.3),
            Map.entry(BehaviorType.SLOW_RESPONSE, -0.2),
            Map.entry(BehaviorType.GHOSTING, -2.0),
            Map.entry(BehaviorType.NO_SHOW, -5.0),
            Map.entry(BehaviorType.NEGATIVE_FEEDBACK, -2.0),
            Map.entry(BehaviorType.REPORTED, -1.0),
            Map.entry(BehaviorType.REPORT_UPHELD, -10.0),
            Map.entry(BehaviorType.INAPPROPRIATE_CONTENT, -5.0),
            Map.entry(BehaviorType.MISREPRESENTATION, -15.0)
    );

    @Autowired
    private UserReputationScoreRepository reputationRepo;

    @Autowired
    private UserBehaviorEventRepository behaviorRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private MessageRepository messageRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAppealRepository appealRepo;

    @Autowired
    private UserAccountabilityReportRepository reportRepo;

    // Appeal mechanism constants
    private static final int APPEAL_COOLDOWN_DAYS = 30;
    private static final int PROBATION_PERIOD_DAYS = 90;
    private static final int TIME_DECAY_MONTHS = 6;

    // Track which conversations have already been flagged for ghosting (to avoid duplicate flags)
    private final Set<String> flaggedGhostingConversations = Collections.synchronizedSet(new HashSet<>());

    public void recordBehavior(User user, BehaviorType type, User targetUser, Map<String, Object> data) {
        try {
            double baseImpact = BEHAVIOR_IMPACTS.getOrDefault(type, 0.0);

            // Apply decay for repeated behaviors (last 7 days)
            long recentCount = behaviorRepo.countByUserAndBehaviorTypeAndCreatedAtAfter(
                    user, type, Date.from(Instant.now().minus(7, ChronoUnit.DAYS))
            );
            double decayFactor = 1.0 / (1.0 + recentCount * 0.2);
            double actualImpact = baseImpact * decayFactor;

            // Record event
            UserBehaviorEvent event = new UserBehaviorEvent();
            event.setUser(user);
            event.setBehaviorType(type);
            event.setTargetUser(targetUser);
            if (data != null) {
                event.setEventData(objectMapper.writeValueAsString(data));
            }
            event.setReputationImpact(actualImpact);
            behaviorRepo.save(event);

            // Update reputation scores
            updateReputationScores(user, type, actualImpact);

        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to record behavior event", e);
        }
    }

    private void updateReputationScores(User user, BehaviorType type, double impact) {
        UserReputationScore rep = reputationRepo.findByUser(user)
                .orElseGet(() -> createDefaultReputation(user));

        switch (type) {
            case THOUGHTFUL_MESSAGE:
            case PROMPT_RESPONSE:
            case LOW_EFFORT_MESSAGE:
            case SLOW_RESPONSE:
                rep.setResponseQuality(clamp(rep.getResponseQuality() + impact, 0, 100));
                break;

            case GHOSTING:
            case GRACEFUL_DECLINE:
            case REPORTED:
            case REPORT_UPHELD:
            case INAPPROPRIATE_CONTENT:
                rep.setRespectScore(clamp(rep.getRespectScore() + impact, 0, 100));
                if (type == BehaviorType.GHOSTING) {
                    rep.setGhostingCount(rep.getGhostingCount() + 1);
                }
                if (type == BehaviorType.REPORTED) {
                    rep.setReportsReceived(rep.getReportsReceived() + 1);
                }
                if (type == BehaviorType.REPORT_UPHELD) {
                    rep.setReportsUpheld(rep.getReportsUpheld() + 1);
                }
                break;

            case VIDEO_VERIFIED:
            case MISREPRESENTATION:
            case PROFILE_COMPLETE:
                rep.setAuthenticityScore(clamp(rep.getAuthenticityScore() + impact, 0, 100));
                break;

            case SCHEDULED_DATE:
            case COMPLETED_DATE:
            case NO_SHOW:
            case POSITIVE_FEEDBACK:
            case NEGATIVE_FEEDBACK:
                rep.setInvestmentScore(clamp(rep.getInvestmentScore() + impact, 0, 100));
                if (type == BehaviorType.COMPLETED_DATE) {
                    rep.setDatesCompleted(rep.getDatesCompleted() + 1);
                }
                if (type == BehaviorType.POSITIVE_FEEDBACK) {
                    rep.setPositiveFeedbackCount(rep.getPositiveFeedbackCount() + 1);
                }
                break;
        }

        // Update trust level
        rep.setTrustLevel(calculateTrustLevel(rep, user));
        reputationRepo.save(rep);
    }

    /**
     * Calculate trust level based on reputation score and incident patterns.
     *
     * IMPORTANT: Requires pattern (3+ serious incidents) before RESTRICTED status
     * to prevent single-incident punishment and allow for learning/mistakes.
     */
    private TrustLevel calculateTrustLevel(UserReputationScore rep, User user) {
        double overall = rep.getOverallScore();
        long accountAgeDays = 0;
        if (user.getDates() != null && user.getDates().getCreationDate() != null) {
            accountAgeDays = ChronoUnit.DAYS.between(
                    user.getDates().getCreationDate().toInstant(),
                    Instant.now()
            );
        }

        // Preserve PROBATION status if user is still on probation
        if (rep.getProbationUntil() != null && rep.getProbationUntil().after(new Date())) {
            return TrustLevel.PROBATION;
        }

        if (accountAgeDays < 30) return TrustLevel.NEW_MEMBER;

        // RESTRICTED requires BOTH low score AND pattern of 3+ serious incidents
        // This prevents single-incident punishment and allows for learning
        if (overall < 30) {
            int seriousIncidentCount = countSeriousIncidents(user);
            if (seriousIncidentCount >= 3) {
                return TrustLevel.RESTRICTED;
            }
            // With low score but < 3 incidents, use UNDER_REVIEW instead
            return TrustLevel.UNDER_REVIEW;
        }

        if (overall < 50) return TrustLevel.UNDER_REVIEW;
        if (overall >= 80 && accountAgeDays >= 180) return TrustLevel.HIGHLY_TRUSTED;
        if (overall >= 65 && accountAgeDays >= 90) return TrustLevel.TRUSTED;
        return TrustLevel.VERIFIED;
    }

    /**
     * Count serious incidents (report upheld, misrepresentation, no-shows, etc.)
     * Used to determine if RESTRICTED status is warranted.
     */
    private int countSeriousIncidents(User user) {
        // Count upheld reports
        int upheldReports = user.getReputationScore() != null
                ? user.getReputationScore().getReportsUpheld()
                : 0;

        // Count serious behavior events from the last year
        Date oneYearAgo = Date.from(Instant.now().minus(365, ChronoUnit.DAYS));
        long seriousBehaviorEvents = behaviorRepo.countByUserAndBehaviorTypeInAndCreatedAtAfter(
                user,
                List.of(
                        BehaviorType.REPORT_UPHELD,
                        BehaviorType.MISREPRESENTATION,
                        BehaviorType.NO_SHOW,
                        BehaviorType.INAPPROPRIATE_CONTENT
                ),
                oneYearAgo
        );

        return upheldReports + (int) seriousBehaviorEvents;
    }

    private UserReputationScore createDefaultReputation(User user) {
        UserReputationScore rep = new UserReputationScore();
        rep.setUser(user);
        rep.setResponseQuality(50.0);
        rep.setRespectScore(50.0);
        rep.setAuthenticityScore(50.0);
        rep.setInvestmentScore(50.0);
        rep.setTrustLevel(TrustLevel.NEW_MEMBER);
        return reputationRepo.save(rep);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public UserReputationScore getOrCreateReputation(User user) {
        return reputationRepo.findByUser(user)
                .orElseGet(() -> createDefaultReputation(user));
    }

    public List<UserBehaviorEvent> getRecentBehavior(User user, int days) {
        Date cutoff = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
        return behaviorRepo.findByUserAndCreatedAtAfter(user, cutoff);
    }

    /**
     * Scheduled task to detect ghosting behavior.
     * Ghosting is defined as: receiving a message and not responding for 72+ hours
     * while the user has been active on the platform (logged in, viewed profiles, etc.)
     */
    @Scheduled(cron = "0 0 * * * *")  // Every hour
    @Transactional
    public void detectGhostingBehavior() {
        LOGGER.info("Running ghosting detection...");
        Date cutoff = Date.from(Instant.now().minus(72, ChronoUnit.HOURS));
        int ghostingCount = 0;

        try {
            // Find conversations with no recent activity
            List<Conversation> inactiveConversations = conversationRepo.findConversationsWithNoRecentActivity(cutoff);

            for (Conversation conversation : inactiveConversations) {
                if (conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
                    continue;
                }

                // Get the last message in the conversation
                Optional<Message> lastMessageOpt = messageRepo.findLastMessageInConversation(conversation);
                if (lastMessageOpt.isEmpty()) {
                    continue;
                }

                Message lastMessage = lastMessageOpt.get();
                User recipient = lastMessage.getUserTo();
                User sender = lastMessage.getUserFrom();

                if (recipient == null || sender == null) {
                    continue;
                }

                // Check if this conversation was already flagged
                String flagKey = conversation.getId() + "_" + recipient.getId() + "_" + lastMessage.getDate().getTime();
                if (flaggedGhostingConversations.contains(flagKey)) {
                    continue;
                }

                // Check if the message was sent before cutoff (72+ hours ago)
                if (lastMessage.getDate() == null || lastMessage.getDate().after(cutoff)) {
                    continue;
                }

                // Check if recipient has responded with at least 2 messages in the conversation
                // (We only count ghosting if there was an established conversation)
                long recipientMessageCount = messageRepo.countByConversationAndUserFrom(conversation, recipient);
                if (recipientMessageCount < 2) {
                    // Not established enough to be considered ghosting - could be just not interested
                    continue;
                }

                // Check if the recipient was active recently (logged in, etc.)
                // If they haven't logged in, they might just be busy/away
                if (recipient.getDates() != null && recipient.getDates().getActiveDate() != null) {
                    Date lastActive = recipient.getDates().getActiveDate();
                    if (lastActive.before(cutoff)) {
                        // User hasn't been active, so it's not ghosting - just inactive
                        continue;
                    }
                }

                // This appears to be ghosting - recipient received message, was active, but didn't respond
                LOGGER.debug("Detected potential ghosting: user {} in conversation {}",
                        recipient.getId(), conversation.getId());

                // Record the ghosting behavior
                recordBehavior(recipient, BehaviorType.GHOSTING, sender, Map.of(
                        "conversationId", conversation.getId(),
                        "lastMessageDate", lastMessage.getDate().getTime(),
                        "hoursSinceMessage", ChronoUnit.HOURS.between(
                                lastMessage.getDate().toInstant(), Instant.now())
                ));

                // Mark as flagged to avoid duplicate detection
                flaggedGhostingConversations.add(flagKey);
                ghostingCount++;

                // Clean up old flags (older than 30 days)
                cleanupOldGhostingFlags();
            }

            LOGGER.info("Ghosting detection completed. Detected {} instances.", ghostingCount);

        } catch (Exception e) {
            LOGGER.error("Error during ghosting detection", e);
        }
    }

    /**
     * Clean up ghosting flags older than 30 days to prevent memory buildup
     */
    private void cleanupOldGhostingFlags() {
        // Since we use a simple Set without timestamps, we'll clear it periodically
        // A more sophisticated implementation would use a cache with TTL
        if (flaggedGhostingConversations.size() > 10000) {
            LOGGER.info("Clearing ghosting flags cache (size: {})", flaggedGhostingConversations.size());
            flaggedGhostingConversations.clear();
        }
    }

    /**
     * Clear a ghosting flag when a user responds (called from message service)
     */
    public void clearGhostingFlag(Conversation conversation, User user) {
        // Remove any flags for this user in this conversation
        flaggedGhostingConversations.removeIf(flag ->
                flag.startsWith(conversation.getId() + "_" + user.getId() + "_"));
    }

    // === Appeals Mechanism Methods ===

    /**
     * Submit an appeal for a RESTRICTED user.
     * Enforces 30-day cooldown between appeals.
     */
    @Transactional
    public UserAppeal submitAppeal(User user, AppealType appealType, String appealReason,
                                    String supportingStatement, String linkedReportUuid) {
        UserReputationScore rep = getOrCreateReputation(user);

        // Check eligibility
        if (rep.getTrustLevel() != TrustLevel.RESTRICTED && rep.getTrustLevel() != TrustLevel.UNDER_REVIEW) {
            throw new IllegalStateException("Only users with RESTRICTED or UNDER_REVIEW status can submit appeals");
        }

        if (Boolean.TRUE.equals(rep.getAppealPending())) {
            throw new IllegalStateException("You already have a pending appeal");
        }

        // Check cooldown
        if (rep.getLastAppealedAt() != null) {
            Date cooldownEnd = Date.from(rep.getLastAppealedAt().toInstant().plus(APPEAL_COOLDOWN_DAYS, ChronoUnit.DAYS));
            if (new Date().before(cooldownEnd)) {
                long daysRemaining = ChronoUnit.DAYS.between(Instant.now(), cooldownEnd.toInstant());
                throw new IllegalStateException("You must wait " + daysRemaining + " more days before submitting another appeal");
            }
        }

        // Create appeal
        UserAppeal appeal = new UserAppeal();
        appeal.setUser(user);
        appeal.setAppealType(appealType);
        appeal.setAppealReason(appealReason);
        appeal.setSupportingStatement(supportingStatement);
        appeal.setStatus(AppealStatus.PENDING);

        // Link report if applicable
        if (linkedReportUuid != null && !linkedReportUuid.isBlank() && appealType == AppealType.REPORT_APPEAL) {
            UserAccountabilityReport report = reportRepo.findByUuid(java.util.UUID.fromString(linkedReportUuid))
                    .orElse(null);
            if (report != null && report.getSubject().equals(user)) {
                appeal.setLinkedReport(report);
            }
        }

        // Update reputation score
        rep.setLastAppealedAt(new Date());
        rep.setAppealPending(true);
        reputationRepo.save(rep);

        return appealRepo.save(appeal);
    }

    /**
     * Get appeal eligibility for a user.
     */
    public Map<String, Object> getAppealEligibility(User user) {
        UserReputationScore rep = getOrCreateReputation(user);
        Map<String, Object> result = new HashMap<>();

        boolean canAppeal = (rep.getTrustLevel() == TrustLevel.RESTRICTED || rep.getTrustLevel() == TrustLevel.UNDER_REVIEW)
                && !Boolean.TRUE.equals(rep.getAppealPending());

        result.put("canAppeal", canAppeal);
        result.put("currentTrustLevel", rep.getTrustLevel().name());
        result.put("hasActivePendingAppeal", Boolean.TRUE.equals(rep.getAppealPending()));

        if (rep.getLastAppealedAt() != null) {
            Date cooldownEnd = Date.from(rep.getLastAppealedAt().toInstant().plus(APPEAL_COOLDOWN_DAYS, ChronoUnit.DAYS));
            result.put("lastAppealedAt", rep.getLastAppealedAt());
            result.put("cooldownEndsAt", cooldownEnd);
            result.put("canAppealAfterCooldown", new Date().after(cooldownEnd));
        }

        if (rep.getProbationUntil() != null) {
            result.put("onProbation", true);
            result.put("probationEndsAt", rep.getProbationUntil());
        }

        return result;
    }

    /**
     * Get user's appeals.
     */
    public List<UserAppeal> getUserAppeals(User user) {
        return appealRepo.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * Get appeal by UUID.
     */
    public UserAppeal getAppealByUuid(java.util.UUID uuid) {
        return appealRepo.findByUuid(uuid).orElse(null);
    }

    /**
     * Withdraw a pending appeal.
     */
    @Transactional
    public void withdrawAppeal(User user, java.util.UUID appealUuid) {
        UserAppeal appeal = appealRepo.findByUuid(appealUuid)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found"));

        if (!appeal.getUser().equals(user)) {
            throw new IllegalArgumentException("You can only withdraw your own appeals");
        }

        if (appeal.getStatus() != AppealStatus.PENDING && appeal.getStatus() != AppealStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Can only withdraw pending or under-review appeals");
        }

        appeal.setStatus(AppealStatus.WITHDRAWN);
        appealRepo.save(appeal);

        // Clear pending flag
        UserReputationScore rep = getOrCreateReputation(user);
        rep.setAppealPending(false);
        reputationRepo.save(rep);
    }

    /**
     * Get pending appeals for admin review.
     */
    public Page<UserAppeal> getPendingAppeals(int page, int size) {
        return appealRepo.findByStatus(AppealStatus.PENDING, PageRequest.of(page, size));
    }

    /**
     * Process an appeal (admin action).
     */
    @Transactional
    public void processAppeal(java.util.UUID appealUuid, User admin, boolean approved,
                              AppealOutcome outcome, String reviewNotes) {
        UserAppeal appeal = appealRepo.findByUuid(appealUuid)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found"));

        appeal.setReviewedBy(admin);
        appeal.setReviewedAt(new Date());
        appeal.setReviewNotes(reviewNotes);
        appeal.setOutcome(outcome);

        User user = appeal.getUser();
        UserReputationScore rep = getOrCreateReputation(user);

        if (approved) {
            appeal.setStatus(AppealStatus.APPROVED);

            // Set probation period
            Date probationEnd = Date.from(Instant.now().plus(PROBATION_PERIOD_DAYS, ChronoUnit.DAYS));
            appeal.setProbationEndDate(probationEnd);
            rep.setProbationUntil(probationEnd);
            rep.setTrustLevel(TrustLevel.PROBATION);

            // Handle specific outcomes
            if (outcome == AppealOutcome.REPUTATION_RESTORED && appeal.getLinkedReport() != null) {
                reverseReportImpact(appeal.getLinkedReport(), rep);
            }
        } else {
            appeal.setStatus(AppealStatus.DENIED);
        }

        // Clear pending flag
        rep.setAppealPending(false);
        reputationRepo.save(rep);
        appealRepo.save(appeal);
    }

    /**
     * Reverse the reputation impact of a report when appeal is approved.
     */
    private void reverseReportImpact(UserAccountabilityReport report, UserReputationScore rep) {
        if (report.getReputationImpact() != null) {
            // Reverse the negative impact
            double reversal = Math.abs(report.getReputationImpact());
            rep.setRespectScore(clamp(rep.getRespectScore() + reversal, 0, 100));
            rep.setReportsUpheld(Math.max(0, rep.getReportsUpheld() - 1));
        }
    }

    /**
     * Scheduled task to graduate users from probation to VERIFIED.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void graduateProbationUsers() {
        LOGGER.info("Running probation graduation check...");

        List<UserAppeal> readyForGraduation = appealRepo.findUsersReadyForProbationGraduation(new Date());
        int graduated = 0;

        for (UserAppeal appeal : readyForGraduation) {
            User user = appeal.getUser();
            UserReputationScore rep = reputationRepo.findByUser(user).orElse(null);

            if (rep == null || rep.getTrustLevel() != TrustLevel.PROBATION) {
                continue;
            }

            // Check if user had any new incidents during probation
            Date probationStart = Date.from(appeal.getReviewedAt().toInstant());
            long recentReports = behaviorRepo.countByUserAndBehaviorTypeAndCreatedAtAfter(
                    user, BehaviorType.REPORT_UPHELD, probationStart
            );

            if (recentReports == 0) {
                // Clean probation - graduate to VERIFIED
                rep.setTrustLevel(TrustLevel.VERIFIED);
                rep.setProbationUntil(null);
                reputationRepo.save(rep);
                graduated++;
                LOGGER.info("User {} graduated from probation to VERIFIED", user.getId());
            } else {
                // Had incidents during probation - stay in PROBATION with extended period
                rep.setProbationUntil(Date.from(Instant.now().plus(PROBATION_PERIOD_DAYS, ChronoUnit.DAYS)));
                reputationRepo.save(rep);
                LOGGER.warn("User {} had {} incidents during probation, extending probation period",
                        user.getId(), recentReports);
            }
        }

        LOGGER.info("Probation graduation completed. {} users graduated.", graduated);
    }

    /**
     * Scheduled task to apply time decay on old negative reputation events.
     * Runs weekly on Sundays at 4 AM.
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    @Transactional
    public void applyTimeDecay() {
        LOGGER.info("Running reputation time decay...");

        Date decayCutoff = Date.from(Instant.now().minus(TIME_DECAY_MONTHS * 30, ChronoUnit.DAYS));
        int decayApplied = 0;

        // Find users who haven't had decay applied recently and have old negative events
        List<UserReputationScore> eligibleForDecay = reputationRepo.findAll().stream()
                .filter(rep -> rep.getTimeDecayAppliedAt() == null ||
                        rep.getTimeDecayAppliedAt().before(decayCutoff))
                .filter(rep -> rep.getOverallScore() < 50)  // Only help users who are struggling
                .toList();

        for (UserReputationScore rep : eligibleForDecay) {
            // Apply decay: boost scores by 2 points (max 50)
            double decayBoost = 2.0;

            if (rep.getResponseQuality() < 50) {
                rep.setResponseQuality(Math.min(50, rep.getResponseQuality() + decayBoost));
            }
            if (rep.getRespectScore() < 50) {
                rep.setRespectScore(Math.min(50, rep.getRespectScore() + decayBoost));
            }
            if (rep.getAuthenticityScore() < 50) {
                rep.setAuthenticityScore(Math.min(50, rep.getAuthenticityScore() + decayBoost));
            }
            if (rep.getInvestmentScore() < 50) {
                rep.setInvestmentScore(Math.min(50, rep.getInvestmentScore() + decayBoost));
            }

            rep.setTimeDecayAppliedAt(new Date());

            // Recalculate trust level
            rep.setTrustLevel(calculateTrustLevel(rep, rep.getUser()));
            reputationRepo.save(rep);
            decayApplied++;
        }

        LOGGER.info("Time decay completed. Applied to {} users.", decayApplied);
    }

    /**
     * Scheduled task to expire old pending appeals (30 days without review).
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void expireOldAppeals() {
        LOGGER.info("Running appeal expiration check...");

        Date cutoff = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        List<UserAppeal> expiredAppeals = appealRepo.findExpiredPendingAppeals(cutoff);
        int expired = 0;

        for (UserAppeal appeal : expiredAppeals) {
            appeal.setStatus(AppealStatus.EXPIRED);
            appealRepo.save(appeal);

            // Clear pending flag
            UserReputationScore rep = reputationRepo.findByUser(appeal.getUser()).orElse(null);
            if (rep != null) {
                rep.setAppealPending(false);
                reputationRepo.save(rep);
            }
            expired++;
        }

        LOGGER.info("Appeal expiration completed. {} appeals expired.", expired);
    }
}
