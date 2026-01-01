package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserCalendarSettings;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.CalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller for calendar integration settings.
 * Handles Google Calendar, Apple Calendar (CalDAV), and Outlook Calendar connections.
 */
@Controller
public class CalendarResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarResource.class);

    public static final String URL = "/settings/calendar";
    public static final String GOOGLE_CONNECT = "/calendar/google/connect";
    public static final String GOOGLE_CALLBACK = "/calendar/google/callback";
    public static final String OUTLOOK_CONNECT = "/calendar/outlook/connect";
    public static final String OUTLOOK_CALLBACK = "/calendar/outlook/callback";

    @Autowired
    private AuthService authService;

    @Autowired
    private CalendarService calendarService;

    @Value("${app.domain:https://aura.dating}")
    private String appDomain;

    // Google OAuth settings (would typically be in application.properties)
    @Value("${google.oauth.client-id:YOUR_GOOGLE_CLIENT_ID}")
    private String googleClientId;

    @Value("${google.oauth.client-secret:YOUR_GOOGLE_CLIENT_SECRET}")
    private String googleClientSecret;

    // Microsoft OAuth settings
    @Value("${microsoft.oauth.client-id:YOUR_MICROSOFT_CLIENT_ID}")
    private String microsoftClientId;

    @Value("${microsoft.oauth.client-secret:YOUR_MICROSOFT_CLIENT_SECRET}")
    private String microsoftClientSecret;

    /**
     * Display calendar settings page
     */
    @GetMapping(URL)
    public ModelAndView calendarSettings() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        UserCalendarSettings settings = calendarService.getSettings();

        ModelAndView mav = new ModelAndView("calendar-settings");
        mav.addObject("settings", settings);

        // Connection status
        mav.addObject("googleConnected", settings.isGoogleCalendarEnabled());
        mav.addObject("appleConnected", settings.isAppleCalendarEnabled());
        mav.addObject("outlookConnected", settings.isOutlookCalendarEnabled());

        // Settings values
        mav.addObject("defaultReminderMinutes", settings.getDefaultReminderMinutes());
        mav.addObject("autoAddDates", settings.isAutoAddDates());
        mav.addObject("showMatchName", settings.isShowMatchName());

        // Last updated
        mav.addObject("lastUpdated", settings.getUpdatedAt());

        return mav;
    }

    /**
     * Initiate Google Calendar OAuth flow
     */
    @GetMapping(GOOGLE_CONNECT)
    public String connectGoogle() throws AlovoaException, UnsupportedEncodingException {
        User user = authService.getCurrentUser(true);

        String redirectUri = appDomain + GOOGLE_CALLBACK;
        String scope = "https://www.googleapis.com/auth/calendar.events";

        // Build Google OAuth URL
        String googleAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + googleClientId +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString()) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8.toString()) +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + user.getUuid(); // Use user UUID as state for security

        LOGGER.info("Redirecting user {} to Google OAuth", user.getId());
        return "redirect:" + googleAuthUrl;
    }

    /**
     * Handle Google OAuth callback
     */
    @GetMapping(GOOGLE_CALLBACK)
    public ModelAndView googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state) throws AlovoaException {

        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("redirect:" + URL);

        if (error != null) {
            LOGGER.warn("Google OAuth error for user {}: {}", user.getId(), error);
            mav.addObject("error", "Failed to connect Google Calendar: " + error);
            return mav;
        }

        if (code == null) {
            LOGGER.warn("No authorization code received for user {}", user.getId());
            mav.addObject("error", "No authorization code received");
            return mav;
        }

        // Verify state matches user UUID for security
        if (!user.getUuid().equals(state)) {
            LOGGER.warn("State mismatch for user {}", user.getId());
            mav.addObject("error", "Security verification failed");
            return mav;
        }

        try {
            calendarService.handleGoogleOAuthCallback(code);
            mav.addObject("success", "Google Calendar connected successfully!");
            LOGGER.info("Successfully connected Google Calendar for user {}", user.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to process Google OAuth callback for user {}: {}",
                    user.getId(), e.getMessage());
            mav.addObject("error", "Failed to connect Google Calendar. Please try again.");
        }

        return mav;
    }

    /**
     * Initiate Outlook Calendar OAuth flow
     */
    @GetMapping(OUTLOOK_CONNECT)
    public String connectOutlook() throws AlovoaException, UnsupportedEncodingException {
        User user = authService.getCurrentUser(true);

        String redirectUri = appDomain + OUTLOOK_CALLBACK;
        String scope = "Calendars.ReadWrite offline_access";

        // Build Microsoft OAuth URL
        String msAuthUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?" +
                "client_id=" + microsoftClientId +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString()) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8.toString()) +
                "&response_mode=query" +
                "&state=" + user.getUuid();

        LOGGER.info("Redirecting user {} to Microsoft OAuth", user.getId());
        return "redirect:" + msAuthUrl;
    }

    /**
     * Handle Outlook OAuth callback
     */
    @GetMapping(OUTLOOK_CALLBACK)
    public ModelAndView outlookCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state) throws AlovoaException {

        User user = authService.getCurrentUser(true);

        ModelAndView mav = new ModelAndView("redirect:" + URL);

        if (error != null) {
            LOGGER.warn("Outlook OAuth error for user {}: {}", user.getId(), error);
            mav.addObject("error", "Failed to connect Outlook Calendar: " + error);
            return mav;
        }

        if (code == null) {
            LOGGER.warn("No authorization code received for user {}", user.getId());
            mav.addObject("error", "No authorization code received");
            return mav;
        }

        // Verify state matches user UUID for security
        if (!user.getUuid().equals(state)) {
            LOGGER.warn("State mismatch for user {}", user.getId());
            mav.addObject("error", "Security verification failed");
            return mav;
        }

        try {
            calendarService.handleOutlookOAuthCallback(code);
            mav.addObject("success", "Outlook Calendar connected successfully!");
            LOGGER.info("Successfully connected Outlook Calendar for user {}", user.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to process Outlook OAuth callback for user {}: {}",
                    user.getId(), e.getMessage());
            mav.addObject("error", "Failed to connect Outlook Calendar. Please try again.");
        }

        return mav;
    }
}
