package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.AccountabilityService;
import com.nonononoki.alovoa.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class AccountabilityResource {

    public static final String URL = "/accountability";
    public static final String URL_USER = "/accountability/{userUuid}";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountabilityService accountabilityService;

    @Autowired
    private UserRepository userRepo;

    @GetMapping(URL)
    public ModelAndView myAccountability() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        return buildAccountabilityView(user, user, false);
    }

    @GetMapping(URL_USER)
    public ModelAndView userAccountability(@PathVariable String userUuid) throws AlovoaException {
        User viewer = authService.getCurrentUser(true);
        User subject = userRepo.findByUuid(UUID.fromString(userUuid))
            .orElseThrow(() -> new AlovoaException("User not found"));

        return buildAccountabilityView(subject, viewer, true);
    }

    private ModelAndView buildAccountabilityView(User subject, User viewer, boolean canSubmitFeedback) {
        ModelAndView mav = new ModelAndView("accountability");

        // Get feedback summary
        Map<String, Object> summary = accountabilityService.getFeedbackSummary(subject);
        mav.addObject("feedbackScore", summary.get("feedbackScore"));
        mav.addObject("positiveCount", summary.get("positiveCount"));
        mav.addObject("negativeCount", summary.get("negativeCount"));
        mav.addObject("totalCount", summary.get("totalReports"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> byCategory = (Map<String, Integer>) summary.get("byCategory");
        mav.addObject("categoryBreakdown", byCategory);

        // Get public feedback
        List<UserAccountabilityReport> feedback = accountabilityService.getPublicFeedback(subject, viewer);
        mav.addObject("feedback", feedback);

        // Can submit feedback if viewing another user
        mav.addObject("canSubmitFeedback", canSubmitFeedback && !subject.equals(viewer));
        mav.addObject("subjectUuid", subject.getUuid().toString());

        return mav;
    }
}
