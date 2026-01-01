package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.WaitlistEntry;
import com.nonononoki.alovoa.service.WaitlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Controller for waitlist pages.
 *
 * Pre-launch: serves as the main landing page.
 * Post-launch: redirects to login/register.
 */
@Controller
public class WaitlistResource {

    @Autowired
    private WaitlistService waitlistService;

    /**
     * Main waitlist landing page.
     */
    @GetMapping("/waitlist")
    public ModelAndView waitlist(
            @RequestParam(value = "ref", required = false) String referralCode) {

        ModelAndView mav = new ModelAndView("waitlist");

        // Get stats for display
        Map<String, Object> stats = waitlistService.getStats();
        mav.addObject("totalSignups", stats.get("total"));
        mav.addObject("marketStats", waitlistService.getMarketStats());

        // If referral code provided, validate it
        if (referralCode != null && !referralCode.isBlank()) {
            mav.addObject("referralCode", referralCode.toUpperCase());
            waitlistService.getByInviteCode(referralCode).ifPresent(
                    entry -> mav.addObject("referrerExists", true)
            );
        }

        return mav;
    }

    /**
     * Check status page - user enters email to see their position.
     */
    @GetMapping("/waitlist/status")
    public ModelAndView status(@RequestParam(value = "email", required = false) String email) {
        ModelAndView mav = new ModelAndView("waitlist-status");

        if (email != null && !email.isBlank()) {
            waitlistService.getByEmail(email).ifPresentOrElse(
                    entry -> {
                        mav.addObject("found", true);
                        mav.addObject("entry", entry);
                        mav.addObject("position", waitlistService.getPositionInLine(entry));
                        mav.addObject("referrals", waitlistService.getReferralCount(entry));
                        mav.addObject("marketStatus", waitlistService.getMarketStatus(entry.getLocation()));
                    },
                    () -> mav.addObject("found", false)
            );
            mav.addObject("email", email);
        }

        return mav;
    }

    /**
     * Success page after signup.
     */
    @GetMapping("/waitlist/success")
    public ModelAndView success(
            @RequestParam("code") String inviteCode) {

        ModelAndView mav = new ModelAndView("waitlist-success");

        waitlistService.getByInviteCode(inviteCode).ifPresent(entry -> {
            mav.addObject("entry", entry);
            mav.addObject("position", waitlistService.getPositionInLine(entry));
            mav.addObject("marketStatus", waitlistService.getMarketStatus(entry.getLocation()));
        });

        return mav;
    }

    /**
     * Markets status page - shows all markets and their readiness.
     */
    @GetMapping("/waitlist/markets")
    public ModelAndView markets() {
        ModelAndView mav = new ModelAndView("waitlist-markets");
        mav.addObject("markets", waitlistService.getAllMarketStats());
        return mav;
    }
}
