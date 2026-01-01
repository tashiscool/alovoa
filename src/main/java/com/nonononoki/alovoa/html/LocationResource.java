package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.LocationAreaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LocationResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private LocationAreaService locationAreaService;

    @GetMapping("/location-settings")
    public String locationSettings(Model model) throws Exception {
        User user = authService.getCurrentUser(true);

        model.addAttribute("user", user);
        model.addAttribute("areas", locationAreaService.getMyAreas());
        model.addAttribute("preferences", locationAreaService.getMyPreferences());
        model.addAttribute("traveling", locationAreaService.getMyTravelingMode().orElse(null));

        return "location-settings";
    }
}
