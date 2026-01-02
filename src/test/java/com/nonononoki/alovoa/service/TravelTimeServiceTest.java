package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.AreaCentroid;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.repo.AreaCentroidRepository;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserLocationAreaRepository;
import com.nonononoki.alovoa.repo.UserRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TravelTimeServiceTest {

    @Autowired
    private TravelTimeService travelTimeService;

    @Autowired
    private LocationAreaService locationAreaService;

    @Autowired
    private AreaCentroidRepository centroidRepo;

    @Autowired
    private UserLocationAreaRepository areaRepo;

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
    private List<AreaCentroid> testCentroids;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(Mockito.any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);

        // Create test centroids for DC Metro area
        testCentroids = createTestCentroids();
    }

    @AfterEach
    void after() throws Exception {
        // Clean up centroids
        if (testCentroids != null) {
            centroidRepo.deleteAll(testCentroids);
        }
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    private List<AreaCentroid> createTestCentroids() {
        List<AreaCentroid> centroids = new ArrayList<>();

        // Arlington, VA (Clarendon neighborhood)
        AreaCentroid clarendon = new AreaCentroid("Clarendon", "Arlington", "VA", 38.8868, -77.0952);
        clarendon.setReferencePoint("Clarendon Metro Station");
        clarendon.setDisplayName("Clarendon, Arlington, VA");
        clarendon.setMetroArea("DC Metro");
        centroids.add(centroidRepo.save(clarendon));

        // Arlington, VA (City-level)
        AreaCentroid arlington = new AreaCentroid("Arlington", "VA", 38.8799, -77.1067);
        arlington.setReferencePoint("Arlington County Center");
        arlington.setDisplayName("Arlington, VA");
        arlington.setMetroArea("DC Metro");
        centroids.add(centroidRepo.save(arlington));

        // Washington, DC (Dupont Circle neighborhood)
        AreaCentroid dupont = new AreaCentroid("Dupont Circle", "Washington", "DC", 38.9097, -77.0435);
        dupont.setReferencePoint("Dupont Circle Metro Station");
        dupont.setDisplayName("Dupont Circle, Washington, DC");
        dupont.setMetroArea("DC Metro");
        centroids.add(centroidRepo.save(dupont));

        // Washington, DC (City-level)
        AreaCentroid dc = new AreaCentroid("Washington", "DC", 38.9072, -77.0369);
        dc.setReferencePoint("Downtown DC");
        dc.setDisplayName("Washington, DC");
        dc.setMetroArea("DC Metro");
        centroids.add(centroidRepo.save(dc));

        // Bethesda, MD
        AreaCentroid bethesda = new AreaCentroid("Bethesda", "MD", 38.9847, -77.0947);
        bethesda.setReferencePoint("Bethesda Metro Station");
        bethesda.setDisplayName("Bethesda, MD");
        bethesda.setMetroArea("DC Metro");
        centroids.add(centroidRepo.save(bethesda));

        // Rockville, MD (further away)
        AreaCentroid rockville = new AreaCentroid("Rockville", "MD", 39.0840, -77.1528);
        rockville.setReferencePoint("Rockville Town Center");
        rockville.setDisplayName("Rockville, MD");
        rockville.setMetroArea("DC Metro");
        centroids.add(centroidRepo.save(rockville));

        return centroids;
    }

    // ============================================
    // Travel Time Calculation Tests
    // ============================================

    @Test
    void testCalculateTravelTime_BetweenCloseAreas_ShouldReturnLowTime() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 in Clarendon
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // User 2 in Dupont Circle (close to Clarendon)
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        assertTrue(travelTime > 0);
        assertTrue(travelTime < 30); // Should be under 30 minutes
        assertEquals(0, travelTime % 5); // Should be rounded to 5 min increments
    }

    @Test
    void testCalculateTravelTime_BetweenFarAreas_ShouldReturnHigherTime() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 in Clarendon
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // User 2 in Rockville (further away)
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Rockville", "Rockville", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        assertTrue(travelTime > 30); // Should be over 30 minutes
        assertEquals(0, travelTime % 5); // Should be rounded to 5 min increments
    }

    @Test
    void testCalculateTravelTime_NoAreas_ShouldReturnNegative() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        assertEquals(-1, travelTime); // Cannot calculate
    }

    @Test
    void testCalculateTravelTime_OneUserNoAreas_ShouldReturnNegative() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Only user 1 has area
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        assertEquals(-1, travelTime); // Cannot calculate
    }

    @Test
    void testCalculateTravelTime_MultipleAreas_ShouldReturnMinimum() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 has areas in Clarendon and Bethesda
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Bethesda", "Bethesda", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.WORK, true);

        // User 2 has area in Bethesda (overlaps with user1's second area)
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Bethesda", "Bethesda", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        // Should be minimal since they both have Bethesda
        assertTrue(travelTime >= 5); // Minimum is 5 minutes
        assertTrue(travelTime < 15); // Should be very close
    }

    @Test
    void testCalculateTravelTime_MinimumFiveMinutes() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Both users in same neighborhood
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.WORK, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        assertEquals(5, travelTime); // Should be minimum 5 minutes
    }

    // ============================================
    // Display and Formatting Tests
    // ============================================

    @Test
    void testGetTravelTimeDisplay_ShouldFormatCorrectly() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        String display = travelTimeService.getTravelTimeDisplay(user1, user2);

        assertNotNull(display);
        assertTrue(display.startsWith("~"));
        assertTrue(display.endsWith("min"));
        assertTrue(display.contains(" "));
    }

    @Test
    void testGetTravelTimeDisplay_NoAreas_ShouldReturnNull() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        String display = travelTimeService.getTravelTimeDisplay(user1, user2);
        assertNull(display);
    }

    // ============================================
    // Bucket Tests
    // ============================================

    @Test
    void testGetTravelTimeBucket_Under15_ShouldReturnCorrectBucket() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Set up users in same area for very short travel time
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.WORK, true);

        TravelTimeService.TravelTimeBucket bucket = travelTimeService.getTravelTimeBucket(user1, user2);

        assertEquals(TravelTimeService.TravelTimeBucket.UNDER_15, bucket);
    }

    @Test
    void testGetTravelTimeBucket_Unknown_ShouldReturnUnknownBucket() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        TravelTimeService.TravelTimeBucket bucket = travelTimeService.getTravelTimeBucket(user1, user2);

        assertEquals(TravelTimeService.TravelTimeBucket.UNKNOWN, bucket);
    }

    @Test
    void testTravelTimeBucket_EnumProperties() throws Exception {
        TravelTimeService.TravelTimeBucket under15 = TravelTimeService.TravelTimeBucket.UNDER_15;

        assertEquals("Under 15 min", under15.getDisplayName());
        assertEquals(0, under15.getMinMinutes());
        assertEquals(15, under15.getMaxMinutes());
    }

    // ============================================
    // Filtering and Sorting Tests
    // ============================================

    @Test
    void testFilterByTravelTime_ShouldFilterCorrectly() throws Exception {
        User currentUser = testUsers.get(0);
        User nearUser = testUsers.get(1);
        User farUser = testUsers.get(2);

        // Current user in Clarendon
        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // Near user in Dupont Circle
        Mockito.doReturn(nearUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        // Far user in Rockville
        Mockito.doReturn(farUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Rockville", "Rockville", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        List<User> candidates = List.of(nearUser, farUser);
        List<User> filtered = travelTimeService.filterByTravelTime(currentUser, candidates, 30);

        // Should only include near user
        assertEquals(1, filtered.size());
        assertEquals(nearUser.getId(), filtered.get(0).getId());
    }

    @Test
    void testFilterByTravelTime_EmptyList_ShouldReturnEmpty() throws Exception {
        User currentUser = testUsers.get(0);
        List<User> candidates = new ArrayList<>();

        List<User> filtered = travelTimeService.filterByTravelTime(currentUser, candidates, 30);

        assertTrue(filtered.isEmpty());
    }

    @Test
    void testSortByTravelTime_ShouldSortClosestFirst() throws Exception {
        User currentUser = testUsers.get(0);
        User nearUser = testUsers.get(1);
        User midUser = testUsers.get(2);

        // Current user in Clarendon
        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // Near user in Arlington
        Mockito.doReturn(nearUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // Mid user in DC
        Mockito.doReturn(midUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        List<User> candidates = List.of(midUser, nearUser); // Unsorted
        List<User> sorted = travelTimeService.sortByTravelTime(currentUser, candidates);

        assertEquals(2, sorted.size());
        assertEquals(nearUser.getId(), sorted.get(0).getId()); // Closest first
        assertEquals(midUser.getId(), sorted.get(1).getId());
    }

    @Test
    void testSortByTravelTime_WithUnknown_ShouldPutUnknownLast() throws Exception {
        User currentUser = testUsers.get(0);
        User knownUser = testUsers.get(1);
        User unknownUser = testUsers.get(2);

        // Current user in Clarendon
        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // Known user in DC
        Mockito.doReturn(knownUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        // Unknown user has no areas

        List<User> candidates = List.of(unknownUser, knownUser);
        List<User> sorted = travelTimeService.sortByTravelTime(currentUser, candidates);

        assertEquals(2, sorted.size());
        assertEquals(knownUser.getId(), sorted.get(0).getId()); // Known first
        assertEquals(unknownUser.getId(), sorted.get(1).getId()); // Unknown last
    }

    @Test
    void testGroupByTravelTime_ShouldGroupCorrectly() throws Exception {
        User currentUser = testUsers.get(0);
        User nearUser = testUsers.get(1);
        User farUser = testUsers.get(2);

        // Current user in Clarendon
        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // Near user in same area
        Mockito.doReturn(nearUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        // Far user in Rockville
        Mockito.doReturn(farUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Rockville", "Rockville", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        List<User> candidates = List.of(nearUser, farUser);
        Map<TravelTimeService.TravelTimeBucket, List<User>> grouped =
                travelTimeService.groupByTravelTime(currentUser, candidates);

        assertNotNull(grouped);
        assertTrue(grouped.containsKey(TravelTimeService.TravelTimeBucket.UNDER_15));
        assertTrue(grouped.containsKey(TravelTimeService.TravelTimeBucket.UNDER_30));

        // Near user should be in UNDER_15
        assertFalse(grouped.get(TravelTimeService.TravelTimeBucket.UNDER_15).isEmpty());
        assertEquals(nearUser.getId(), grouped.get(TravelTimeService.TravelTimeBucket.UNDER_15).get(0).getId());
    }

    // ============================================
    // Travel Time Info Tests
    // ============================================

    @Test
    void testGetTravelTimeInfo_ShouldReturnCompleteInfo() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Both users in Arlington
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Ballston", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.WORK, true);

        TravelTimeService.TravelTimeInfo info = travelTimeService.getTravelTimeInfo(user1, user2);

        assertNotNull(info);
        assertTrue(info.getMinutes() > 0);
        assertNotNull(info.getDisplay());
        assertTrue(info.getDisplay().contains("min"));
        assertNotNull(info.getBucket());
        assertTrue(info.isHasOverlappingAreas()); // Both in Arlington
        assertFalse(info.getOverlappingAreas().isEmpty());
        assertTrue(info.getOverlappingAreas().contains("Arlington"));
    }

    @Test
    void testGetTravelTimeInfo_NoOverlap_ShouldIndicateNoOverlap() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Different cities
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        TravelTimeService.TravelTimeInfo info = travelTimeService.getTravelTimeInfo(user1, user2);

        assertNotNull(info);
        assertFalse(info.isHasOverlappingAreas());
        assertTrue(info.getOverlappingAreas().isEmpty());
    }

    @Test
    void testGetTravelTimeInfo_NoAreas_ShouldReturnUnknown() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        TravelTimeService.TravelTimeInfo info = travelTimeService.getTravelTimeInfo(user1, user2);

        assertNotNull(info);
        assertEquals(-1, info.getMinutes());
        assertNull(info.getDisplay());
        assertEquals(TravelTimeService.TravelTimeBucket.UNKNOWN, info.getBucket());
        assertFalse(info.isHasOverlappingAreas());
        assertTrue(info.getOverlappingAreas().isEmpty());
    }

    // ============================================
    // Haversine Distance Tests (Indirect)
    // ============================================

    @Test
    void testHaversineDistanceCalculation_ShouldBeReasonable() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Clarendon to Dupont is about 3-4 miles
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        // At 25 mph average + 40% urban penalty, 3-4 miles should be roughly 10-20 minutes
        assertTrue(travelTime >= 10);
        assertTrue(travelTime <= 25);
    }

    @Test
    void testTravelTimeRounding_ShouldAlwaysBeMultipleOfFive() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Bethesda", "Bethesda", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        assertTrue(travelTime > 0);
        assertEquals(0, travelTime % 5); // Must be divisible by 5
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    void testCalculateTravelTime_NoCentroidData_ShouldReturnNegative() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Add areas with cities that don't have centroid data
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Downtown", "UnknownCity", "XX",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Uptown", "AnotherUnknownCity", "YY",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        int travelTime = travelTimeService.calculateTravelTime(user1, user2);

        assertEquals(-1, travelTime); // Cannot calculate without centroid data
    }

    @Test
    void testFilterByTravelTime_HighMaxMinutes_ShouldIncludeAll() throws Exception {
        User currentUser = testUsers.get(0);
        User nearUser = testUsers.get(1);
        User farUser = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(nearUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.doReturn(farUser).when(authService).getCurrentUser(true);
        locationAreaService.addArea("Rockville", "Rockville", "MD",
                UserLocationArea.DisplayLevel.CITY, null, UserLocationArea.AreaLabel.HOME, true);

        List<User> candidates = List.of(nearUser, farUser);
        List<User> filtered = travelTimeService.filterByTravelTime(currentUser, candidates, 120); // 2 hours

        assertEquals(2, filtered.size()); // All should be included
    }
}
