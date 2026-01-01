package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.IntakeProgressDto;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.IntakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IntakeResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private IntakeService intakeService;

    @GetMapping("/intake")
    public String intake(Model model) throws Exception {
        User user = authService.getCurrentUser(true);
        IntakeProgressDto progress = intakeService.getIntakeProgress();

        model.addAttribute("user", user);
        model.addAttribute("progress", progress);

        return "intake";
    }
}
