package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.ProfileDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileDetailsResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private ProfileDetailsService profileDetailsService;

    @GetMapping("/profile-details")
    public String profileDetails(Model model) throws Exception {
        User user = authService.getCurrentUser(true);

        model.addAttribute("user", user);
        model.addAttribute("details", profileDetailsService.getMyDetails());
        model.addAttribute("options", profileDetailsService.getDetailOptions());

        return "profile-details";
    }
}
