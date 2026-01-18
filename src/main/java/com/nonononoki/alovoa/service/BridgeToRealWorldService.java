package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoDate;
import com.nonononoki.alovoa.entity.user.*;
import com.nonononoki.alovoa.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for "Bridge to Real World" features.
 * Helps users transition from app interactions to real-world connections.
 *
 * Philosophy: Dating apps should help people leave them. Success is measured
 * by meaningful real-world connections, not engagement metrics.
 */
@Service
public class BridgeToRealWorldService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeToRealWorldService.class);

    @Autowired
    private DateVenueSuggestionRepository suggestionRepo;

    @Autowired
    private RealWorldDateRepository realWorldDateRepo;

    @Autowired
    private PostDateFeedbackRepository feedbackRepo;

    @Autowired
    private RelationshipMilestoneRepository milestoneRepo;

    @Autowired
    private VideoDateRepository videoDateRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private UserNotificationRepository notificationRepo;

    // ==================== DATE VENUE SUGGESTIONS ====================

    /**
     * Generate date venue suggestions based on shared interests between two users.
     */
    @Transactional
    public List<DateVenueSuggestion> generateVenueSuggestions(User userA, User userB, Conversation conversation) {
        List<DateVenueSuggestion> suggestions = new ArrayList<>();

        // Find shared interests
        Set<String> sharedInterests = findSharedInterests(userA, userB);

        if (sharedInterests.isEmpty()) {
            // Default suggestions for getting to know each other
            suggestions.add(createSuggestion(userA, userB, conversation,
                    "Coffee Shop",
                    "A casual coffee date",
                    Collections.emptyList(),
                    "Coffee dates are perfect for first meetings - low pressure, easy to extend or end."));
            suggestions.add(createSuggestion(userA, userB, conversation,
                    "Walk in the Park",
                    "A scenic outdoor walk",
                    Collections.emptyList(),
                    "Walking dates feel natural and make conversation easier."));
        } else {
            // Generate suggestions based on shared interests
            for (String interest : sharedInterests) {
                DateVenueSuggestion suggestion = createSuggestionForInterest(
                        userA, userB, conversation, interest);
                if (suggestion != null) {
                    suggestions.add(suggestion);
                }
            }
        }

        // Save and return top 3
        List<DateVenueSuggestion> toSave = suggestions.stream()
                .limit(3)
                .collect(Collectors.toList());

        return suggestionRepo.saveAll(toSave);
    }

    private Set<String> findSharedInterests(User userA, User userB) {
        Set<String> interestsA = new HashSet<>();
        Set<String> interestsB = new HashSet<>();

        if (userA.getInterests() != null) {
            userA.getInterests().forEach(i -> interestsA.add(i.getText().toLowerCase()));
        }
        if (userB.getInterests() != null) {
            userB.getInterests().forEach(i -> interestsB.add(i.getText().toLowerCase()));
        }

        interestsA.retainAll(interestsB);
        return interestsA;
    }

    private DateVenueSuggestion createSuggestionForInterest(
            User userA, User userB, Conversation conversation, String interest) {

        String category;
        String description;
        String reason;

        // Map common interests to venue categories
        String lowerInterest = interest.toLowerCase();

        if (lowerInterest.contains("music") || lowerInterest.contains("concert")) {
            category = "Live Music Venue";
            description = "Check out a local live music show together";
            reason = "You both enjoy music - experiencing it live could be memorable!";
        } else if (lowerInterest.contains("food") || lowerInterest.contains("cooking") || lowerInterest.contains("restaurant")) {
            category = "Restaurant";
            description = "Try a new restaurant or cuisine together";
            reason = "You both appreciate food - discovering a new spot together could be fun!";
        } else if (lowerInterest.contains("art") || lowerInterest.contains("museum")) {
            category = "Museum or Gallery";
            description = "Visit a local art exhibit or museum";
            reason = "You both enjoy art - walking through a gallery gives natural conversation topics.";
        } else if (lowerInterest.contains("hiking") || lowerInterest.contains("outdoor") || lowerInterest.contains("nature")) {
            category = "Nature Trail";
            description = "Go for a scenic hike or nature walk";
            reason = "You both love the outdoors - fresh air and nature make great date settings!";
        } else if (lowerInterest.contains("book") || lowerInterest.contains("reading")) {
            category = "Bookstore Cafe";
            description = "Browse a bookstore and grab coffee";
            reason = "You both love reading - you can share book recommendations over coffee.";
        } else if (lowerInterest.contains("movie") || lowerInterest.contains("film")) {
            category = "Independent Cinema";
            description = "Watch a film at an indie theater, then discuss over dinner";
            reason = "You both enjoy films - but save discussion for after so you can actually talk!";
        } else if (lowerInterest.contains("game") || lowerInterest.contains("board game")) {
            category = "Board Game Cafe";
            description = "Play games together at a board game cafe";
            reason = "You both enjoy games - a little friendly competition can be fun!";
        } else if (lowerInterest.contains("fitness") || lowerInterest.contains("gym") || lowerInterest.contains("yoga")) {
            category = "Active Date";
            description = "Try a fitness class together or go rock climbing";
            reason = "You both value fitness - doing something active together can be energizing!";
        } else {
            // Generic suggestion based on the interest
            category = "Activity based on: " + interest;
            description = "Find something related to your shared interest in " + interest;
            reason = "You both enjoy " + interest + " - find something local that celebrates this!";
        }

        return createSuggestion(userA, userB, conversation, category, description,
                Collections.singletonList(interest), reason);
    }

    private DateVenueSuggestion createSuggestion(User userA, User userB, Conversation conversation,
                                                  String category, String description,
                                                  List<String> interests, String reason) {
        DateVenueSuggestion suggestion = new DateVenueSuggestion();
        suggestion.setUserA(userA);
        suggestion.setUserB(userB);
        suggestion.setConversation(conversation);
        suggestion.setVenueCategory(category);
        suggestion.setVenueDescription(description);
        suggestion.setMatchingInterests(String.join(",", interests));
        suggestion.setReason(reason);
        return suggestion;
    }

    /**
     * Get active suggestions for a conversation
     */
    public List<DateVenueSuggestion> getSuggestionsForConversation(Conversation conversation) {
        return suggestionRepo.findByConversationAndDismissedFalseOrderBySuggestedAtDesc(conversation);
    }

    /**
     * Accept a venue suggestion
     */
    @Transactional
    public DateVenueSuggestion acceptSuggestion(Long suggestionId) {
        DateVenueSuggestion suggestion = suggestionRepo.findById(suggestionId)
                .orElseThrow(() -> new RuntimeException("Suggestion not found"));
        suggestion.setAccepted(true);
        return suggestionRepo.save(suggestion);
    }

    /**
     * Dismiss a venue suggestion
     */
    @Transactional
    public void dismissSuggestion(Long suggestionId) {
        DateVenueSuggestion suggestion = suggestionRepo.findById(suggestionId)
                .orElseThrow(() -> new RuntimeException("Suggestion not found"));
        suggestion.setDismissed(true);
        suggestionRepo.save(suggestion);
    }

    // ==================== POST-DATE FEEDBACK ====================

    /**
     * Submit feedback after a video or real-world date
     */
    @Transactional
    public PostDateFeedback submitFeedback(User fromUser, UUID dateUuid, Map<String, Object> feedbackData, boolean isVideoDate) {
        PostDateFeedback feedback = new PostDateFeedback();
        feedback.setFromUser(fromUser);

        if (isVideoDate) {
            VideoDate videoDate = videoDateRepo.findById(Long.parseLong(dateUuid.toString()))
                    .orElseThrow(() -> new RuntimeException("Video date not found"));
            feedback.setVideoDate(videoDate);
            feedback.setAboutUser(videoDate.getUserA().getId().equals(fromUser.getId())
                    ? videoDate.getUserB() : videoDate.getUserA());
        } else {
            RealWorldDate realDate = realWorldDateRepo.findByUuid(dateUuid)
                    .orElseThrow(() -> new RuntimeException("Date not found"));
            feedback.setRealWorldDate(realDate);
            feedback.setAboutUser(realDate.getUserA().getId().equals(fromUser.getId())
                    ? realDate.getUserB() : realDate.getUserA());
        }

        // Parse feedback data
        if (feedbackData.containsKey("overallRating")) {
            feedback.setOverallRating(((Number) feedbackData.get("overallRating")).intValue());
        }
        if (feedbackData.containsKey("wouldSeeAgain")) {
            feedback.setWouldSeeAgain((Boolean) feedbackData.get("wouldSeeAgain"));
        }
        if (feedbackData.containsKey("chemistryRating")) {
            feedback.setChemistryRating(((Number) feedbackData.get("chemistryRating")).intValue());
        }
        if (feedbackData.containsKey("conversationRating")) {
            feedback.setConversationRating(((Number) feedbackData.get("conversationRating")).intValue());
        }
        if (feedbackData.containsKey("feltSafe")) {
            feedback.setFeltSafe((Boolean) feedbackData.get("feltSafe"));
        }
        if (feedbackData.containsKey("wasRespectful")) {
            feedback.setWasRespectful((Boolean) feedbackData.get("wasRespectful"));
        }
        if (feedbackData.containsKey("highlights")) {
            feedback.setHighlights((String) feedbackData.get("highlights"));
        }
        if (feedbackData.containsKey("concerns")) {
            feedback.setConcerns((String) feedbackData.get("concerns"));
        }
        if (feedbackData.containsKey("photosAccurate")) {
            feedback.setPhotosAccurate((Boolean) feedbackData.get("photosAccurate"));
        }
        if (feedbackData.containsKey("profileAccurate")) {
            feedback.setProfileAccurate((Boolean) feedbackData.get("profileAccurate"));
        }
        if (feedbackData.containsKey("planningSecondDate")) {
            feedback.setPlanningSecondDate((Boolean) feedbackData.get("planningSecondDate"));
        }
        if (feedbackData.containsKey("exchangedContact")) {
            feedback.setExchangedContact((Boolean) feedbackData.get("exchangedContact"));
        }

        return feedbackRepo.save(feedback);
    }

    /**
     * Request feedback for a completed date
     */
    @Transactional
    public void requestFeedback(User user, VideoDate videoDate) {
        // Check if feedback already submitted
        if (feedbackRepo.findByVideoDateAndFromUser(videoDate, user).isPresent()) {
            return;
        }

        // Create notification requesting feedback
        UserNotification notification = new UserNotification();
        notification.setUserTo(user);
        notification.setContent("POST_DATE_FEEDBACK_REQUEST");
        notification.setNotificationType("POST_DATE_FEEDBACK_REQUEST");
        notification.setMessage("How was your video date? Your feedback helps us improve the experience for everyone.");
        notification.setDate(new Date());
        notificationRepo.save(notification);
    }

    // ==================== RELATIONSHIP MILESTONES ====================

    /**
     * Create a milestone for a conversation
     */
    @Transactional
    public RelationshipMilestone createMilestone(Conversation conversation,
                                                   RelationshipMilestone.MilestoneType type) {
        // Check if milestone already exists
        Optional<RelationshipMilestone> existing = milestoneRepo.findByConversationAndMilestoneType(conversation, type);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<User> users = new ArrayList<>(conversation.getUsers());
        if (users.size() < 2) {
            throw new RuntimeException("Conversation must have at least 2 users");
        }

        RelationshipMilestone milestone = new RelationshipMilestone();
        milestone.setUserA(users.get(0));
        milestone.setUserB(users.get(1));
        milestone.setConversation(conversation);
        milestone.setMilestoneType(type);
        milestone.setMilestoneDate(LocalDate.now());

        return milestoneRepo.save(milestone);
    }

    /**
     * Schedule a 30-day check-in milestone
     */
    @Transactional
    public void scheduleThirtyDayCheckIn(Conversation conversation) {
        List<User> users = new ArrayList<>(conversation.getUsers());
        if (users.size() < 2) return;

        RelationshipMilestone milestone = new RelationshipMilestone();
        milestone.setUserA(users.get(0));
        milestone.setUserB(users.get(1));
        milestone.setConversation(conversation);
        milestone.setMilestoneType(RelationshipMilestone.MilestoneType.THIRTY_DAYS);
        milestone.setMilestoneDate(LocalDate.now().plusDays(30));

        milestoneRepo.save(milestone);
        LOGGER.info("Scheduled 30-day check-in for conversation {}", conversation.getId());
    }

    /**
     * Send check-in notification to users
     */
    @Transactional
    public void sendCheckIn(RelationshipMilestone milestone) {
        String message = getCheckInMessage(milestone.getMilestoneType());

        // Notify both users
        for (User user : Arrays.asList(milestone.getUserA(), milestone.getUserB())) {
            UserNotification notification = new UserNotification();
            notification.setUserTo(user);
            notification.setContent("MILESTONE_CHECK_IN");
            notification.setNotificationType("MILESTONE_CHECK_IN");
            notification.setMessage(message);
            notification.setDate(new Date());
            notificationRepo.save(notification);
        }

        milestone.setCheckInSent(true);
        milestone.setCheckInSentAt(new Date());
        milestoneRepo.save(milestone);
    }

    private String getCheckInMessage(RelationshipMilestone.MilestoneType type) {
        switch (type) {
            case SEVEN_DAYS:
                return "It's been a week since you matched! How are things going?";
            case THIRTY_DAYS:
                return "It's been 30 days! We'd love to hear how things are going. " +
                       "If you've found a connection, that's what we're here for!";
            case SIXTY_DAYS:
                return "Two months in! Still enjoying getting to know each other?";
            case NINETY_DAYS:
                return "Three months - that's wonderful! Are you still using the app, " +
                       "or have you found what you were looking for?";
            case FIRST_VIDEO_DATE:
                return "Congrats on your first video date! How did it go?";
            case FIRST_REAL_DATE:
                return "Amazing - you met in person! We'd love to hear how it went.";
            default:
                return "How are things going?";
        }
    }

    /**
     * Record user's response to a check-in
     */
    @Transactional
    public void respondToCheckIn(User user, UUID milestoneUuid, String response,
                                  RelationshipMilestone.RelationshipStatus status,
                                  Boolean stillTogether) {
        RelationshipMilestone milestone = milestoneRepo.findByUuid(milestoneUuid)
                .orElseThrow(() -> new RuntimeException("Milestone not found"));

        if (milestone.getUserA().getId().equals(user.getId())) {
            milestone.setUserAResponse(response);
            milestone.setUserARespondedAt(new Date());
        } else if (milestone.getUserB().getId().equals(user.getId())) {
            milestone.setUserBResponse(response);
            milestone.setUserBRespondedAt(new Date());
        } else {
            throw new RuntimeException("User not part of this milestone");
        }

        if (status != null) {
            milestone.setRelationshipStatus(status);
        }
        if (stillTogether != null) {
            milestone.setStillTogether(stillTogether);
        }

        milestoneRepo.save(milestone);
    }

    /**
     * Record that both users are leaving the platform together (success!)
     */
    @Transactional
    public void recordSuccessfulExit(Conversation conversation) {
        RelationshipMilestone milestone = new RelationshipMilestone();
        List<User> users = new ArrayList<>(conversation.getUsers());

        milestone.setUserA(users.get(0));
        milestone.setUserB(users.get(1));
        milestone.setConversation(conversation);
        milestone.setMilestoneType(RelationshipMilestone.MilestoneType.LEFT_PLATFORM_TOGETHER);
        milestone.setMilestoneDate(LocalDate.now());
        milestone.setLeftPlatformTogether(true);
        milestone.setStillTogether(true);
        milestone.setRelationshipStatus(RelationshipMilestone.RelationshipStatus.IN_RELATIONSHIP);

        milestoneRepo.save(milestone);

        LOGGER.info("Recorded successful exit for users {} and {}",
                users.get(0).getId(), users.get(1).getId());
    }

    // ==================== SCHEDULED TASKS ====================

    /**
     * Process pending check-ins daily
     */
    @Scheduled(cron = "0 0 10 * * *") // 10 AM daily
    @Transactional
    public void processPendingCheckIns() {
        LocalDate today = LocalDate.now();
        List<RelationshipMilestone> pending = milestoneRepo.findPendingCheckIns(today);

        for (RelationshipMilestone milestone : pending) {
            try {
                sendCheckIn(milestone);
                LOGGER.info("Sent check-in for milestone {} (type: {})",
                        milestone.getUuid(), milestone.getMilestoneType());
            } catch (Exception e) {
                LOGGER.error("Failed to send check-in for milestone {}", milestone.getUuid(), e);
            }
        }

        if (!pending.isEmpty()) {
            LOGGER.info("Processed {} pending check-ins", pending.size());
        }
    }

    /**
     * Request feedback after completed video dates
     */
    @Scheduled(cron = "0 0 12 * * *") // Noon daily
    @Transactional
    public void requestPendingFeedback() {
        Date yesterday = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date twoDaysAgo = Date.from(Instant.now().minus(2, ChronoUnit.DAYS));

        // Find completed video dates from yesterday that haven't received feedback requests
        List<VideoDate> completedDates = videoDateRepo.findByStatusAndEndedAtBetween(
                VideoDate.DateStatus.COMPLETED, twoDaysAgo, yesterday);

        for (VideoDate date : completedDates) {
            requestFeedback(date.getUserA(), date);
            requestFeedback(date.getUserB(), date);
        }

        if (!completedDates.isEmpty()) {
            LOGGER.info("Requested feedback for {} completed video dates", completedDates.size());
        }
    }

    // ==================== ANALYTICS ====================

    public Map<String, Object> getBridgeAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        Date thirtyDaysAgo = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        Date now = new Date();

        analytics.put("totalRealWorldDates", realWorldDateRepo.count());
        analytics.put("feedbackSubmittedLast30Days", feedbackRepo.countBySubmittedAtBetween(thirtyDaysAgo, now));
        analytics.put("secondDatesPlannedLast30Days", feedbackRepo.countSecondDatesPlannedBetween(thirtyDaysAgo, now));
        analytics.put("venueSuggestionsAccepted", suggestionRepo.countByAcceptedTrue());
        analytics.put("successfulExits", milestoneRepo.countSuccessfulExits());
        analytics.put("thirtyDayCheckInsStillTogether",
                milestoneRepo.countStillTogetherByMilestoneType(RelationshipMilestone.MilestoneType.THIRTY_DAYS));

        return analytics;
    }
}
