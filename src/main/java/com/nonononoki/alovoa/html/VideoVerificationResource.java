package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class VideoVerificationResource {

    public static final String URL = "/video-verification";

    @Autowired
    private AuthService authService;

    @GetMapping(URL)
    public ModelAndView videoVerification() throws AlovoaException {
        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("video-verification");
        mav.addObject("verified", user.isVideoVerified());

        if (user.getVideoVerification() != null) {
            mav.addObject("verificationStatus", user.getVideoVerification().getStatus());
            mav.addObject("faceMatchScore", user.getVideoVerification().getFaceMatchScore());
        }

        return mav;
    }
}
