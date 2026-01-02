package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.CompatibilityScore;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.entity.MatchWindow;
import com.nonononoki.alovoa.entity.MatchWindow.WindowStatus;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import com.nonononoki.alovoa.repo.CompatibilityScoreRepository;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.MatchWindowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing 24-hour match decision windows.
 *
 * This implements the "Known" app's best feature: time-boxed decisions
 * that prevent endless chat purgatory and ghosting.
 *
 * Flow:
 * 1. When compatibility score is high enough, create a match window
 * 2. Both users see the match in their "Pending Decisions" queue
 * 3. They have 24 hours to confirm or decline
 * 4. If both confirm -> conversation opens
 * 5. If either declines or time expires -> match archived
 */
@Service
public class MatchWindowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchWindowService.class);

    @Autowired
    private MatchWindowRepository windowRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private CompatibilityScoreRepository compatibilityRepo;

    @Autowired
    private AuthService authService;

    @Autowired
    private ReputationService reputationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DonationService donationService;

    // ============================================
    // Creating Match Windows
    // ============================================

    /**
     * Create a match window between two users.
     * Called when compatibility score exceeds threshold.
     */
    @Transactional
    public MatchWindow createWindow(User userA, User userB, Double compatibilityScore) throws Exception {
        // Check if window already exists
        if (windowRepo.existsActiveWindowBetween(userA, userB)) {
            throw new Exception("Active match window already exists between these users");
        }

        MatchWindow window = new MatchWindow();
        window.setUserA(userA);
        window.setUserB(userB);
        window.setCompatibilityScore(compatibilityScore);
        window.setStatus(WindowStatus.PENDING_BOTH);

        window = windowRepo.save(window);

        // Notify both users
        notifyNewMatch(window);

        LOGGER.info("Created match window {} between users {} and {} ({}% compatible)",
                window.getUuid(), userA.getId(), userB.getId(),
                Math.round(compatibilityScore * 100));

        return window;
    }

    /**
     * Create windows for all high-compatibility matches for a user.
     * Called after user completes assessment or daily match refresh.
     */
    @Transactional
    public List<MatchWindow> createWindowsForHighMatches(User user, double minCompatibility) {
        List<CompatibilityScore> topMatches = compatibilityRepo.findByUserAOrderByOverallScoreDesc(user);
        List<MatchWindow> created = new ArrayList<>();

        for (CompatibilityScore score : topMatches) {
            if (score.getOverallScore() == null || score.getOverallScore() < minCompatibility) {
                continue;
            }

            User otherUser = score.getUserB();

            // Skip if window already exists
            if (windowRepo.existsActiveWindowBetween(user, otherUser)) {
                continue;
            }

            try {
                MatchWindow window = createWindow(user, otherUser, score.getOverallScore());
                created.add(window);
            } catch (Exception e) {
                LOGGER.warn("Could not create window for users {} and {}: {}",
                        user.getId(), otherUser.getId(), e.getMessage());
            }
        }

        return created;
    }

    // ============================================
    // User Actions
    // ============================================

    /**
     * User confirms interest in a match.
     */
    @Transactional
    public MatchWindow confirmInterest(UUID windowUuid) throws Exception {
        User currentUser = authService.getCurrentUser(true);
        MatchWindow window = windowRepo.findByUuid(windowUuid)
                .orElseThrow(() -> new Exception("Match window not found"));

        validateUserInWindow(currentUser, window);

        if (window.isExpired()) {
            window.setStatus(WindowStatus.EXPIRED);
            windowRepo.save(window);
            throw new Exception("This match window has expired");
        }

        // Record confirmation
        if (currentUser.getId().equals(window.getUserA().getId())) {
            window.setUserAConfirmed(true);
            window.setUserAConfirmedAt(new Date());
        } else {
            window.setUserBConfirmed(true);
            window.setUserBConfirmedAt(new Date());
        }

        // Update status
        if (window.isBothConfirmed()) {
            window.setStatus(WindowStatus.CONFIRMED);
            // Create conversation
            Conversation conversation = createConversationForMatch(window);
            window.setConversation(conversation);

            // Record positive behavior
            reputationService.recordBehavior(window.getUserA(),
                    UserBehaviorEvent.BehaviorType.SCHEDULED_DATE,
                    window.getUserB(), null);
            reputationService.recordBehavior(window.getUserB(),
                    UserBehaviorEvent.BehaviorType.SCHEDULED_DATE,
                    window.getUserA(), null);

            // Show donation prompt after match window is confirmed
            donationService.showAfterMatchPrompt(currentUser);

            notifyMatchConfirmed(window);
        } else {
            // Update to waiting on the other user
            if (window.isUserAConfirmed()) {
                window.setStatus(WindowStatus.PENDING_USER_B);
            } else {
                window.setStatus(WindowStatus.PENDING_USER_A);
            }
            notifyPartnerPending(window, currentUser);
        }

        return windowRepo.save(window);
    }

    /**
     * User declines a match.
     */
    @Transactional
    public MatchWindow declineMatch(UUID windowUuid) throws Exception {
        User currentUser = authService.getCurrentUser(true);
        MatchWindow window = windowRepo.findByUuid(windowUuid)
                .orElseThrow(() -> new Exception("Match window not found"));

        validateUserInWindow(currentUser, window);

        // Record decline
        if (currentUser.getId().equals(window.getUserA().getId())) {
            window.setStatus(WindowStatus.DECLINED_BY_A);
        } else {
            window.setStatus(WindowStatus.DECLINED_BY_B);
        }

        // Record graceful decline (positive behavior)
        reputationService.recordBehavior(currentUser,
                UserBehaviorEvent.BehaviorType.GRACEFUL_DECLINE,
                window.getOtherUser(currentUser), null);

        LOGGER.info("User {} declined match window {}", currentUser.getId(), windowUuid);

        return windowRepo.save(window);
    }

    /**
     * Request a 12-hour extension on the window.
     * Each window can only be extended once.
     */
    @Transactional
    public MatchWindow requestExtension(UUID windowUuid) throws Exception {
        User currentUser = authService.getCurrentUser(true);
        MatchWindow window = windowRepo.findByUuid(windowUuid)
                .orElseThrow(() -> new Exception("Match window not found"));

        validateUserInWindow(currentUser, window);

        if (!window.canExtend()) {
            throw new Exception("This window cannot be extended");
        }

        if (window.isExpired()) {
            throw new Exception("This window has already expired");
        }

        // Add extension time
        long newExpiry = window.getExpiresAt().getTime() +
                (MatchWindow.EXTENSION_HOURS * 60 * 60 * 1000L);
        window.setExpiresAt(new Date(newExpiry));
        window.setExtensionUsed(true);
        window.setExtensionRequestedBy(currentUser);

        // Notify the other user
        notifyExtensionRequested(window, currentUser);

        LOGGER.info("User {} extended match window {} by {} hours",
                currentUser.getId(), windowUuid, MatchWindow.EXTENSION_HOURS);

        return windowRepo.save(window);
    }

    // ============================================
    // Query Methods
    // ============================================

    /**
     * Get all pending decisions for current user.
     */
    public List<MatchWindow> getPendingDecisions() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        return windowRepo.findPendingWindowsForUser(user);
    }

    /**
     * Get matches where user is waiting on the other person.
     */
    public List<MatchWindow> getWaitingMatches() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        return windowRepo.findWaitingWindowsForUser(user);
    }

    /**
     * Get confirmed matches (ready for conversation).
     */
    public List<MatchWindow> getConfirmedMatches() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        return windowRepo.findConfirmedWindowsForUser(user);
    }

    /**
     * Get count of pending decisions (for notification badge).
     */
    public int getPendingCount() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        return windowRepo.countPendingDecisions(user);
    }

    /**
     * Get window by UUID.
     */
    public Optional<MatchWindow> getWindow(UUID uuid) {
        return windowRepo.findByUuid(uuid);
    }

    // ============================================
    // Scheduled Tasks
    // ============================================

    /**
     * Expire windows that have passed their deadline.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void expireWindows() {
        List<MatchWindow> expired = windowRepo.findExpiredWindows(new Date());

        for (MatchWindow window : expired) {
            window.setStatus(WindowStatus.EXPIRED);
            windowRepo.save(window);

            // If one user confirmed but the other didn't, that's ghosting
            if (window.isUserAConfirmed() && !window.isUserBConfirmed()) {
                reputationService.recordBehavior(window.getUserB(),
                        UserBehaviorEvent.BehaviorType.GHOSTING,
                        window.getUserA(), null);
            } else if (window.isUserBConfirmed() && !window.isUserAConfirmed()) {
                reputationService.recordBehavior(window.getUserA(),
                        UserBehaviorEvent.BehaviorType.GHOSTING,
                        window.getUserB(), null);
            }

            LOGGER.info("Expired match window {}", window.getUuid());
        }

        if (!expired.isEmpty()) {
            LOGGER.info("Expired {} match windows", expired.size());
        }
    }

    /**
     * Send reminders for windows expiring in the next 4 hours.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void sendExpirationReminders() {
        Date now = new Date();
        Date fourHoursFromNow = new Date(now.getTime() + (4 * 60 * 60 * 1000L));

        List<MatchWindow> expiringSoon = windowRepo.findWindowsExpiringSoon(now, fourHoursFromNow);

        for (MatchWindow window : expiringSoon) {
            if (!window.isUserAConfirmed()) {
                notifyExpirationReminder(window, window.getUserA());
            }
            if (!window.isUserBConfirmed()) {
                notifyExpirationReminder(window, window.getUserB());
            }
        }
    }

    // ============================================
    // Private Helpers
    // ============================================

    private void validateUserInWindow(User user, MatchWindow window) throws Exception {
        if (!user.getId().equals(window.getUserA().getId()) &&
            !user.getId().equals(window.getUserB().getId())) {
            throw new Exception("You are not part of this match");
        }
    }

    private Conversation createConversationForMatch(MatchWindow window) {
        // Check if conversation already exists
        Optional<Conversation> existing = conversationRepo.findByUsers(
                window.getUserA().getId(), window.getUserB().getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Conversation conversation = new Conversation();
        conversation.setUsers(Arrays.asList(window.getUserA(), window.getUserB()));
        conversation.setLastUpdated(new Date());
        return conversationRepo.save(conversation);
    }

    // ============================================
    // Notification Helpers (stubs - implement based on your notification system)
    // ============================================

    private void notifyNewMatch(MatchWindow window) {
        // TODO: Send push/email notification to both users about new match
        LOGGER.debug("Would notify users about new match window {}", window.getUuid());
    }

    private void notifyPartnerPending(MatchWindow window, User whoConfirmed) {
        // TODO: Notify the other user that their match confirmed
        User other = window.getOtherUser(whoConfirmed);
        LOGGER.debug("Would notify user {} that {} confirmed interest", other.getId(), whoConfirmed.getId());
    }

    private void notifyMatchConfirmed(MatchWindow window) {
        // TODO: Notify both users that match is confirmed
        LOGGER.debug("Would notify both users about confirmed match {}", window.getUuid());
    }

    private void notifyExtensionRequested(MatchWindow window, User requester) {
        // TODO: Notify other user about extension
        LOGGER.debug("Would notify about extension request for window {}", window.getUuid());
    }

    private void notifyExpirationReminder(MatchWindow window, User user) {
        // TODO: Send reminder that window is expiring soon
        LOGGER.debug("Would send expiration reminder to user {} for window {}",
                user.getId(), window.getUuid());
    }
}
