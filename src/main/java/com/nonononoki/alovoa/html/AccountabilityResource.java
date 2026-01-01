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
    public static final String URL_MY_REPORTS = "/accountability/my-reports";
    public static final String URL_REPORT_FORM = "/accountability/report/{userUuid}";
    public static final String URL_VIEW_REPORT = "/accountability/view/{reportUuid}";

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

    /**
     * View reports received by me and submitted by me
     */
    @GetMapping(URL_MY_REPORTS)
    public ModelAndView myReports() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        ModelAndView mav = new ModelAndView("accountability-reports");

        // Get reports about me (received)
        List<UserAccountabilityReport> receivedReports = user.getAccountabilityReportsReceived();
        mav.addObject("receivedReports", receivedReports);
        mav.addObject("receivedCount", receivedReports.size());

        // Get reports I submitted
        List<UserAccountabilityReport> submittedReports = user.getAccountabilityReportsSubmitted();
        mav.addObject("submittedReports", submittedReports);
        mav.addObject("submittedCount", submittedReports.size());

        return mav;
    }

    /**
     * Form to submit a report about a user
     */
    @GetMapping(URL_REPORT_FORM)
    public ModelAndView reportForm(@PathVariable String userUuid) throws AlovoaException {
        User viewer = authService.getCurrentUser(true);
        User subject = userRepo.findByUuid(UUID.fromString(userUuid))
            .orElseThrow(() -> new AlovoaException("User not found"));

        if (viewer.equals(subject)) {
            throw new AlovoaException("Cannot report yourself");
        }

        ModelAndView mav = new ModelAndView("accountability-report-form");
        mav.addObject("subject", subject);
        mav.addObject("subjectUuid", subject.getUuid().toString());

        return mav;
    }

    /**
     * View detailed information about a specific report
     */
    @GetMapping(URL_VIEW_REPORT)
    public ModelAndView viewReport(@PathVariable String reportUuid) throws AlovoaException {
        User viewer = authService.getCurrentUser(true);
        UUID uuid = UUID.fromString(reportUuid);

        // For now, redirect to the accountability page
        // In the future, this could be a dedicated report detail page
        return new ModelAndView("redirect:" + URL_MY_REPORTS);
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
