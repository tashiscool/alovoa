package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.MatchWindow;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.MatchWindowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HTML resource controller for 24-hour match decision windows UI.
 * Displays pending match windows with countdown timers and action buttons.
 */
@Controller
public class MatchWindowResource {

    public static final String URL = "/match-windows";
    public static final String URL_WINDOW = "/match-windows/{uuid}";

    @Autowired
    private AuthService authService;

    @Autowired
    private MatchWindowService matchWindowService;

    /**
     * Display the match windows dashboard showing:
     * - Pending decisions (waiting for current user)
     * - Waiting matches (current user confirmed, waiting on other)
     * - Confirmed matches (both confirmed, ready for conversation)
     */
    @GetMapping(URL)
    public ModelAndView matchWindows() throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("match-windows");

        // Get all match window categories
        List<MatchWindow> pending = matchWindowService.getPendingDecisions();
        List<MatchWindow> waiting = matchWindowService.getWaitingMatches();
        List<MatchWindow> confirmed = matchWindowService.getConfirmedMatches();

        // Add to model
        mav.addObject("pendingWindows", pending);
        mav.addObject("waitingWindows", waiting);
        mav.addObject("confirmedWindows", confirmed);
        mav.addObject("currentUser", currentUser);

        // Calculate totals for badges
        mav.addObject("pendingCount", pending.size());
        mav.addObject("waitingCount", waiting.size());
        mav.addObject("confirmedCount", confirmed.size());

        return mav;
    }

    /**
     * View a specific match window with full profile details.
     * Shows detailed view of the match with larger profile info.
     */
    @GetMapping(URL_WINDOW)
    public ModelAndView viewWindow(@PathVariable UUID uuid) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);

        Optional<MatchWindow> windowOpt = matchWindowService.getWindow(uuid);
        if (windowOpt.isEmpty()) {
            throw new AlovoaException("Match window not found");
        }

        MatchWindow window = windowOpt.get();

        // Verify user is part of this match
        if (!currentUser.getId().equals(window.getUserA().getId()) &&
            !currentUser.getId().equals(window.getUserB().getId())) {
            throw new AlovoaException("Unauthorized access to this match window");
        }

        ModelAndView mav = new ModelAndView("match-window-detail");

        // Get the other user in the match
        User otherUser = window.getOtherUser(currentUser);

        mav.addObject("window", window);
        mav.addObject("otherUser", otherUser);
        mav.addObject("currentUser", currentUser);
        mav.addObject("hasUserConfirmed", window.hasUserConfirmed(currentUser));
        mav.addObject("canExtend", window.canExtend());

        return mav;
    }
}
