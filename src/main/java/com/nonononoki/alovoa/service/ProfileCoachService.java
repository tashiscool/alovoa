package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ProfileCoachingMessage;
import com.nonononoki.alovoa.entity.user.ProfileEditDailySummary;
import com.nonononoki.alovoa.entity.user.ProfileEditEvent;
import com.nonononoki.alovoa.repo.ProfileCoachingMessageRepository;
import com.nonononoki.alovoa.repo.ProfileEditDailySummaryRepository;
import com.nonononoki.alovoa.repo.ProfileEditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Profile Coach Service - Tracks editing frequency and provides coaching.
 * Part of AURA's anti-optimization design philosophy.
 *
 * Philosophy: Frequent profile editing often indicates someone is treating
 * dating like a game to be optimized. This service gently discourages that
 * behavior while encouraging authenticity.
 */
@Service
public class ProfileCoachService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileCoachService.class);

    // Threshold for "excessive" edits per day
    @Value("${app.profile-coach.daily-edit-threshold:5}")
    private int dailyEditThreshold;

    // Cooldown between coaching messages of the same type (hours)
    @Value("${app.profile-coach.message-cooldown-hours:24}")
    private int messageCooldownHours;

    @Autowired
    private ProfileEditEventRepository editEventRepo;

    @Autowired
    private ProfileEditDailySummaryRepository dailySummaryRepo;

    @Autowired
    private ProfileCoachingMessageRepository coachingMessageRepo;

    // Coaching messages
    private static final Map<ProfileCoachingMessage.MessageType, List<String>> COACHING_MESSAGES = new HashMap<>();

    static {
        COACHING_MESSAGES.put(ProfileCoachingMessage.MessageType.FREQUENT_EDITS, Arrays.asList(
            "You've been making a lot of profile changes today. Remember, the best connections come from being authentically yourself, not from optimizing your profile.",
            "We noticed you're editing your profile frequently. Take a breath - you're already great as you are. Constant tweaking can actually work against you.",
            "Profile tip: The most successful users focus on being genuine rather than perfect. Your authentic self is more attractive than any optimized version."
        ));

        COACHING_MESSAGES.put(ProfileCoachingMessage.MessageType.PHOTO_OPTIMIZATION, Arrays.asList(
            "You've changed your photos several times today. While it's good to have quality photos, constant swapping can indicate insecurity. Trust your choices!",
            "Photo tip: Pick photos that show the real you, not the 'most attractive' version. Authenticity builds better connections."
        ));

        COACHING_MESSAGES.put(ProfileCoachingMessage.MessageType.BIO_TWEAKING, Arrays.asList(
            "Your bio has been updated multiple times. Remember: honesty and authenticity attract compatible matches better than clever wording.",
            "Bio tip: Write something true about yourself once, then leave it. The right person will appreciate who you really are."
        ));

        COACHING_MESSAGES.put(ProfileCoachingMessage.MessageType.AUTHENTICITY_REMINDER, Arrays.asList(
            "Dating apps work best when you show the real you. Optimization strategies often backfire by attracting the wrong people.",
            "The goal isn't to get the most matches - it's to find someone compatible. Being yourself is the fastest path there."
        ));

        COACHING_MESSAGES.put(ProfileCoachingMessage.MessageType.SUCCESS_TIP, Arrays.asList(
            "Successful profiles have one thing in common: authenticity. The people who find love are those who show their true selves.",
            "Fun fact: Users who edit their profiles less frequently tend to have higher quality matches. Quality over optimization!"
        ));
    }

    /**
     * Record a profile edit event
     */
    @Transactional
    public void recordEdit(User user, ProfileEditEvent.EditType editType, String fieldEdited) {
        LocalDate today = LocalDate.now();

        // Record the event
        ProfileEditEvent event = new ProfileEditEvent();
        event.setUser(user);
        event.setEditType(editType);
        event.setFieldEdited(fieldEdited);
        event.setEditDate(today);
        event.setEditTimestamp(new Date());
        editEventRepo.save(event);

        // Update daily summary
        ProfileEditDailySummary summary = dailySummaryRepo.findByUserAndEditDate(user, today)
                .orElseGet(() -> {
                    ProfileEditDailySummary newSummary = new ProfileEditDailySummary();
                    newSummary.setUser(user);
                    newSummary.setEditDate(today);
                    return newSummary;
                });

        summary.incrementEdit(editType);
        dailySummaryRepo.save(summary);

        // Check if coaching is needed
        if (summary.exceedsThreshold(dailyEditThreshold) && !summary.getCoachingSent()) {
            sendCoachingMessage(user, summary);
            summary.setCoachingSent(true);
            dailySummaryRepo.save(summary);
        }

        LOGGER.debug("Recorded {} edit for user {} (total today: {})",
                editType, user.getId(), summary.getTotalEdits());
    }

    /**
     * Send an appropriate coaching message based on editing pattern
     */
    @Transactional
    public void sendCoachingMessage(User user, ProfileEditDailySummary summary) {
        ProfileCoachingMessage.MessageType messageType;
        String triggerReason;

        // Determine message type based on edit pattern
        if (summary.getPhotoEdits() > summary.getBioEdits() && summary.getPhotoEdits() > summary.getPromptEdits()) {
            messageType = ProfileCoachingMessage.MessageType.PHOTO_OPTIMIZATION;
            triggerReason = "Multiple photo changes: " + summary.getPhotoEdits();
        } else if (summary.getBioEdits() > 2) {
            messageType = ProfileCoachingMessage.MessageType.BIO_TWEAKING;
            triggerReason = "Multiple bio updates: " + summary.getBioEdits();
        } else {
            messageType = ProfileCoachingMessage.MessageType.FREQUENT_EDITS;
            triggerReason = "Total edits exceeded threshold: " + summary.getTotalEdits();
        }

        // Check cooldown
        Date cooldownCutoff = Date.from(Instant.now().minus(messageCooldownHours, ChronoUnit.HOURS));
        if (coachingMessageRepo.existsByUserAndMessageTypeAndSentAtAfter(user, messageType, cooldownCutoff)) {
            LOGGER.debug("Skipping coaching message - user {} received {} recently", user.getId(), messageType);
            return;
        }

        // Select a random message
        List<String> messages = COACHING_MESSAGES.get(messageType);
        String messageContent = messages.get(new Random().nextInt(messages.size()));

        // Create and save the message
        ProfileCoachingMessage message = new ProfileCoachingMessage();
        message.setUser(user);
        message.setMessageType(messageType);
        message.setMessageContent(messageContent);
        message.setTriggerReason(triggerReason);
        message.setSentAt(new Date());
        coachingMessageRepo.save(message);

        LOGGER.info("Sent {} coaching message to user {}: {}", messageType, user.getId(), triggerReason);
    }

    /**
     * Get active coaching messages for a user
     */
    public List<ProfileCoachingMessage> getActiveMessages(User user) {
        return coachingMessageRepo.findByUserAndDismissedFalseOrderBySentAtDesc(user);
    }

    /**
     * Dismiss a coaching message
     */
    @Transactional
    public void dismissMessage(UUID messageUuid, Boolean helpful) {
        coachingMessageRepo.findByUuid(messageUuid).ifPresent(message -> {
            message.setDismissed(true);
            message.setDismissedAt(new Date());
            message.setHelpfulFeedback(helpful);
            coachingMessageRepo.save(message);
        });
    }

    /**
     * Get editing statistics for a user
     */
    public EditingStats getEditingStats(User user) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        LocalDate monthAgo = today.minusDays(30);

        long todayEdits = editEventRepo.countByUserAndEditDate(user, today);
        long weekEdits = dailySummaryRepo.getTotalEditsInPeriod(user, weekAgo);
        long monthEdits = dailySummaryRepo.getTotalEditsInPeriod(user, monthAgo);
        Double avgDaily = dailySummaryRepo.getAverageDailyEdits(user);

        return new EditingStats(todayEdits, weekEdits, monthEdits, avgDaily != null ? avgDaily : 0);
    }

    /**
     * Scheduled task to send daily summary coaching
     * Runs at end of day to catch any users who exceeded threshold
     */
    @Scheduled(cron = "0 0 23 * * *")
    @Transactional
    public void processDailyCoaching() {
        LOGGER.info("Running daily profile coaching check...");

        LocalDate today = LocalDate.now();
        List<ProfileEditDailySummary> needsCoaching = dailySummaryRepo.findSummariesNeedingCoaching(today, dailyEditThreshold);

        int processed = 0;
        for (ProfileEditDailySummary summary : needsCoaching) {
            sendCoachingMessage(summary.getUser(), summary);
            summary.setCoachingSent(true);
            dailySummaryRepo.save(summary);
            processed++;
        }

        LOGGER.info("Daily coaching check complete. Sent {} coaching messages.", processed);
    }

    /**
     * Editing statistics
     */
    public static class EditingStats {
        public final long todayEdits;
        public final long weekEdits;
        public final long monthEdits;
        public final double averageDailyEdits;

        public EditingStats(long todayEdits, long weekEdits, long monthEdits, double averageDailyEdits) {
            this.todayEdits = todayEdits;
            this.weekEdits = weekEdits;
            this.monthEdits = monthEdits;
            this.averageDailyEdits = averageDailyEdits;
        }
    }
}
