package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.PoliticalAssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PoliticalAssessmentResource {

    public static final String URL = "/values-assessment";

    @Autowired
    private AuthService authService;

    @Autowired
    private PoliticalAssessmentService politicalAssessmentService;

    @GetMapping(URL)
    public ModelAndView valuesAssessment() throws AlovoaException {
        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("political-assessment");

        UserPoliticalAssessment assessment = politicalAssessmentService.getOrCreateAssessment(user);

        mav.addObject("gateStatus", assessment.getGateStatus().name());
        mav.addObject("statusMessage", politicalAssessmentService.getGateStatusMessage(user));
        mav.addObject("canAccessMatching", politicalAssessmentService.canAccessMatching(user));
        mav.addObject("assessmentComplete", assessment.getAssessmentCompletedAt() != null);

        // Pass existing values for resuming incomplete assessment
        if (assessment.getEconomicClass() != null) {
            mav.addObject("economicClass", assessment.getEconomicClass().name());
        }
        if (assessment.getPoliticalOrientation() != null) {
            mav.addObject("politicalOrientation", assessment.getPoliticalOrientation().name());
        }
        if (assessment.getEconomicValuesScore() != null) {
            mav.addObject("economicValuesScore", assessment.getEconomicValuesScore());
        }

        // Gate-specific flags
        if (assessment.getGateStatus() == UserPoliticalAssessment.GateStatus.PENDING_EXPLANATION) {
            mav.addObject("needsExplanation", true);
        }
        if (assessment.getGateStatus() == UserPoliticalAssessment.GateStatus.PENDING_VASECTOMY) {
            mav.addObject("needsVasectomy", true);
            mav.addObject("acknowledgedRequirement", assessment.getAcknowledgedVasectomyRequirement());
        }

        return mav;
    }
}
