package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.PersonalityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PersonalityResource {

    public static final String URL_ASSESSMENT = "/personality-assessment";
    public static final String URL_RESULTS = "/personality-results";

    @Autowired
    private AuthService authService;

    @Autowired
    private PersonalityService personalityService;

    @GetMapping(URL_ASSESSMENT)
    public ModelAndView personalityAssessment() throws AlovoaException {
        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("personality-assessment");

        // Check if already completed
        UserPersonalityProfile profile = user.getPersonalityProfile();
        if (profile != null && profile.isComplete()) {
            mav.addObject("alreadyCompleted", true);
        } else {
            mav.addObject("alreadyCompleted", false);
            mav.addObject("questions", personalityService.getAssessmentQuestions());
        }

        return mav;
    }

    @GetMapping(URL_RESULTS)
    public ModelAndView personalityResults() throws AlovoaException {
        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("personality-results");

        UserPersonalityProfile profile = user.getPersonalityProfile();
        if (profile == null || !profile.isComplete()) {
            // Redirect to assessment if not completed
            return new ModelAndView("redirect:" + URL_ASSESSMENT);
        }

        mav.addObject("openness", profile.getOpenness());
        mav.addObject("conscientiousness", profile.getConscientiousness());
        mav.addObject("extraversion", profile.getExtraversion());
        mav.addObject("agreeableness", profile.getAgreeableness());
        mav.addObject("neuroticism", profile.getNeuroticism());
        mav.addObject("attachmentStyle", profile.getAttachmentStyle());
        mav.addObject("communicationDirectness", profile.getCommunicationDirectness());
        mav.addObject("communicationEmotional", profile.getCommunicationEmotional());
        mav.addObject("completedAt", profile.getAssessmentCompletedAt());

        return mav;
    }
}
