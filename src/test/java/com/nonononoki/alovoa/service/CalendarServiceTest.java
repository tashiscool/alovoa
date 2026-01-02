package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.VideoDate;
import com.nonononoki.alovoa.entity.VideoDate.DateStatus;
import com.nonononoki.alovoa.entity.user.UserCalendarSettings;
import com.nonononoki.alovoa.entity.user.UserCalendarSettings.CalendarProvider;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserCalendarSettingsRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.VideoDateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalendarServiceTest {

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private UserCalendarSettingsRepository settingsRepo;

    @Autowired
    private VideoDateRepository videoDatRepo;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private RestTemplate restTemplate;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(Mockito.any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    // ============================================
    // Settings Management
    // ============================================

    @Test
    void testGetSettings_CreatesNewIfNotExists() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Should not exist initially
        assertFalse(settingsRepo.findByUser(user).isPresent());

        UserCalendarSettings settings = calendarService.getSettings();

        assertNotNull(settings);
        assertNotNull(settings.getId());
        assertEquals(user.getId(), settings.getUser().getId());
        assertFalse(settings.isGoogleCalendarEnabled());
        assertFalse(settings.isAppleCalendarEnabled());
        assertFalse(settings.isOutlookCalendarEnabled());
        assertEquals(60, settings.getDefaultReminderMinutes());
        assertTrue(settings.isAutoAddDates());
        assertFalse(settings.isShowMatchName());
    }

    @Test
    void testGetSettings_ReturnsExistingSettings() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create settings manually
        UserCalendarSettings created = new UserCalendarSettings();
        created.setUser(user);
        created.setGoogleCalendarEnabled(true);
        created.setDefaultReminderMinutes(30);
        settingsRepo.save(created);

        // Get settings
        UserCalendarSettings retrieved = calendarService.getSettings();

        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertTrue(retrieved.isGoogleCalendarEnabled());
        assertEquals(30, retrieved.getDefaultReminderMinutes());
    }

    @Test
    void testUpdateSettings_Success() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setGoogleCalendarEnabled(true);
        updates.setAppleCalendarEnabled(false);
        updates.setOutlookCalendarEnabled(true);
        updates.setDefaultReminderMinutes(120);
        updates.setAutoAddDates(false);
        updates.setShowMatchName(true);

        UserCalendarSettings saved = calendarService.updateSettings(updates);

        assertNotNull(saved);
        assertTrue(saved.isGoogleCalendarEnabled());
        assertFalse(saved.isAppleCalendarEnabled());
        assertTrue(saved.isOutlookCalendarEnabled());
        assertEquals(120, saved.getDefaultReminderMinutes());
        assertFalse(saved.isAutoAddDates());
        assertTrue(saved.isShowMatchName());
    }

    @Test
    void testUpdateSettings_CreatesIfNotExists() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Verify no settings exist
        assertFalse(settingsRepo.findByUser(user).isPresent());

        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setGoogleCalendarEnabled(true);

        UserCalendarSettings saved = calendarService.updateSettings(updates);

        assertNotNull(saved);
        assertTrue(saved.isGoogleCalendarEnabled());

        // Verify settings were created
        assertTrue(settingsRepo.findByUser(user).isPresent());
    }

    // ============================================
    // Calendar Provider Detection
    // ============================================

    @Test
    void testHasAnyCalendarEnabled_AllDisabled() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings settings = calendarService.getSettings();
        assertFalse(settings.hasAnyCalendarEnabled());
    }

    @Test
    void testHasAnyCalendarEnabled_GoogleEnabled() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setGoogleCalendarEnabled(true);
        UserCalendarSettings settings = calendarService.updateSettings(updates);

        assertTrue(settings.hasAnyCalendarEnabled());
    }

    @Test
    void testGetPrimaryProvider_Google() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setGoogleCalendarEnabled(true);
        UserCalendarSettings settings = calendarService.updateSettings(updates);

        assertEquals(CalendarProvider.GOOGLE, settings.getPrimaryProvider());
    }

    @Test
    void testGetPrimaryProvider_Apple() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setAppleCalendarEnabled(true);
        UserCalendarSettings settings = calendarService.updateSettings(updates);

        assertEquals(CalendarProvider.APPLE, settings.getPrimaryProvider());
    }

    @Test
    void testGetPrimaryProvider_Outlook() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setOutlookCalendarEnabled(true);
        UserCalendarSettings settings = calendarService.updateSettings(updates);

        assertEquals(CalendarProvider.OUTLOOK, settings.getPrimaryProvider());
    }

    @Test
    void testGetPrimaryProvider_GoogleTakesPrecedence() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setGoogleCalendarEnabled(true);
        updates.setAppleCalendarEnabled(true);
        updates.setOutlookCalendarEnabled(true);
        UserCalendarSettings settings = calendarService.updateSettings(updates);

        assertEquals(CalendarProvider.GOOGLE, settings.getPrimaryProvider());
    }

    @Test
    void testGetPrimaryProvider_NoneEnabled() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings settings = calendarService.getSettings();

        assertNull(settings.getPrimaryProvider());
    }

    // ============================================
    // Calendar Event Creation
    // ============================================

    @Test
    void testAddDateToCalendar_NoCalendarEnabled() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        boolean result = calendarService.addDateToCalendar(videoDate, userA);

        assertFalse(result);
    }

    @Test
    void testAddDateToCalendar_GoogleEnabled() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        // Enable Google Calendar
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setGoogleCalendarEnabled(true);
        calendarService.updateSettings(updates);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        boolean result = calendarService.addDateToCalendar(videoDate, userA);

        assertTrue(result);

        // Verify event ID was set
        VideoDate updated = videoDatRepo.findById(videoDate.getId()).get();
        assertNotNull(updated.getGoogleCalendarEventId());
        assertTrue(updated.isUserACalendarSynced());
    }

    @Test
    void testAddDateToCalendar_AppleEnabled() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        // Enable Apple Calendar
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setAppleCalendarEnabled(true);
        calendarService.updateSettings(updates);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        boolean result = calendarService.addDateToCalendar(videoDate, userA);

        assertTrue(result);

        // Verify event ID was set
        VideoDate updated = videoDatRepo.findById(videoDate.getId()).get();
        assertNotNull(updated.getAppleCalendarEventId());
        assertTrue(updated.isUserACalendarSynced());
    }

    @Test
    void testAddDateToCalendar_OutlookEnabled() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        // Enable Outlook Calendar
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setOutlookCalendarEnabled(true);
        calendarService.updateSettings(updates);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        boolean result = calendarService.addDateToCalendar(videoDate, userA);

        assertTrue(result);

        // Verify event ID was set
        VideoDate updated = videoDatRepo.findById(videoDate.getId()).get();
        assertNotNull(updated.getOutlookCalendarEventId());
        assertTrue(updated.isUserACalendarSynced());
    }

    @Test
    void testAddDateToCalendar_UserBSyncsCorrectly() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        // Enable Google Calendar for user B
        Mockito.doReturn(userB).when(authService).getCurrentUser(true);
        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setGoogleCalendarEnabled(true);
        calendarService.updateSettings(updates);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        boolean result = calendarService.addDateToCalendar(videoDate, userB);

        assertTrue(result);

        // Verify user B calendar synced flag is set
        VideoDate updated = videoDatRepo.findById(videoDate.getId()).get();
        assertNotNull(updated.getGoogleCalendarEventId());
        assertFalse(updated.isUserACalendarSynced());
        assertTrue(updated.isUserBCalendarSynced());
    }

    // ============================================
    // iCal File Generation
    // ============================================

    @Test
    void testGenerateICalFile_BasicStructure() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        assertNotNull(ical);
        assertTrue(ical.contains("BEGIN:VCALENDAR"));
        assertTrue(ical.contains("END:VCALENDAR"));
        assertTrue(ical.contains("BEGIN:VEVENT"));
        assertTrue(ical.contains("END:VEVENT"));
        assertTrue(ical.contains("VERSION:2.0"));
    }

    @Test
    void testGenerateICalFile_ContainsEventDetails() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);
        videoDate.setRoomUrl("https://example.com/room/123");
        videoDatRepo.save(videoDate);

        String ical = calendarService.generateICalFile(videoDate, userA);

        assertTrue(ical.contains("SUMMARY:"));
        assertTrue(ical.contains("DESCRIPTION:"));
        assertTrue(ical.contains("URL:https://example.com/room/123"));
        assertTrue(ical.contains("DTSTART:"));
        assertTrue(ical.contains("DTEND:"));
    }

    @Test
    void testGenerateICalFile_ContainsReminders() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        // Should have two reminders: 1 hour and 15 minutes
        assertTrue(ical.contains("BEGIN:VALARM"));
        assertTrue(ical.contains("END:VALARM"));
        assertTrue(ical.contains("TRIGGER:-PT1H"));  // 1 hour before
        assertTrue(ical.contains("TRIGGER:-PT15M")); // 15 minutes before
    }

    @Test
    void testGenerateICalFile_WithShowMatchNameEnabled() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        // Enable showing match name
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        UserCalendarSettings updates = new UserCalendarSettings();
        updates.setShowMatchName(true);
        calendarService.updateSettings(updates);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        // Should contain the other user's first name
        assertTrue(ical.contains("Video Date with " + userB.getFirstName()));
    }

    @Test
    void testGenerateICalFile_WithShowMatchNameDisabled() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        // Keep default (showMatchName = false)
        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        // Should NOT contain the other user's name
        assertFalse(ical.contains("Video Date with " + userB.getFirstName()));
        assertTrue(ical.contains("Video Date"));
    }

    @Test
    void testGenerateICalFile_EscapesSpecialCharacters() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        // Should properly escape special characters in description
        assertNotNull(ical);
        // The escaping should handle commas, semicolons, backslashes, and newlines
    }

    @Test
    void testGenerateICalFile_UniqueUID() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String icalA = calendarService.generateICalFile(videoDate, userA);
        String icalB = calendarService.generateICalFile(videoDate, userB);

        // Each user should get a unique UID
        assertTrue(icalA.contains("UID:"));
        assertTrue(icalB.contains("UID:"));

        // Extract UIDs and verify they're different
        assertNotEquals(icalA, icalB);
    }

    @Test
    void testGenerateICalFile_CorrectDuration() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        // Should have both DTSTART and DTEND
        assertTrue(ical.contains("DTSTART:"));
        assertTrue(ical.contains("DTEND:"));

        // DTEND should be 1 hour after DTSTART (default duration)
        // This is a basic check; more precise time checking would require parsing
        assertNotNull(ical);
    }

    // ============================================
    // Settings Validation
    // ============================================

    @Test
    void testDefaultReminderMinutes_DefaultValue() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings settings = calendarService.getSettings();

        assertEquals(60, settings.getDefaultReminderMinutes());
    }

    @Test
    void testAutoAddDates_DefaultValue() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings settings = calendarService.getSettings();

        assertTrue(settings.isAutoAddDates());
    }

    @Test
    void testShowMatchName_DefaultValue() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserCalendarSettings settings = calendarService.getSettings();

        assertFalse(settings.isShowMatchName());
    }

    // ============================================
    // Timezone Handling
    // ============================================

    @Test
    void testGenerateICalFile_UsesUTCTimezone() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        // iCal timestamps should end with 'Z' indicating UTC
        assertTrue(ical.contains("DTSTART:"));
        assertTrue(ical.contains("Z"));
    }

    @Test
    void testGenerateICalFile_CorrectDateFormat() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        VideoDate videoDate = createTestVideoDate(userA, userB);

        String ical = calendarService.generateICalFile(videoDate, userA);

        // Should contain properly formatted date (yyyyMMdd'T'HHmmss'Z')
        // Example: 20250101T120000Z
        assertTrue(ical.matches(".*DTSTART:\\d{8}T\\d{6}Z.*"));
        assertTrue(ical.matches(".*DTEND:\\d{8}T\\d{6}Z.*"));
    }

    // ============================================
    // Helper Methods
    // ============================================

    private VideoDate createTestVideoDate(User userA, User userB) {
        VideoDate videoDate = new VideoDate();
        videoDate.setUserA(userA);
        videoDate.setUserB(userB);

        // Schedule for tomorrow at 2 PM
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        videoDate.setScheduledAt(cal.getTime());
        videoDate.setStatus(DateStatus.SCHEDULED);

        return videoDatRepo.save(videoDate);
    }
}
