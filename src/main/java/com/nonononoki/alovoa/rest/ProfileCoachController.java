package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ProfileCoachingMessage;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.ProfileCoachService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for Profile Coach functionality.
 * Provides access to coaching messages and editing statistics.
 */
@RestController
@RequestMapping("/api/profile-coach")
public class ProfileCoachController {

    @Autowired
    private ProfileCoachService profileCoachService;

    @Autowired
    private AuthService authService;

    /**
     * Get active coaching messages for the current user
     */
    @GetMapping("/messages")
    public ResponseEntity<List<CoachingMessageDto>> getActiveMessages() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        List<ProfileCoachingMessage> messages = profileCoachService.getActiveMessages(user);

        List<CoachingMessageDto> dtos = messages.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Dismiss a coaching message with optional feedback
     */
    @PostMapping("/messages/{uuid}/dismiss")
    public ResponseEntity<Map<String, Object>> dismissMessage(
            @PathVariable UUID uuid,
            @RequestBody(required = false) DismissRequest request) throws AlovoaException {

        authService.getCurrentUser(true); // Ensure authenticated
        profileCoachService.dismissMessage(uuid, request != null ? request.helpful : null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * Get editing statistics for the current user
     */
    @GetMapping("/stats")
    public ResponseEntity<EditingStatsDto> getEditingStats() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        ProfileCoachService.EditingStats stats = profileCoachService.getEditingStats(user);

        EditingStatsDto dto = new EditingStatsDto();
        dto.todayEdits = stats.todayEdits;
        dto.weekEdits = stats.weekEdits;
        dto.monthEdits = stats.monthEdits;
        dto.averageDailyEdits = stats.averageDailyEdits;
        dto.isHighFrequency = stats.todayEdits >= 5;
        dto.editBehaviorLabel = getEditBehaviorLabel(stats.averageDailyEdits);

        return ResponseEntity.ok(dto);
    }

    /**
     * Get a coaching tip (positive encouragement)
     */
    @GetMapping("/tip")
    public ResponseEntity<Map<String, String>> getCoachingTip() throws AlovoaException {
        authService.getCurrentUser(true); // Ensure authenticated

        // Random positive tips
        String[] tips = {
            "Authenticity is attractive. The best profiles show the real you, not a curated version.",
            "Quality over quantity: focus on meaningful conversations rather than maximizing matches.",
            "Your profile is a starting point, not the whole story. Real connection happens in conversation.",
            "The goal isn't to appeal to everyone - it's to appeal to the right someone.",
            "Confidence in who you are beats any 'optimization' trick every time."
        };

        Map<String, String> response = new HashMap<>();
        response.put("tip", tips[(int) (Math.random() * tips.length)]);
        return ResponseEntity.ok(response);
    }

    // Helper methods

    private CoachingMessageDto toDto(ProfileCoachingMessage message) {
        CoachingMessageDto dto = new CoachingMessageDto();
        dto.uuid = message.getUuid().toString();
        dto.messageType = message.getMessageType().name();
        dto.messageContent = message.getMessageContent();
        dto.sentAt = message.getSentAt().toInstant().toString();
        return dto;
    }

    private String getEditBehaviorLabel(double avgDailyEdits) {
        if (avgDailyEdits < 1) return "Stable";
        if (avgDailyEdits < 3) return "Moderate";
        if (avgDailyEdits < 5) return "Active";
        return "Very Active";
    }

    // DTOs

    public static class CoachingMessageDto {
        public String uuid;
        public String messageType;
        public String messageContent;
        public String sentAt;
    }

    public static class EditingStatsDto {
        public long todayEdits;
        public long weekEdits;
        public long monthEdits;
        public double averageDailyEdits;
        public boolean isHighFrequency;
        public String editBehaviorLabel;
    }

    public static class DismissRequest {
        public Boolean helpful;
    }
}
