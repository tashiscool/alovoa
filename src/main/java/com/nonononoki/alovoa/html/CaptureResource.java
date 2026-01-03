package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the web capture page.
 * Allows users to record audio, screen, or webcam content.
 */
@Controller
public class CaptureResource {

    public static final String URL = "/capture";

    @Autowired
    private AuthService authService;

    @GetMapping(URL)
    public ModelAndView capturePage() throws AlovoaException {
        // Require authentication
        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("capture");

        // Can add user-specific data here if needed
        // For now, the page is self-contained with JavaScript

        return mav;
    }
}
