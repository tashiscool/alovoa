package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.MentalHealthResource;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.AccountPause;
import com.nonononoki.alovoa.entity.user.InterventionDelivery;
import com.nonononoki.alovoa.entity.user.RadicalizationEvent;
import com.nonononoki.alovoa.entity.user.UserNotification;
import com.nonononoki.alovoa.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates intervention delivery for the radicalization prevention system.
 *
 * Philosophy: This is NOT about punishment. It's about offering support and
 * resources to users who may be going through a difficult time. Every intervention
 * is framed as care, not judgment.
 */
@Service
public class InterventionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterventionService.class);

    @Value("${app.intervention.tier3.pause.hours:24}")
    private int tier3PauseHours;

    @Autowired
    private InterventionDeliveryRepository interventionRepo;

    @Autowired
    private AccountPauseRepository pauseRepo;

    @Autowired
    private MentalHealthResourceRepository resourceRepo;

    @Autowired
    private UserNotificationRepository notificationRepo;

    @Autowired
    private UserRepository userRepo;

    /**
     * Send intervention message based on tier and event.
     * This is the main entry point for the intervention system.
     */
    @Transactional
    public InterventionDelivery sendIntervention(User user, RadicalizationEvent event, int tier, String message) {
        LOGGER.info("Sending tier {} intervention to user {}", tier, user.getId());

        InterventionDelivery.MessageType messageType;
        InterventionDelivery.DeliveryChannel channel;
        String notificationType;

        switch (tier) {
            case 1:
                // Tier 1: Log only, no direct intervention
                LOGGER.info("Tier 1 event for user {} - monitoring only", user.getId());
                return null;

            case 2:
                messageType = InterventionDelivery.MessageType.GENTLE_REDIRECT;
                channel = InterventionDelivery.DeliveryChannel.IN_APP_NOTIFICATION;
                notificationType = UserNotification.INTERVENTION_GENTLE;
                break;

            case 3:
                messageType = InterventionDelivery.MessageType.CRISIS_INTERVENTION;
                channel = InterventionDelivery.DeliveryChannel.IN_APP_MODAL;
                notificationType = UserNotification.INTERVENTION_CRISIS;
                // For tier 3, we'll also trigger an account pause with resources
                break;

            default:
                LOGGER.warn("Unknown intervention tier: {}", tier);
                return null;
        }

        // Create intervention delivery record
        InterventionDelivery delivery = new InterventionDelivery();
        delivery.setUser(user);
        delivery.setRadicalizationEvent(event);
        delivery.setInterventionTier(tier);
        delivery.setMessageType(messageType);
        delivery.setMessageContent(message);
        delivery.setDeliveryChannel(channel);
        interventionRepo.save(delivery);

        // Create in-app notification
        createNotification(user, notificationType, message);

        // For tier 3, also create account pause with resources
        if (tier == 3) {
            createProtectivePause(user, message);
        }

        LOGGER.info("Intervention {} delivered to user {} via {}",
                delivery.getUuid(), user.getId(), channel);

        return delivery;
    }

    /**
     * Create an in-app notification for the user
     */
    private void createNotification(User user, String notificationType, String message) {
        UserNotification notification = new UserNotification();
        notification.setUserTo(user);
        notification.setUserFrom(null); // System notification
        notification.setContent(notificationType);
        notification.setNotificationType(notificationType);
        notification.setMessage(message);
        notification.setDate(new Date());
        notification.setReadStatus(false);
        notificationRepo.save(notification);
    }

    /**
     * Create a protective pause for Tier 3 situations.
     * This is NOT punishment - it's a protective break with resources.
     */
    @Transactional
    public AccountPause createProtectivePause(User user, String context) {
        // Check if user already has an active pause
        Optional<AccountPause> existingPause = pauseRepo.findByUserAndResumedAtIsNull(user);
        if (existingPause.isPresent()) {
            LOGGER.info("User {} already has active pause, skipping", user.getId());
            return existingPause.get();
        }

        // Get mental health resources for user's country
        String countryCode = user.getCountry();
        List<MentalHealthResource> resources = getResourcesForUser(countryCode);
        String resourcesJson = formatResourcesForUser(resources);

        // Create pause
        AccountPause pause = new AccountPause();
        pause.setUser(user);
        pause.setPauseReason("We noticed some patterns that suggest you might be going through a difficult time. " +
                "We're pausing your account briefly so you can take a break and access some helpful resources.");
        pause.setPauseType(AccountPause.PauseType.PROTECTIVE_BREAK);
        pause.setPauseUntil(Date.from(Instant.now().plus(tier3PauseHours, ChronoUnit.HOURS)));
        pause.setResourcesProvided(resourcesJson);
        pause.setCanAppeal(true);
        pauseRepo.save(pause);

        // Update user
        user.setAccountPaused(true);
        user.setPauseReason(pause.getPauseReason());
        user.setCurrentPause(pause);
        userRepo.save(user);

        // Send account pause notification with resources
        sendAccountPauseNotification(user, pause, resources);

        LOGGER.warn("Created protective pause for user {} until {}",
                user.getId(), pause.getPauseUntil());

        return pause;
    }

    /**
     * Send the account pause notification with mental health resources
     */
    private void sendAccountPauseNotification(User user, AccountPause pause, List<MentalHealthResource> resources) {
        StringBuilder message = new StringBuilder();
        message.append("We care about your wellbeing. ");
        message.append("Your account is taking a brief break until ");
        message.append(pause.getPauseUntil().toString());
        message.append(".\n\n");
        message.append("Here are some resources that might help:\n\n");

        for (MentalHealthResource resource : resources) {
            message.append("- ").append(resource.getName());
            if (resource.getContactInfo() != null) {
                message.append(": ").append(resource.getContactInfo());
            }
            if (resource.getAvailable247()) {
                message.append(" (Available 24/7)");
            }
            message.append("\n");
        }

        message.append("\nRemember: Reaching out is a sign of strength, not weakness.");

        createNotification(user, UserNotification.ACCOUNT_PAUSE_NOTICE, message.toString());

        // Also create an intervention delivery record for tracking
        InterventionDelivery delivery = new InterventionDelivery();
        delivery.setUser(user);
        delivery.setInterventionTier(3);
        delivery.setMessageType(InterventionDelivery.MessageType.ACCOUNT_PAUSE_NOTICE);
        delivery.setMessageContent(message.toString());
        delivery.setDeliveryChannel(InterventionDelivery.DeliveryChannel.IN_APP_MODAL);
        interventionRepo.save(delivery);
    }

    /**
     * Get mental health resources appropriate for user's location
     */
    public List<MentalHealthResource> getResourcesForUser(String countryCode) {
        List<MentalHealthResource> resources = new ArrayList<>();

        // Get country-specific resources first
        if (countryCode != null && !countryCode.isEmpty()) {
            resources.addAll(resourceRepo.findByCountryCodeAndActiveTrueOrderByPriorityDesc(countryCode));
        }

        // Add international resources
        resources.addAll(resourceRepo.findByResourceTypeAndActiveTrueOrderByPriorityDesc(
                MentalHealthResource.ResourceType.INTERNATIONAL));

        // If no country-specific resources, get US resources as fallback (most comprehensive)
        if (resources.isEmpty()) {
            resources.addAll(resourceRepo.findByCountryCodeAndActiveTrueOrderByPriorityDesc("US"));
        }

        // Limit to top 5 most relevant
        return resources.stream()
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Format resources as JSON for storage
     */
    private String formatResourcesForUser(List<MentalHealthResource> resources) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < resources.size(); i++) {
            MentalHealthResource r = resources.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"name\":\"").append(escapeJson(r.getName())).append("\",")
              .append("\"type\":\"").append(r.getResourceType().name()).append("\",")
              .append("\"contact\":\"").append(escapeJson(r.getContactInfo())).append("\",")
              .append("\"url\":\"").append(escapeJson(r.getUrl())).append("\",")
              .append("\"available247\":").append(r.getAvailable247())
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"").replace("\n", "\\n");
    }

    /**
     * Resume an account pause - called when pause expires or user is ready
     */
    @Transactional
    public void resumeAccount(User user, String reason) {
        Optional<AccountPause> pauseOpt = pauseRepo.findByUserAndResumedAtIsNull(user);
        if (pauseOpt.isEmpty()) {
            LOGGER.warn("No active pause found for user {}", user.getId());
            return;
        }

        AccountPause pause = pauseOpt.get();
        pause.setResumedAt(new Date());
        pause.setResumedReason(reason);
        pauseRepo.save(pause);

        user.setAccountPaused(false);
        user.setPauseReason(null);
        user.setCurrentPause(null);
        userRepo.save(user);

        // Send welcome back message
        String welcomeMessage = "Welcome back! We're glad you're here. " +
                "Remember, the resources we shared are always available if you need them. " +
                "We're here to help you have a positive experience.";

        createNotification(user, UserNotification.RECOVERY_WELCOME, welcomeMessage);

        // Track the recovery
        InterventionDelivery delivery = new InterventionDelivery();
        delivery.setUser(user);
        delivery.setInterventionTier(0);
        delivery.setMessageType(InterventionDelivery.MessageType.RECOVERY_WELCOME);
        delivery.setMessageContent(welcomeMessage);
        delivery.setDeliveryChannel(InterventionDelivery.DeliveryChannel.IN_APP_NOTIFICATION);
        interventionRepo.save(delivery);

        LOGGER.info("Account resumed for user {} - reason: {}", user.getId(), reason);
    }

    /**
     * Mark that user clicked on resources in intervention
     */
    @Transactional
    public void markResourcesClicked(User user, UUID interventionUuid) {
        Optional<InterventionDelivery> deliveryOpt = interventionRepo.findByUuid(interventionUuid);
        if (deliveryOpt.isPresent()) {
            InterventionDelivery delivery = deliveryOpt.get();
            if (delivery.getUser().getId().equals(user.getId())) {
                delivery.setResourcesClicked(true);
                interventionRepo.save(delivery);
                LOGGER.info("User {} clicked resources for intervention {}", user.getId(), interventionUuid);
            }
        }
    }

    /**
     * Scheduled task to auto-resume expired pauses
     */
    @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
    @Transactional
    public void processExpiredPauses() {
        List<AccountPause> expiredPauses = pauseRepo.findExpiredPauses(new Date());

        for (AccountPause pause : expiredPauses) {
            resumeAccount(pause.getUser(), "Pause period expired - automatic resume");
        }

        if (!expiredPauses.isEmpty()) {
            LOGGER.info("Auto-resumed {} expired account pauses", expiredPauses.size());
        }
    }

    /**
     * Offer resources proactively to a user who might benefit
     */
    @Transactional
    public void offerResources(User user, String context) {
        // Don't offer if we've sent resources recently
        Date oneDayAgo = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        boolean recentOffer = interventionRepo.existsByUserAndMessageTypeAndDeliveredAtAfter(
                user, InterventionDelivery.MessageType.RESOURCE_OFFER, oneDayAgo);

        if (recentOffer) {
            LOGGER.debug("Skipping resource offer for user {} - recent offer exists", user.getId());
            return;
        }

        List<MentalHealthResource> resources = getResourcesForUser(user.getCountry());

        StringBuilder message = new StringBuilder();
        message.append("Dating can be challenging sometimes. Here are some resources that might help:\n\n");

        for (MentalHealthResource resource : resources.stream().limit(3).collect(Collectors.toList())) {
            message.append("- ").append(resource.getName());
            if (resource.getContactInfo() != null) {
                message.append(": ").append(resource.getContactInfo());
            }
            message.append("\n");
        }

        message.append("\nThere's no shame in seeking support.");

        createNotification(user, UserNotification.RESOURCE_OFFER, message.toString());

        InterventionDelivery delivery = new InterventionDelivery();
        delivery.setUser(user);
        delivery.setInterventionTier(0);
        delivery.setMessageType(InterventionDelivery.MessageType.RESOURCE_OFFER);
        delivery.setMessageContent(message.toString());
        delivery.setDeliveryChannel(InterventionDelivery.DeliveryChannel.IN_APP_NOTIFICATION);
        interventionRepo.save(delivery);

        LOGGER.info("Offered resources to user {} - context: {}", user.getId(), context);
    }

    /**
     * Get intervention statistics for admin dashboard
     */
    public Map<String, Object> getInterventionStats() {
        Map<String, Object> stats = new HashMap<>();

        Date thirtyDaysAgo = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        Date now = new Date();

        stats.put("tier2InterventionsLast30Days",
                interventionRepo.countByInterventionTierAndDeliveredAtBetween(2, thirtyDaysAgo, now));
        stats.put("tier3InterventionsLast30Days",
                interventionRepo.countByInterventionTierAndDeliveredAtBetween(3, thirtyDaysAgo, now));
        stats.put("resourcesClickedLast30Days",
                interventionRepo.countByResourcesClickedTrueAndDeliveredAtBetween(thirtyDaysAgo, now));
        stats.put("protectivePausesLast30Days",
                pauseRepo.countByPauseTypeAndPausedAtBetween(
                        AccountPause.PauseType.PROTECTIVE_BREAK, thirtyDaysAgo, now));

        return stats;
    }
}
