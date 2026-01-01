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
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.MessageRepository;
import com.nonononoki.alovoa.repo.UserBehaviorEventRepository;
import com.nonononoki.alovoa.repo.UserReputationScoreRepository;
import com.nonononoki.alovoa.repo.UserRepository;
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

    private TrustLevel calculateTrustLevel(UserReputationScore rep, User user) {
        double overall = rep.getOverallScore();
        long accountAgeDays = 0;
        if (user.getDates() != null && user.getDates().getCreationDate() != null) {
            accountAgeDays = ChronoUnit.DAYS.between(
                    user.getDates().getCreationDate().toInstant(),
                    Instant.now()
            );
        }

        if (accountAgeDays < 30) return TrustLevel.NEW_MEMBER;
        if (overall < 30) return TrustLevel.RESTRICTED;
        if (overall < 50) return TrustLevel.UNDER_REVIEW;
        if (overall >= 80 && accountAgeDays >= 180) return TrustLevel.HIGHLY_TRUSTED;
        if (overall >= 65 && accountAgeDays >= 90) return TrustLevel.TRUSTED;
        return TrustLevel.VERIFIED;
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
}
