package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.ReputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ReputationResource {

    public static final String URL = "/reputation";

    @Autowired
    private AuthService authService;

    @Autowired
    private ReputationService reputationService;

    @GetMapping(URL)
    public ModelAndView reputation() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        UserReputationScore reputation = reputationService.getOrCreateReputation(user);

        ModelAndView mav = new ModelAndView("reputation");

        // Overall score and trust level
        mav.addObject("overallScore", Math.round(reputation.getOverallScore()));
        mav.addObject("trustLevel", reputation.getTrustLevel().name());

        // Individual scores
        mav.addObject("responseQuality", Math.round(reputation.getResponseQuality()));
        mav.addObject("respectScore", Math.round(reputation.getRespectScore()));
        mav.addObject("authenticityScore", Math.round(reputation.getAuthenticityScore()));
        mav.addObject("investmentScore", Math.round(reputation.getInvestmentScore()));

        // Stats
        mav.addObject("datesCompleted", reputation.getDatesCompleted());
        mav.addObject("positiveFeedback", reputation.getPositiveFeedbackCount());
        mav.addObject("ghostingCount", reputation.getGhostingCount());

        // Video verified status
        mav.addObject("videoVerified", user.isVideoVerified());

        // Badges
        List<Map<String, String>> badges = new ArrayList<>();
        if (user.isVideoVerified()) {
            badges.add(Map.of("name", "Video Verified", "icon", "video"));
        }
        if (reputation.getResponseQuality() >= 80) {
            badges.add(Map.of("name", "Great Communicator", "icon", "comments"));
        }
        if (reputation.getDatesCompleted() >= 5) {
            badges.add(Map.of("name", "Active Dater", "icon", "calendar-check"));
        }
        if (reputation.getTrustLevel() == UserReputationScore.TrustLevel.HIGHLY_TRUSTED) {
            badges.add(Map.of("name", "Highly Trusted", "icon", "shield-alt"));
        }
        mav.addObject("badges", badges);

        // Recent activity
        List<UserBehaviorEvent> events = reputationService.getRecentBehavior(user, 30);
        List<Map<String, Object>> recentActivity = events.stream()
            .limit(10)
            .map(e -> Map.<String, Object>of(
                "description", formatBehaviorType(e.getBehaviorType()),
                "impact", e.getReputationImpact(),
                "date", e.getCreatedAt()
            ))
            .collect(Collectors.toList());
        mav.addObject("recentActivity", recentActivity);

        return mav;
    }

    private String formatBehaviorType(UserBehaviorEvent.BehaviorType type) {
        return switch (type) {
            case THOUGHTFUL_MESSAGE -> "Sent a thoughtful message";
            case PROMPT_RESPONSE -> "Responded promptly";
            case SCHEDULED_DATE -> "Scheduled a video date";
            case COMPLETED_DATE -> "Completed a video date";
            case POSITIVE_FEEDBACK -> "Received positive feedback";
            case GRACEFUL_DECLINE -> "Declined respectfully";
            case PROFILE_COMPLETE -> "Completed profile";
            case VIDEO_VERIFIED -> "Verified video identity";
            case LOW_EFFORT_MESSAGE -> "Low effort message sent";
            case SLOW_RESPONSE -> "Slow to respond";
            case GHOSTING -> "Ghosting detected";
            case NO_SHOW -> "No show on date";
            case NEGATIVE_FEEDBACK -> "Received negative feedback";
            case REPORTED -> "Received a report";
            case REPORT_UPHELD -> "Report was upheld";
            case INAPPROPRIATE_CONTENT -> "Inappropriate content flagged";
            case MISREPRESENTATION -> "Profile misrepresentation";
        };
    }
}
