package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.ProfileVisitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileVisitorsResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private ProfileVisitService profileVisitService;

    @GetMapping("/visitors")
    public String visitors(Model model) throws Exception {
        User user = authService.getCurrentUser(true);

        model.addAttribute("user", user);
        model.addAttribute("recentVisitors", profileVisitService.getRecentVisitors(20));
        model.addAttribute("myVisits", profileVisitService.getMyVisits(20));

        return "visitors";
    }
}
