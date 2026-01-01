package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.UserVideoIntroductionRepository;
import com.nonononoki.alovoa.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the video introduction page.
 * Allows users to record a video intro with AI analysis or manual tagging fallback.
 */
@Controller
public class VideoIntroResource {

    public static final String URL = "/video-intro";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserVideoIntroductionRepository videoIntroRepo;

    @GetMapping(URL)
    public ModelAndView videoIntroduction() throws AlovoaException {
        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("video-intro");

        // Check if user already has a video introduction
        UserVideoIntroduction existingVideo = videoIntroRepo.findByUser(user).orElse(null);

        if (existingVideo != null) {
            mav.addObject("hasVideoIntro", true);
            mav.addObject("analyzed", existingVideo.getStatus() == UserVideoIntroduction.AnalysisStatus.COMPLETED);

            // Add video details if needed
            if (existingVideo.getStatus() == UserVideoIntroduction.AnalysisStatus.COMPLETED) {
                mav.addObject("worldviewSummary", existingVideo.getWorldviewSummary());
                mav.addObject("backgroundSummary", existingVideo.getBackgroundSummary());
            }
        } else {
            mav.addObject("hasVideoIntro", false);
        }

        return mav;
    }
}
