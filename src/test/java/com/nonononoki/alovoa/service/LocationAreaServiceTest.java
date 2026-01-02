package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.entity.user.UserLocationPreferences;
import com.nonononoki.alovoa.entity.user.UserTravelingMode;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserLocationAreaRepository;
import com.nonononoki.alovoa.repo.UserLocationPreferencesRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.UserTravelingModeRepository;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocationAreaServiceTest {

    @Autowired
    private LocationAreaService locationAreaService;

    @Autowired
    private UserLocationAreaRepository areaRepo;

    @Autowired
    private UserLocationPreferencesRepository prefsRepo;

    @Autowired
    private UserTravelingModeRepository travelingRepo;

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
    // Area Management Tests
    // ============================================

    @Test
    void testAddFirstArea_ShouldBecomePrimary() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationArea area = locationAreaService.addArea(
                "Clarendon",
                "Arlington",
                "VA",
                UserLocationArea.DisplayLevel.CITY,
                null,
                UserLocationArea.AreaLabel.HOME,
                true
        );

        assertNotNull(area);
        assertEquals(UserLocationArea.AreaType.PRIMARY, area.getAreaType());
        assertEquals("Clarendon", area.getNeighborhood());
        assertEquals("Arlington", area.getCity());
        assertEquals("VA", area.getState());
        assertEquals("US", area.getCountry());
        assertEquals(0, area.getDisplayOrder());
        assertTrue(area.isVisibleOnProfile());
    }

    @Test
    void testAddSecondArea_ShouldBecomeSecondary() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Add first area
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // Add second area
        UserLocationArea secondArea = locationAreaService.addArea(
                "Dupont Circle",
                "Washington",
                "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD,
                null,
                UserLocationArea.AreaLabel.WORK,
                true
        );

        assertEquals(UserLocationArea.AreaType.SECONDARY, secondArea.getAreaType());
        assertEquals(1, secondArea.getDisplayOrder());
    }

    @Test
    void testAddThirdArea_ShouldBecomeTertiary() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Add three areas
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);
        UserLocationArea thirdArea = locationAreaService.addArea(
                "Bethesda",
                "Bethesda",
                "MD",
                UserLocationArea.DisplayLevel.CITY,
                null,
                UserLocationArea.AreaLabel.FRIENDS_LIVE,
                false
        );

        assertEquals(UserLocationArea.AreaType.TERTIARY, thirdArea.getAreaType());
        assertEquals(2, thirdArea.getDisplayOrder());
        assertFalse(thirdArea.isVisibleOnProfile());
    }

    @Test
    void testAddFourthArea_ShouldThrowException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Add three areas (max allowed)
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);
        locationAreaService.addArea("Bethesda", "Bethesda", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.FRIENDS_LIVE, true);

        // Try to add fourth area
        Exception exception = assertThrows(Exception.class, () -> {
            locationAreaService.addArea("Rockville", "Rockville", "MD",
                    UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HANGOUT, true);
        });

        assertTrue(exception.getMessage().contains("Maximum 3 areas allowed"));
    }

    @Test
    void testUpdateArea_ShouldUpdateSuccessfully() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationArea area = locationAreaService.addArea(
                "Clarendon",
                "Arlington",
                "VA",
                UserLocationArea.DisplayLevel.CITY,
                null,
                UserLocationArea.AreaLabel.HOME,
                true
        );

        UserLocationArea updated = locationAreaService.updateArea(
                area.getId(),
                UserLocationArea.DisplayLevel.REGION,
                "Northern Virginia",
                UserLocationArea.AreaLabel.PREFER_NOT_TO_SAY,
                false
        );

        assertEquals(UserLocationArea.DisplayLevel.REGION, updated.getDisplayLevel());
        assertEquals("Northern Virginia", updated.getDisplayAs());
        assertEquals(UserLocationArea.AreaLabel.PREFER_NOT_TO_SAY, updated.getLabel());
        assertFalse(updated.isVisibleOnProfile());
    }

    @Test
    void testUpdateArea_UnauthorizedUser_ShouldThrowException() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 creates an area
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        UserLocationArea area = locationAreaService.addArea(
                "Clarendon",
                "Arlington",
                "VA",
                UserLocationArea.DisplayLevel.CITY,
                null,
                UserLocationArea.AreaLabel.HOME,
                true
        );

        // User 2 tries to update it
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        Exception exception = assertThrows(Exception.class, () -> {
            locationAreaService.updateArea(
                    area.getId(),
                    UserLocationArea.DisplayLevel.HIDDEN,
                    null,
                    UserLocationArea.AreaLabel.HOME,
                    false
            );
        });

        assertTrue(exception.getMessage().contains("Not authorized"));
    }

    @Test
    void testRemoveArea_NonPrimary_ShouldSucceed() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Add two areas
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        UserLocationArea secondArea = locationAreaService.addArea(
                "Dupont Circle",
                "Washington",
                "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD,
                null,
                UserLocationArea.AreaLabel.WORK,
                true
        );

        long countBefore = areaRepo.countByUser(user);
        locationAreaService.removeArea(secondArea.getId());
        long countAfter = areaRepo.countByUser(user);

        assertEquals(countBefore - 1, countAfter);
        assertEquals(1, countAfter);
    }

    @Test
    void testRemovePrimaryArea_WhenOnlyOne_ShouldThrowException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationArea area = locationAreaService.addArea(
                "Clarendon",
                "Arlington",
                "VA",
                UserLocationArea.DisplayLevel.CITY,
                null,
                UserLocationArea.AreaLabel.HOME,
                true
        );

        Exception exception = assertThrows(Exception.class, () -> {
            locationAreaService.removeArea(area.getId());
        });

        assertTrue(exception.getMessage().contains("Cannot remove primary area"));
    }

    @Test
    void testRemovePrimaryArea_WhenMultipleExist_ShouldPromoteSecondary() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationArea primaryArea = locationAreaService.addArea(
                "Clarendon",
                "Arlington",
                "VA",
                UserLocationArea.DisplayLevel.CITY,
                null,
                UserLocationArea.AreaLabel.HOME,
                true
        );

        UserLocationArea secondaryArea = locationAreaService.addArea(
                "Dupont Circle",
                "Washington",
                "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD,
                null,
                UserLocationArea.AreaLabel.WORK,
                true
        );

        locationAreaService.removeArea(primaryArea.getId());

        // Refresh secondary area from DB
        UserLocationArea promotedArea = areaRepo.findById(secondaryArea.getId()).orElseThrow();
        assertEquals(UserLocationArea.AreaType.PRIMARY, promotedArea.getAreaType());
        assertEquals(0, promotedArea.getDisplayOrder());
    }

    @Test
    void testGetMyAreas_ShouldReturnOrderedList() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);
        locationAreaService.addArea("Bethesda", "Bethesda", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.FRIENDS_LIVE, true);

        List<UserLocationArea> areas = locationAreaService.getMyAreas();

        assertEquals(3, areas.size());
        assertEquals(0, areas.get(0).getDisplayOrder());
        assertEquals(1, areas.get(1).getDisplayOrder());
        assertEquals(2, areas.get(2).getDisplayOrder());
    }

    // ============================================
    // Location Preferences Tests
    // ============================================

    @Test
    void testGetMyPreferences_ShouldCreateDefaultIfNotExists() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationPreferences prefs = locationAreaService.getMyPreferences();

        assertNotNull(prefs);
        assertEquals(20, prefs.getMaxTravelMinutes()); // Default value
        assertTrue(prefs.isRequireAreaOverlap());
        assertTrue(prefs.isShowExceptionalMatches());
        assertEquals(0.90, prefs.getExceptionalMatchThreshold(), 0.001);
    }

    @Test
    void testUpdatePreferences_ShouldUpdateSuccessfully() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationPreferences prefs = locationAreaService.updatePreferences(
                45,
                false,
                true
        );

        assertEquals(45, prefs.getMaxTravelMinutes());
        assertFalse(prefs.isRequireAreaOverlap());
        assertTrue(prefs.isShowExceptionalMatches());
    }

    @Test
    void testSetMovingTo_ShouldSetMovingLocation() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocalDate futureDate = LocalDate.now().plusMonths(2);
        Date movingDate = Date.from(futureDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        UserLocationPreferences prefs = locationAreaService.setMovingTo(
                "Seattle",
                "WA",
                movingDate
        );

        assertEquals("Seattle", prefs.getMovingToCity());
        assertEquals("WA", prefs.getMovingToState());
        assertEquals(movingDate, prefs.getMovingDate());
        assertNotNull(prefs.getMovingDisplay());
        assertTrue(prefs.getMovingDisplay().contains("Seattle"));
    }

    @Test
    void testSetMovingTo_ClearMoving_ShouldClearData() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // First set moving to
        LocalDate futureDate = LocalDate.now().plusMonths(2);
        Date movingDate = Date.from(futureDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        locationAreaService.setMovingTo("Seattle", "WA", movingDate);

        // Then clear it
        UserLocationPreferences prefs = locationAreaService.setMovingTo(null, null, null);

        assertNull(prefs.getMovingToCity());
        assertNull(prefs.getMovingToState());
        assertNull(prefs.getMovingDate());
        assertNull(prefs.getMovingDisplay());
    }

    // ============================================
    // Traveling Mode Tests
    // ============================================

    @Test
    void testEnableTravelingMode_ShouldCreateSuccessfully() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocalDate arriving = LocalDate.now().plusDays(5);
        LocalDate leaving = LocalDate.now().plusDays(10);
        Date arrivingDate = Date.from(arriving.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date leavingDate = Date.from(leaving.atStartOfDay(ZoneId.systemDefault()).toInstant());

        UserTravelingMode traveling = locationAreaService.enableTravelingMode(
                "New York",
                "NY",
                arrivingDate,
                leavingDate,
                true,
                true
        );

        assertNotNull(traveling);
        assertEquals("New York", traveling.getDestinationCity());
        assertEquals("NY", traveling.getDestinationState());
        assertEquals("US", traveling.getDestinationCountry());
        assertEquals(arrivingDate, traveling.getArrivingDate());
        assertEquals(leavingDate, traveling.getLeavingDate());
        assertTrue(traveling.isShowMeThere());
        assertTrue(traveling.isShowLocalsToMe());
        assertTrue(traveling.isActive());
        assertTrue(traveling.isAutoDisable());
    }

    @Test
    void testEnableTravelingMode_InvalidDates_ShouldThrowException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocalDate arriving = LocalDate.now().plusDays(10);
        LocalDate leaving = LocalDate.now().plusDays(5); // Before arriving
        Date arrivingDate = Date.from(arriving.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date leavingDate = Date.from(leaving.atStartOfDay(ZoneId.systemDefault()).toInstant());

        Exception exception = assertThrows(Exception.class, () -> {
            locationAreaService.enableTravelingMode(
                    "New York",
                    "NY",
                    arrivingDate,
                    leavingDate,
                    true,
                    true
            );
        });

        assertTrue(exception.getMessage().contains("Arrival date must be before leaving date"));
    }

    @Test
    void testEnableTravelingMode_ReplacesExisting_ShouldDeleteOldOne() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocalDate arriving1 = LocalDate.now().plusDays(5);
        LocalDate leaving1 = LocalDate.now().plusDays(10);
        Date arrivingDate1 = Date.from(arriving1.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date leavingDate1 = Date.from(leaving1.atStartOfDay(ZoneId.systemDefault()).toInstant());

        // Enable first traveling mode
        locationAreaService.enableTravelingMode("New York", "NY", arrivingDate1, leavingDate1, true, true);

        LocalDate arriving2 = LocalDate.now().plusDays(15);
        LocalDate leaving2 = LocalDate.now().plusDays(20);
        Date arrivingDate2 = Date.from(arriving2.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date leavingDate2 = Date.from(leaving2.atStartOfDay(ZoneId.systemDefault()).toInstant());

        // Enable second traveling mode
        UserTravelingMode traveling2 = locationAreaService.enableTravelingMode(
                "Boston",
                "MA",
                arrivingDate2,
                leavingDate2,
                false,
                true
        );

        // Should only have one traveling mode
        Optional<UserTravelingMode> current = locationAreaService.getMyTravelingMode();
        assertTrue(current.isPresent());
        assertEquals("Boston", current.get().getDestinationCity());
        assertEquals(traveling2.getId(), current.get().getId());
    }

    @Test
    void testDisableTravelingMode_ShouldMarkAsInactive() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocalDate arriving = LocalDate.now().plusDays(5);
        LocalDate leaving = LocalDate.now().plusDays(10);
        Date arrivingDate = Date.from(arriving.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date leavingDate = Date.from(leaving.atStartOfDay(ZoneId.systemDefault()).toInstant());

        locationAreaService.enableTravelingMode("New York", "NY", arrivingDate, leavingDate, true, true);
        locationAreaService.disableTravelingMode();

        Optional<UserTravelingMode> traveling = locationAreaService.getMyTravelingMode();
        assertTrue(traveling.isPresent());
        assertFalse(traveling.get().isActive());
    }

    // ============================================
    // Matching Logic Tests
    // ============================================

    @Test
    void testHasOverlappingAreas_ShouldReturnTrue() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 adds areas
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // User 2 adds overlapping area
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Ballston", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.WORK, true);

        boolean hasOverlap = locationAreaService.hasOverlappingAreas(user1, user2);
        assertTrue(hasOverlap);
    }

    @Test
    void testHasOverlappingAreas_ShouldReturnFalse() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 adds area
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // User 2 adds different area
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);

        boolean hasOverlap = locationAreaService.hasOverlappingAreas(user1, user2);
        assertFalse(hasOverlap);
    }

    @Test
    void testGetOverlappingAreas_ShouldReturnCityNames() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 adds areas
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);

        // User 2 adds overlapping areas
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Ballston", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Georgetown", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HANGOUT, true);

        List<String> overlapping = locationAreaService.getOverlappingAreas(user1, user2);

        assertEquals(2, overlapping.size());
        assertTrue(overlapping.contains("Arlington"));
        assertTrue(overlapping.contains("Washington"));
    }

    @Test
    void testFindUsersWithOverlappingAreas_ShouldReturnMatches() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        User user3 = testUsers.get(2);

        // User 1 adds area
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // User 2 adds overlapping area
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Ballston", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.WORK, true);

        // User 3 adds different area
        Mockito.doReturn(user3).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Georgetown", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        List<User> matches = locationAreaService.findUsersWithOverlappingAreas(user1);

        assertEquals(1, matches.size());
        assertEquals(user2.getId(), matches.get(0).getId());
    }

    @Test
    void testGetLocationDisplay_ShouldReturnVisibleAreas() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, false); // Hidden

        Map<String, Object> display = locationAreaService.getLocationDisplay(user);

        assertNotNull(display);
        assertTrue(display.containsKey("areas"));

        @SuppressWarnings("unchecked")
        List<String> areas = (List<String>) display.get("areas");

        assertEquals(1, areas.size()); // Only visible area
        assertTrue(areas.get(0).contains("Arlington"));
    }

    @Test
    void testGetLocationDisplay_WithTravelingMode_ShouldIncludeTraveling() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        LocalDate arriving = LocalDate.now().minusDays(1); // Currently active
        LocalDate leaving = LocalDate.now().plusDays(5);
        Date arrivingDate = Date.from(arriving.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date leavingDate = Date.from(leaving.atStartOfDay(ZoneId.systemDefault()).toInstant());

        locationAreaService.enableTravelingMode("New York", "NY", arrivingDate, leavingDate, true, true);

        Map<String, Object> display = locationAreaService.getLocationDisplay(user);

        assertTrue((Boolean) display.get("traveling"));
        assertEquals("New York, NY", display.get("travelingTo"));
    }

    @Test
    void testGetLocationDisplay_WithMovingTo_ShouldIncludeMoving() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        LocalDate futureDate = LocalDate.now().plusMonths(2);
        Date movingDate = Date.from(futureDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        locationAreaService.setMovingTo("Seattle", "WA", movingDate);

        Map<String, Object> display = locationAreaService.getLocationDisplay(user);

        assertNotNull(display.get("movingTo"));
        assertTrue(display.get("movingTo").toString().contains("Seattle"));
    }

    // ============================================
    // Scheduled Task Tests
    // ============================================

    @Test
    void testDisableExpiredTravelingModes_ShouldDisableExpired() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create expired traveling mode
        LocalDate arriving = LocalDate.now().minusDays(10);
        LocalDate leaving = LocalDate.now().minusDays(5); // Expired
        Date arrivingDate = Date.from(arriving.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date leavingDate = Date.from(leaving.atStartOfDay(ZoneId.systemDefault()).toInstant());

        locationAreaService.enableTravelingMode("New York", "NY", arrivingDate, leavingDate, true, true);

        // Run scheduled task
        locationAreaService.disableExpiredTravelingModes();

        Optional<UserTravelingMode> traveling = locationAreaService.getMyTravelingMode();
        assertTrue(traveling.isPresent());
        assertFalse(traveling.get().isActive()); // Should be disabled
    }
}
