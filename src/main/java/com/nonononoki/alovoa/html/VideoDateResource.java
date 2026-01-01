package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoDate;
import com.nonononoki.alovoa.repo.VideoDateRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.VideoDateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@Controller
public class VideoDateResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private VideoDateService videoDateService;

    @Autowired
    private VideoDateRepository videoDateRepo;

    @GetMapping("/video-dates")
    public String videoDates(Model model) throws Exception {
        User user = authService.getCurrentUser(true);

        Map<String, Object> upcomingData = videoDateService.getUpcomingDates();
        Map<String, Object> proposalsData = videoDateService.getPendingProposals();
        Map<String, Object> historyData = videoDateService.getDateHistory();

        model.addAttribute("user", user);
        model.addAttribute("upcoming", upcomingData.get("dates"));
        model.addAttribute("proposals", proposalsData.get("proposals"));
        model.addAttribute("history", historyData.get("history"));

        return "video-date";
    }

    @GetMapping("/video-date/{id}")
    public ModelAndView viewDate(@PathVariable Long id) throws Exception {
        User user = authService.getCurrentUser(true);

        VideoDate videoDate = videoDateRepo.findById(id)
                .orElseThrow(() -> new Exception("Video date not found"));

        // Verify user is part of this date
        if (!videoDate.getUserA().getId().equals(user.getId()) &&
                !videoDate.getUserB().getId().equals(user.getId())) {
            throw new Exception("Unauthorized");
        }

        User partner = videoDate.getUserA().getId().equals(user.getId())
                ? videoDate.getUserB() : videoDate.getUserA();

        ModelAndView mav = new ModelAndView("video-date-details");
        mav.addObject("user", user);
        mav.addObject("videoDate", videoDate);
        mav.addObject("partner", partner);

        return mav;
    }

    @GetMapping("/video-date/{id}/room")
    public ModelAndView joinRoom(@PathVariable Long id) throws Exception {
        User user = authService.getCurrentUser(true);

        VideoDate videoDate = videoDateRepo.findById(id)
                .orElseThrow(() -> new Exception("Video date not found"));

        // Verify user is part of this date
        if (!videoDate.getUserA().getId().equals(user.getId()) &&
                !videoDate.getUserB().getId().equals(user.getId())) {
            throw new Exception("Unauthorized");
        }

        // Verify date is in correct status
        if (videoDate.getStatus() != VideoDate.DateStatus.ACCEPTED &&
                videoDate.getStatus() != VideoDate.DateStatus.SCHEDULED &&
                videoDate.getStatus() != VideoDate.DateStatus.IN_PROGRESS) {
            throw new Exception("Video date cannot be joined at this time");
        }

        User partner = videoDate.getUserA().getId().equals(user.getId())
                ? videoDate.getUserB() : videoDate.getUserA();

        ModelAndView mav = new ModelAndView("video-date-room");
        mav.addObject("user", user);
        mav.addObject("videoDate", videoDate);
        mav.addObject("partner", partner);
        mav.addObject("roomUrl", videoDate.getRoomUrl());

        return mav;
    }
}
