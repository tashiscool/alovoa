package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.ReputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reputation")
public class ReputationController {

    @Autowired
    private ReputationService reputationService;

    @Autowired
    private AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<?> getMyReputation() {
        try {
            User user = authService.getCurrentUser(true);
            UserReputationScore reputation = reputationService.getOrCreateReputation(user);

            Map<String, Object> result = new HashMap<>();
            result.put("overallScore", reputation.getOverallScore());
            result.put("scores", Map.of(
                    "responseQuality", reputation.getResponseQuality(),
                    "respect", reputation.getRespectScore(),
                    "authenticity", reputation.getAuthenticityScore(),
                    "investment", reputation.getInvestmentScore()
            ));
            result.put("trustLevel", reputation.getTrustLevel().name());
            result.put("stats", Map.of(
                    "ghostingCount", reputation.getGhostingCount(),
                    "datesCompleted", reputation.getDatesCompleted(),
                    "positiveFeedback", reputation.getPositiveFeedbackCount()
            ));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/badges")
    public ResponseEntity<?> getMyBadges() {
        try {
            User user = authService.getCurrentUser(true);
            UserReputationScore reputation = reputationService.getOrCreateReputation(user);

            List<Map<String, Object>> badges = new java.util.ArrayList<>();

            // Trust level badge
            badges.add(Map.of(
                    "id", "trust_" + reputation.getTrustLevel().name().toLowerCase(),
                    "name", formatTrustLevel(reputation.getTrustLevel()),
                    "type", "trust"
            ));

            // Video verified badge
            if (user.isVideoVerified()) {
                badges.add(Map.of(
                        "id", "video_verified",
                        "name", "Video Verified",
                        "type", "verification"
                ));
            }

            // Response quality badge
            if (reputation.getResponseQuality() >= 80) {
                badges.add(Map.of(
                        "id", "responsive",
                        "name", "Great Communicator",
                        "type", "quality"
                ));
            }

            // Dates completed badge
            if (reputation.getDatesCompleted() >= 5) {
                badges.add(Map.of(
                        "id", "active_dater",
                        "name", "Active Dater",
                        "type", "activity"
                ));
            }

            return ResponseEntity.ok(Map.of("badges", badges));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getReputationHistory(@RequestParam(defaultValue = "30") Integer days) {
        try {
            User user = authService.getCurrentUser(true);
            List<UserBehaviorEvent> events = reputationService.getRecentBehavior(user, days);

            List<Map<String, Object>> history = events.stream()
                    .map(e -> Map.<String, Object>of(
                            "type", e.getBehaviorType().name(),
                            "impact", e.getReputationImpact(),
                            "date", e.getCreatedAt()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("history", history));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String formatTrustLevel(UserReputationScore.TrustLevel level) {
        switch (level) {
            case NEW_MEMBER: return "New Member";
            case VERIFIED: return "Verified";
            case TRUSTED: return "Trusted";
            case HIGHLY_TRUSTED: return "Highly Trusted";
            case UNDER_REVIEW: return "Under Review";
            case RESTRICTED: return "Restricted";
            default: return level.name();
        }
    }
}
