package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.*;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.DateVenueSuggestionRepository;
import com.nonononoki.alovoa.repo.RelationshipMilestoneRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.BridgeToRealWorldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for Bridge to Real World features.
 * Helps users transition from app to real-world connections.
 */
@RestController
@RequestMapping("/api/bridge")
public class BridgeToRealWorldController {

    @Autowired
    private BridgeToRealWorldService bridgeService;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private DateVenueSuggestionRepository suggestionRepo;

    @Autowired
    private RelationshipMilestoneRepository milestoneRepo;

    @Autowired
    private AuthService authService;

    // ==================== VENUE SUGGESTIONS ====================

    /**
     * Get venue suggestions for a conversation
     */
    @GetMapping("/suggestions/{conversationId}")
    public ResponseEntity<List<Map<String, Object>>> getVenueSuggestions(
            @PathVariable Long conversationId) throws Exception {
        User user = authService.getCurrentUser();
        Conversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new Exception("Conversation not found"));

        // Verify user is part of conversation
        if (!isUserInConversation(user, conversation)) {
            return ResponseEntity.status(403).build();
        }

        List<DateVenueSuggestion> suggestions = bridgeService.getSuggestionsForConversation(conversation);

        // Generate new suggestions if none exist
        if (suggestions.isEmpty()) {
            User otherUser = getOtherUser(user, conversation);
            if (otherUser != null) {
                suggestions = bridgeService.generateVenueSuggestions(user, otherUser, conversation);
            }
        }

        List<Map<String, Object>> response = suggestions.stream()
                .map(this::mapSuggestionToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Accept a venue suggestion
     */
    @PostMapping("/suggestions/{suggestionId}/accept")
    public ResponseEntity<Map<String, Object>> acceptSuggestion(
            @PathVariable Long suggestionId) throws Exception {
        authService.getCurrentUser();
        DateVenueSuggestion suggestion = bridgeService.acceptSuggestion(suggestionId);
        return ResponseEntity.ok(mapSuggestionToResponse(suggestion));
    }

    /**
     * Dismiss a venue suggestion
     */
    @PostMapping("/suggestions/{suggestionId}/dismiss")
    public ResponseEntity<Void> dismissSuggestion(@PathVariable Long suggestionId) throws Exception {
        authService.getCurrentUser();
        bridgeService.dismissSuggestion(suggestionId);
        return ResponseEntity.ok().build();
    }

    /**
     * Generate new suggestions for a conversation
     */
    @PostMapping("/suggestions/{conversationId}/generate")
    public ResponseEntity<List<Map<String, Object>>> generateSuggestions(
            @PathVariable Long conversationId) throws Exception {
        User user = authService.getCurrentUser();
        Conversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new Exception("Conversation not found"));

        if (!isUserInConversation(user, conversation)) {
            return ResponseEntity.status(403).build();
        }

        User otherUser = getOtherUser(user, conversation);
        List<DateVenueSuggestion> suggestions = bridgeService.generateVenueSuggestions(user, otherUser, conversation);

        List<Map<String, Object>> response = suggestions.stream()
                .map(this::mapSuggestionToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // ==================== POST-DATE FEEDBACK ====================

    /**
     * Submit feedback for a date
     */
    @PostMapping("/feedback/{dateUuid}")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @PathVariable UUID dateUuid,
            @RequestParam(defaultValue = "true") boolean isVideoDate,
            @RequestBody Map<String, Object> feedbackData) throws Exception {
        User user = authService.getCurrentUser();

        PostDateFeedback feedback = bridgeService.submitFeedback(user, dateUuid, feedbackData, isVideoDate);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("feedbackId", feedback.getUuid().toString());
        response.put("message", "Thank you for your feedback! It helps us improve the experience.");

        return ResponseEntity.ok(response);
    }

    // ==================== RELATIONSHIP MILESTONES ====================

    /**
     * Get milestones for a conversation
     */
    @GetMapping("/milestones/{conversationId}")
    public ResponseEntity<List<Map<String, Object>>> getMilestones(
            @PathVariable Long conversationId) throws Exception {
        User user = authService.getCurrentUser();
        Conversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new Exception("Conversation not found"));

        if (!isUserInConversation(user, conversation)) {
            return ResponseEntity.status(403).build();
        }

        List<RelationshipMilestone> milestones = milestoneRepo.findByConversationOrderByMilestoneDateDesc(conversation);

        List<Map<String, Object>> response = milestones.stream()
                .map(this::mapMilestoneToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Respond to a milestone check-in
     */
    @PostMapping("/milestones/{milestoneUuid}/respond")
    public ResponseEntity<Map<String, Object>> respondToMilestone(
            @PathVariable UUID milestoneUuid,
            @RequestBody Map<String, Object> responseData) throws Exception {
        User user = authService.getCurrentUser();

        String response = (String) responseData.get("response");
        String statusStr = (String) responseData.get("relationshipStatus");
        Boolean stillTogether = (Boolean) responseData.get("stillTogether");

        RelationshipMilestone.RelationshipStatus status = null;
        if (statusStr != null) {
            status = RelationshipMilestone.RelationshipStatus.valueOf(statusStr);
        }

        bridgeService.respondToCheckIn(user, milestoneUuid, response, status, stillTogether);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Thanks for letting us know!");

        return ResponseEntity.ok(result);
    }

    /**
     * Report leaving the platform together (success!)
     */
    @PostMapping("/success/{conversationId}")
    public ResponseEntity<Map<String, Object>> reportSuccess(
            @PathVariable Long conversationId) throws Exception {
        User user = authService.getCurrentUser();
        Conversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new Exception("Conversation not found"));

        if (!isUserInConversation(user, conversation)) {
            return ResponseEntity.status(403).build();
        }

        bridgeService.recordSuccessfulExit(conversation);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Congratulations! We're so happy for you. This is exactly what we hoped for!");

        return ResponseEntity.ok(response);
    }

    // ==================== ANALYTICS (for user) ====================

    /**
     * Get user's relationship journey summary
     */
    @GetMapping("/journey")
    public ResponseEntity<Map<String, Object>> getJourneySummary() throws Exception {
        User user = authService.getCurrentUser();

        List<RelationshipMilestone> milestones = milestoneRepo.findByUserOrderByMilestoneDateDesc(user);
        List<DateVenueSuggestion> suggestions = suggestionRepo.findByUserOrderBySuggestedAtDesc(user);

        Map<String, Object> journey = new HashMap<>();
        journey.put("totalMilestones", milestones.size());
        journey.put("activeSuggestions", suggestions.stream()
                .filter(s -> !s.getDismissed() && !s.getAccepted())
                .count());
        journey.put("acceptedSuggestions", suggestions.stream()
                .filter(DateVenueSuggestion::getAccepted)
                .count());

        // Find most recent milestone
        if (!milestones.isEmpty()) {
            journey.put("latestMilestone", mapMilestoneToResponse(milestones.get(0)));
        }

        return ResponseEntity.ok(journey);
    }

    // ==================== HELPER METHODS ====================

    private boolean isUserInConversation(User user, Conversation conversation) {
        return conversation.getUsers().stream()
                .anyMatch(u -> u.getId().equals(user.getId()));
    }

    private User getOtherUser(User user, Conversation conversation) {
        return conversation.getUsers().stream()
                .filter(u -> !u.getId().equals(user.getId()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> mapSuggestionToResponse(DateVenueSuggestion suggestion) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", suggestion.getId());
        response.put("venueCategory", suggestion.getVenueCategory());
        response.put("venueName", suggestion.getVenueName());
        response.put("venueDescription", suggestion.getVenueDescription());
        response.put("matchingInterests", suggestion.getMatchingInterests());
        response.put("reason", suggestion.getReason());
        response.put("suggestedAt", suggestion.getSuggestedAt());
        response.put("accepted", suggestion.getAccepted());
        response.put("dismissed", suggestion.getDismissed());
        return response;
    }

    private Map<String, Object> mapMilestoneToResponse(RelationshipMilestone milestone) {
        Map<String, Object> response = new HashMap<>();
        response.put("uuid", milestone.getUuid().toString());
        response.put("type", milestone.getMilestoneType().name());
        response.put("date", milestone.getMilestoneDate().toString());
        response.put("checkInSent", milestone.getCheckInSent());
        response.put("relationshipStatus", milestone.getRelationshipStatus() != null
                ? milestone.getRelationshipStatus().name() : null);
        response.put("stillTogether", milestone.getStillTogether());
        response.put("leftPlatformTogether", milestone.getLeftPlatformTogether());
        return response;
    }
}
