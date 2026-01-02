package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.EssayDto;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.EssayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Page controller for essay editing.
 */
@Controller
public class EssayResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private EssayService essayService;

    @GetMapping("/essays")
    public String essays(Model model) throws AlovoaException {
        authService.getCurrentUser(true);
        List<EssayDto> essays = essayService.getCurrentUserEssays();
        model.addAttribute("essays", essays);

        // Calculate completion stats
        long filledCount = essays.stream().filter(e -> e.getAnswer() != null && !e.getAnswer().isBlank()).count();
        model.addAttribute("filledCount", filledCount);
        model.addAttribute("totalCount", essays.size());

        return "essays";
    }
}
