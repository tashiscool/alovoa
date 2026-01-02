package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.DateSpotSuggestion;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.entity.user.UserLocationPreferences;
import com.nonononoki.alovoa.entity.user.UserTravelingMode;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private LocationAreaService locationService;

    @MockitoBean
    private DateSpotService dateSpotService;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax, firstNameLengthMin);
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    // ============================================
    // Area Management Tests
    // ============================================

    @Test
    @WithMockUser
    @DisplayName("GET /location/areas - Returns user's location areas")
    void testGetMyAreas() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<UserLocationArea> mockAreas = new ArrayList<>();
        Mockito.when(locationService.getMyAreas()).thenReturn(mockAreas);

        mockMvc.perform(get("/location/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /location/areas - Adds new location area")
    void testAddArea() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocationController.AreaRequest request = new LocationController.AreaRequest();
        request.neighborhood = "Downtown";
        request.city = "San Francisco";
        request.state = "CA";
        request.displayLevel = UserLocationArea.DisplayLevel.CITY;
        request.displayAs = "SF";
        request.label = UserLocationArea.AreaLabel.HOME;
        request.visibleOnProfile = true;

        UserLocationArea mockArea = new UserLocationArea();
        Mockito.when(locationService.addArea(anyString(), anyString(), anyString(),
                any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockArea);

        mockMvc.perform(post("/location/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Mockito.verify(locationService).addArea("Downtown", "San Francisco", "CA",
                UserLocationArea.DisplayLevel.CITY, "SF", UserLocationArea.AreaLabel.HOME, true);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /location/areas - Handles exception when adding area")
    void testAddAreaException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocationController.AreaRequest request = new LocationController.AreaRequest();
        request.neighborhood = "Downtown";
        request.city = "San Francisco";
        request.state = "CA";

        // Use nullable() for parameters that may be null
        Mockito.when(locationService.addArea(anyString(), anyString(), anyString(),
                nullable(UserLocationArea.DisplayLevel.class), nullable(String.class),
                nullable(UserLocationArea.AreaLabel.class), anyBoolean()))
                .thenThrow(new RuntimeException("Too many areas"));

        mockMvc.perform(post("/location/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Too many areas"));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /location/areas/{areaId} - Updates existing area")
    void testUpdateArea() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocationController.AreaUpdateRequest request = new LocationController.AreaUpdateRequest();
        request.displayLevel = UserLocationArea.DisplayLevel.REGION;
        request.displayAs = "California";
        request.label = UserLocationArea.AreaLabel.WORK;
        request.visibleOnProfile = false;

        UserLocationArea mockArea = new UserLocationArea();
        Mockito.when(locationService.updateArea(any(Long.class), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockArea);

        mockMvc.perform(put("/location/areas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Mockito.verify(locationService).updateArea(1L, UserLocationArea.DisplayLevel.REGION,
                "California", UserLocationArea.AreaLabel.WORK, false);
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /location/areas/{areaId} - Removes area")
    void testRemoveArea() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.doNothing().when(locationService).removeArea(1L);

        mockMvc.perform(delete("/location/areas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Mockito.verify(locationService).removeArea(1L);
    }

    // ============================================
    // Location Preferences Tests
    // ============================================

    @Test
    @WithMockUser
    @DisplayName("GET /location/preferences - Returns user preferences")
    void testGetPreferences() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationPreferences mockPrefs = new UserLocationPreferences();
        Mockito.when(locationService.getMyPreferences()).thenReturn(mockPrefs);

        mockMvc.perform(get("/location/preferences"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /location/preferences - Updates preferences")
    void testUpdatePreferences() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocationController.PreferencesRequest request = new LocationController.PreferencesRequest();
        request.maxTravelMinutes = 30;
        request.requireAreaOverlap = false;
        request.showExceptionalMatches = true;

        UserLocationPreferences mockPrefs = new UserLocationPreferences();
        Mockito.when(locationService.updatePreferences(anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(mockPrefs);

        mockMvc.perform(put("/location/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Mockito.verify(locationService).updatePreferences(30, false, true);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /location/moving-to - Sets moving to location")
    void testSetMovingTo() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocationController.MovingToRequest request = new LocationController.MovingToRequest();
        request.city = "Austin";
        request.state = "TX";
        request.movingDate = new Date();

        UserLocationPreferences mockPrefs = new UserLocationPreferences();
        Mockito.when(locationService.setMovingTo(anyString(), anyString(), any(Date.class)))
                .thenReturn(mockPrefs);

        mockMvc.perform(post("/location/moving-to")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Mockito.verify(locationService).setMovingTo("Austin", "TX", request.movingDate);
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /location/moving-to - Clears moving to location")
    void testClearMovingTo() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserLocationPreferences mockPrefs = new UserLocationPreferences();
        Mockito.when(locationService.setMovingTo(null, null, null)).thenReturn(mockPrefs);

        mockMvc.perform(delete("/location/moving-to"))
                .andExpect(status().isOk());

        Mockito.verify(locationService).setMovingTo(null, null, null);
    }

    // ============================================
    // Traveling Mode Tests
    // ============================================

    @Test
    @WithMockUser
    @DisplayName("GET /location/traveling - Returns traveling mode when active")
    void testGetTravelingModeActive() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UserTravelingMode mockTraveling = new UserTravelingMode();
        // Set required fields to avoid serialization issues
        mockTraveling.setDestinationCity("New York");
        mockTraveling.setDestinationState("NY");
        mockTraveling.setShowMeThere(true);
        mockTraveling.setShowLocalsToMe(true);
        // Set dates to prevent NPE in isCurrentlyActive(), isUpcoming(), hasEnded()
        mockTraveling.setArrivingDate(new Date());
        mockTraveling.setLeavingDate(new Date(System.currentTimeMillis() + 86400000)); // tomorrow
        mockTraveling.setDisplayAs("New York, NY");
        Mockito.when(locationService.getMyTravelingMode()).thenReturn(Optional.of(mockTraveling));

        mockMvc.perform(get("/location/traveling"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /location/traveling - Returns inactive when not traveling")
    void testGetTravelingModeInactive() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(locationService.getMyTravelingMode()).thenReturn(Optional.empty());

        mockMvc.perform(get("/location/traveling"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /location/traveling - Enables traveling mode")
    void testEnableTravelingMode() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        LocationController.TravelingRequest request = new LocationController.TravelingRequest();
        request.destinationCity = "New York";
        request.destinationState = "NY";
        request.arrivingDate = new Date();
        request.leavingDate = new Date(System.currentTimeMillis() + 86400000);
        request.showMeThere = true;
        request.showLocalsToMe = false;

        UserTravelingMode mockTraveling = new UserTravelingMode();
        // Set required fields to avoid serialization issues
        mockTraveling.setDestinationCity("New York");
        mockTraveling.setDestinationState("NY");
        mockTraveling.setShowMeThere(true);
        mockTraveling.setShowLocalsToMe(false);
        // Set dates to prevent NPE in isCurrentlyActive(), isUpcoming(), hasEnded()
        mockTraveling.setArrivingDate(request.arrivingDate);
        mockTraveling.setLeavingDate(request.leavingDate);
        mockTraveling.setDisplayAs("New York, NY");
        Mockito.when(locationService.enableTravelingMode(anyString(), anyString(),
                any(Date.class), any(Date.class), anyBoolean(), anyBoolean()))
                .thenReturn(mockTraveling);

        mockMvc.perform(post("/location/traveling")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Mockito.verify(locationService).enableTravelingMode("New York", "NY",
                request.arrivingDate, request.leavingDate, true, false);
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /location/traveling - Disables traveling mode")
    void testDisableTravelingMode() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.doNothing().when(locationService).disableTravelingMode();

        mockMvc.perform(delete("/location/traveling"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Mockito.verify(locationService).disableTravelingMode();
    }

    // ============================================
    // Date Spot Suggestions Tests
    // ============================================

    @Test
    @WithMockUser
    @DisplayName("GET /location/date-spots - Returns date spots in user's areas")
    void testGetDateSpots() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, List<DateSpotSuggestion>> mockSpots = new HashMap<>();
        mockSpots.put("Downtown", new ArrayList<>());
        Mockito.when(dateSpotService.getSpotsInMyAreas()).thenReturn(mockSpots);

        mockMvc.perform(get("/location/date-spots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /location/date-spots/safe - Returns safe spots by neighborhood")
    void testGetSafeSpots() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<DateSpotSuggestion> mockSpots = new ArrayList<>();
        Mockito.when(dateSpotService.getSafeSpots("Downtown")).thenReturn(mockSpots);

        mockMvc.perform(get("/location/date-spots/safe")
                        .param("neighborhood", "Downtown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        Mockito.verify(dateSpotService).getSafeSpots("Downtown");
    }

    @Test
    @WithMockUser
    @DisplayName("GET /location/date-spots/daytime - Returns daytime-friendly spots")
    void testGetDaytimeSpots() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<DateSpotSuggestion> mockSpots = new ArrayList<>();
        Mockito.when(dateSpotService.getDaytimeSpots("Downtown")).thenReturn(mockSpots);

        mockMvc.perform(get("/location/date-spots/daytime")
                        .param("neighborhood", "Downtown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        Mockito.verify(dateSpotService).getDaytimeSpots("Downtown");
    }

    @Test
    @WithMockUser
    @DisplayName("GET /location/date-spots/budget - Returns budget-friendly spots")
    void testGetBudgetSpots() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<DateSpotSuggestion> mockSpots = new ArrayList<>();
        Mockito.when(dateSpotService.getBudgetFriendlySpots("Downtown")).thenReturn(mockSpots);

        mockMvc.perform(get("/location/date-spots/budget")
                        .param("neighborhood", "Downtown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        Mockito.verify(dateSpotService).getBudgetFriendlySpots("Downtown");
    }

    @Test
    @WithMockUser
    @DisplayName("GET /location/date-spots/type/{venueType} - Returns spots by venue type")
    void testGetSpotsByType() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<DateSpotSuggestion> mockSpots = new ArrayList<>();
        Mockito.when(dateSpotService.getSpotsByType("Downtown", DateSpotSuggestion.VenueType.COFFEE_SHOP))
                .thenReturn(mockSpots);

        // Use valid VenueType enum value (COFFEE_SHOP, not CAFE)
        mockMvc.perform(get("/location/date-spots/type/COFFEE_SHOP")
                        .param("neighborhood", "Downtown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        Mockito.verify(dateSpotService).getSpotsByType("Downtown", DateSpotSuggestion.VenueType.COFFEE_SHOP);
    }

    // ============================================
    // Location Display and Overlap Tests
    // ============================================

    @Test
    @WithMockUser
    @DisplayName("GET /location/overlap/{userId} - Checks area overlap with another user")
    void testCheckOverlap() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        UserLocationArea mockArea = new UserLocationArea();
        mockArea.setUser(user1);
        List<UserLocationArea> areas = List.of(mockArea);
        Mockito.when(locationService.getMyAreas()).thenReturn(areas);

        Mockito.when(locationService.hasOverlappingAreas(user1, user2)).thenReturn(true);
        Mockito.when(locationService.getOverlappingAreas(user1, user2))
                .thenReturn(List.of("Downtown SF"));

        mockMvc.perform(get("/location/overlap/" + user2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasOverlap").value(true))
                .andExpect(jsonPath("$.overlappingAreas").isArray())
                .andExpect(jsonPath("$.overlappingAreas.length()").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /location/overlap/{userId} - Returns no overlap when user has no areas")
    void testCheckOverlapNoAreas() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Mockito.when(locationService.getMyAreas()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/location/overlap/" + user2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasOverlap").value(false))
                .andExpect(jsonPath("$.reason").value("You haven't set up your location areas yet"));
    }
}
