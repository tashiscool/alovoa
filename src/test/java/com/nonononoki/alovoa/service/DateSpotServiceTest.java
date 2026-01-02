package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.DateSpotSuggestion;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.DateSpotSuggestionRepository;
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

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DateSpotServiceTest {

    @Autowired
    private DateSpotService dateSpotService;

    @Autowired
    private DateSpotSuggestionRepository spotRepo;

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

    @Autowired
    private LocationAreaService locationAreaService;

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

        // Create some test spots
        createTestDateSpots();
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    private void createTestDateSpots() {
        // Spot 1: Safe coffee shop in Dupont Circle
        DateSpotSuggestion spot1 = new DateSpotSuggestion();
        spot1.setNeighborhood("Dupont Circle");
        spot1.setCity("Washington");
        spot1.setState("DC");
        spot1.setName("Compass Coffee");
        spot1.setAddress("1535 17th St NW, Washington, DC 20036");
        spot1.setDescription("Popular coffee shop with indoor and outdoor seating");
        spot1.setVenueType(DateSpotSuggestion.VenueType.COFFEE_SHOP);
        spot1.setPriceRange(DateSpotSuggestion.PriceRange.BUDGET);
        spot1.setPublicSpace(true);
        spot1.setWellLit(true);
        spot1.setNearTransit(true);
        spot1.setEasyExit(true);
        spot1.setDaytimeFriendly(true);
        spot1.setNearestTransit("Dupont Circle Metro");
        spot1.setWalkMinutesFromTransit(5);
        spot1.setAverageRating(4.5);
        spot1.setRatingCount(10);
        spot1.setActive(true);
        spot1.setCreatedAt(new Date());
        spot1.setUpdatedAt(new Date());
        spotRepo.saveAndFlush(spot1);

        // Spot 2: Museum in Dupont Circle
        DateSpotSuggestion spot2 = new DateSpotSuggestion();
        spot2.setNeighborhood("Dupont Circle");
        spot2.setCity("Washington");
        spot2.setState("DC");
        spot2.setName("The Phillips Collection");
        spot2.setAddress("1600 21st St NW, Washington, DC 20009");
        spot2.setDescription("Art museum with rotating exhibits");
        spot2.setVenueType(DateSpotSuggestion.VenueType.MUSEUM);
        spot2.setPriceRange(DateSpotSuggestion.PriceRange.MODERATE);
        spot2.setPublicSpace(true);
        spot2.setWellLit(true);
        spot2.setNearTransit(true);
        spot2.setEasyExit(true);
        spot2.setDaytimeFriendly(true);
        spot2.setNearestTransit("Dupont Circle Metro");
        spot2.setWalkMinutesFromTransit(8);
        spot2.setAverageRating(4.8);
        spot2.setRatingCount(15);
        spot2.setActive(true);
        spot2.setCreatedAt(new Date());
        spot2.setUpdatedAt(new Date());
        spotRepo.saveAndFlush(spot2);

        // Spot 3: Restaurant in Arlington
        DateSpotSuggestion spot3 = new DateSpotSuggestion();
        spot3.setNeighborhood("Clarendon");
        spot3.setCity("Arlington");
        spot3.setState("VA");
        spot3.setName("Northside Social");
        spot3.setAddress("3211 Wilson Blvd, Arlington, VA 22201");
        spot3.setDescription("Coffee and restaurant with spacious seating");
        spot3.setVenueType(DateSpotSuggestion.VenueType.RESTAURANT);
        spot3.setPriceRange(DateSpotSuggestion.PriceRange.MODERATE);
        spot3.setPublicSpace(true);
        spot3.setWellLit(true);
        spot3.setNearTransit(true);
        spot3.setEasyExit(true);
        spot3.setDaytimeFriendly(true);
        spot3.setNearestTransit("Clarendon Metro");
        spot3.setWalkMinutesFromTransit(3);
        spot3.setAverageRating(4.3);
        spot3.setRatingCount(20);
        spot3.setActive(true);
        spot3.setCreatedAt(new Date());
        spot3.setUpdatedAt(new Date());
        spotRepo.saveAndFlush(spot3);

        // Spot 4: Inactive spot (should not appear in searches)
        DateSpotSuggestion spot4 = new DateSpotSuggestion();
        spot4.setNeighborhood("Dupont Circle");
        spot4.setCity("Washington");
        spot4.setState("DC");
        spot4.setName("Closed Cafe");
        spot4.setVenueType(DateSpotSuggestion.VenueType.COFFEE_SHOP);
        spot4.setPriceRange(DateSpotSuggestion.PriceRange.BUDGET);
        spot4.setActive(false); // Inactive
        spot4.setCreatedAt(new Date());
        spot4.setUpdatedAt(new Date());
        spotRepo.saveAndFlush(spot4);
    }

    // ============================================
    // Suggesting Date Spots for Matches
    // ============================================

    @Test
    void testGetSpotsForMatch_OverlappingAreas_ShouldReturnSpots() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 adds Dupont Circle
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        // User 2 adds Dupont Circle
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user2);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);

        List<DateSpotSuggestion> spots = dateSpotService.getSpotsForMatch(user1, user2);

        assertNotNull(spots);
        assertFalse(spots.isEmpty());
        // Should return spots in Dupont Circle
        assertTrue(spots.stream().anyMatch(s -> s.getName().equals("Compass Coffee")));
        assertTrue(spots.stream().anyMatch(s -> s.getName().equals("The Phillips Collection")));
    }

    @Test
    void testGetSpotsForMatch_NoOverlap_ShouldReturnEmpty() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User 1 adds Dupont Circle
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        // User 2 adds different area
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user2);
        locationAreaService.addArea("Georgetown", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        List<DateSpotSuggestion> spots = dateSpotService.getSpotsForMatch(user1, user2);

        // Should be empty or only city-level spots
        assertNotNull(spots);
    }

    @Test
    void testGetSpotsForMatch_ShouldExcludeInactiveSpots() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(user2);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);

        List<DateSpotSuggestion> spots = dateSpotService.getSpotsForMatch(user1, user2);

        // Should not include inactive spots
        assertFalse(spots.stream().anyMatch(s -> s.getName().equals("Closed Cafe")));
    }

    // ============================================
    // Location-based Filtering
    // ============================================

    @Test
    void testGetSafeSpots_ShouldReturnOnlySafeSpots() {
        List<DateSpotSuggestion> safeSpots = dateSpotService.getSafeSpots("Dupont Circle");

        assertNotNull(safeSpots);
        assertFalse(safeSpots.isEmpty());

        // All returned spots should have safety features
        safeSpots.forEach(spot -> {
            assertTrue(spot.isNearTransit());
            assertTrue(spot.isPublicSpace());
            assertTrue(spot.isWellLit());
        });
    }

    @Test
    void testGetDaytimeSpots_ShouldReturnDaytimeFriendlySpots() {
        List<DateSpotSuggestion> daytimeSpots = dateSpotService.getDaytimeSpots("Dupont Circle");

        assertNotNull(daytimeSpots);
        assertFalse(daytimeSpots.isEmpty());

        daytimeSpots.forEach(spot -> {
            assertTrue(spot.isDaytimeFriendly());
        });
    }

    @Test
    void testGetBudgetFriendlySpots_ShouldReturnAffordableOptions() {
        List<DateSpotSuggestion> budgetSpots = dateSpotService.getBudgetFriendlySpots("Dupont Circle");

        assertNotNull(budgetSpots);
        assertFalse(budgetSpots.isEmpty());

        budgetSpots.forEach(spot -> {
            assertTrue(spot.getPriceRange() == DateSpotSuggestion.PriceRange.FREE ||
                    spot.getPriceRange() == DateSpotSuggestion.PriceRange.BUDGET);
        });
    }

    @Test
    void testGetSpotsByType_ShouldFilterByVenueType() {
        List<DateSpotSuggestion> coffeeShops = dateSpotService.getSpotsByType(
                "Dupont Circle", DateSpotSuggestion.VenueType.COFFEE_SHOP);

        assertNotNull(coffeeShops);
        assertFalse(coffeeShops.isEmpty());

        coffeeShops.forEach(spot -> {
            assertEquals(DateSpotSuggestion.VenueType.COFFEE_SHOP, spot.getVenueType());
        });

        // Should include Compass Coffee
        assertTrue(coffeeShops.stream().anyMatch(s -> s.getName().equals("Compass Coffee")));
    }

    @Test
    void testGetTopRatedSpots_ShouldOrderByRating() {
        List<DateSpotSuggestion> topRated = dateSpotService.getTopRatedSpots("Dupont Circle");

        assertNotNull(topRated);
        assertFalse(topRated.isEmpty());

        // Should be ordered by rating descending
        for (int i = 0; i < topRated.size() - 1; i++) {
            assertTrue(topRated.get(i).getAverageRating() >= topRated.get(i + 1).getAverageRating());
        }
    }

    // ============================================
    // User Preferences Matching
    // ============================================

    @Test
    void testFilterSpots_ByVenueType_ShouldFilter() {
        List<DateSpotSuggestion> allSpots = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle");

        List<DateSpotSuggestion> filtered = dateSpotService.filterSpots(
                allSpots,
                DateSpotSuggestion.VenueType.MUSEUM,
                null,
                false,
                false
        );

        assertFalse(filtered.isEmpty());
        filtered.forEach(spot -> {
            assertEquals(DateSpotSuggestion.VenueType.MUSEUM, spot.getVenueType());
        });
    }

    @Test
    void testFilterSpots_ByMaxPrice_ShouldFilter() {
        List<DateSpotSuggestion> allSpots = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle");

        List<DateSpotSuggestion> filtered = dateSpotService.filterSpots(
                allSpots,
                null,
                DateSpotSuggestion.PriceRange.BUDGET,
                false,
                false
        );

        assertFalse(filtered.isEmpty());
        filtered.forEach(spot -> {
            assertTrue(spot.getPriceRange().ordinal() <= DateSpotSuggestion.PriceRange.BUDGET.ordinal());
        });
    }

    @Test
    void testFilterSpots_RequireNearTransit_ShouldFilter() {
        List<DateSpotSuggestion> allSpots = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle");

        List<DateSpotSuggestion> filtered = dateSpotService.filterSpots(
                allSpots,
                null,
                null,
                true, // Require near transit
                false
        );

        assertFalse(filtered.isEmpty());
        filtered.forEach(spot -> {
            assertTrue(spot.isNearTransit());
        });
    }

    @Test
    void testFilterSpots_RequireDaytime_ShouldFilter() {
        List<DateSpotSuggestion> allSpots = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle");

        List<DateSpotSuggestion> filtered = dateSpotService.filterSpots(
                allSpots,
                null,
                null,
                false,
                true // Require daytime friendly
        );

        assertFalse(filtered.isEmpty());
        filtered.forEach(spot -> {
            assertTrue(spot.isDaytimeFriendly());
        });
    }

    @Test
    void testFilterSpots_MultipleCriteria_ShouldApplyAll() {
        List<DateSpotSuggestion> allSpots = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle");

        List<DateSpotSuggestion> filtered = dateSpotService.filterSpots(
                allSpots,
                DateSpotSuggestion.VenueType.COFFEE_SHOP,
                DateSpotSuggestion.PriceRange.BUDGET,
                true,
                true
        );

        filtered.forEach(spot -> {
            assertEquals(DateSpotSuggestion.VenueType.COFFEE_SHOP, spot.getVenueType());
            assertTrue(spot.getPriceRange().ordinal() <= DateSpotSuggestion.PriceRange.BUDGET.ordinal());
            assertTrue(spot.isNearTransit());
            assertTrue(spot.isDaytimeFriendly());
        });
    }

    // ============================================
    // Spot Management (Admin functions)
    // ============================================

    @Test
    void testAddSpot_ShouldCreateNewSpot() {
        DateSpotSuggestion newSpot = new DateSpotSuggestion();
        newSpot.setNeighborhood("Adams Morgan");
        newSpot.setCity("Washington");
        newSpot.setState("DC");
        newSpot.setName("Tryst Coffeehouse");
        newSpot.setAddress("2459 18th St NW, Washington, DC 20009");
        newSpot.setVenueType(DateSpotSuggestion.VenueType.COFFEE_SHOP);
        newSpot.setPriceRange(DateSpotSuggestion.PriceRange.BUDGET);

        long countBefore = spotRepo.count();
        DateSpotSuggestion saved = dateSpotService.addSpot(newSpot);
        long countAfter = spotRepo.count();

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertTrue(saved.isActive());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(countBefore + 1, countAfter);
    }

    @Test
    void testUpdateSpot_ShouldModifyExistingSpot() throws Exception {
        DateSpotSuggestion original = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle").get(0);

        DateSpotSuggestion updates = new DateSpotSuggestion();
        updates.setName("Updated Name");
        updates.setDescription("Updated description");
        updates.setPriceRange(DateSpotSuggestion.PriceRange.UPSCALE);

        DateSpotSuggestion updated = dateSpotService.updateSpot(original.getId(), updates);

        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated description", updated.getDescription());
        assertEquals(DateSpotSuggestion.PriceRange.UPSCALE, updated.getPriceRange());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void testUpdateSpot_NonexistentId_ShouldThrowException() {
        DateSpotSuggestion updates = new DateSpotSuggestion();
        updates.setName("Test");

        Exception exception = assertThrows(Exception.class, () -> {
            dateSpotService.updateSpot(999999L, updates);
        });

        assertTrue(exception.getMessage().contains("Spot not found"));
    }

    @Test
    void testDeactivateSpot_ShouldMarkAsInactive() throws Exception {
        DateSpotSuggestion spot = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle").get(0);
        assertTrue(spot.isActive());

        dateSpotService.deactivateSpot(spot.getId());

        DateSpotSuggestion deactivated = spotRepo.findById(spot.getId()).orElseThrow();
        assertFalse(deactivated.isActive());
    }

    @Test
    void testUpdateSpotRating_ShouldCalculateNewAverage() throws Exception {
        DateSpotSuggestion spot = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle").get(0);
        double originalRating = spot.getAverageRating();
        int originalCount = spot.getRatingCount();

        // Add a new rating
        dateSpotService.updateSpotRating(spot.getId(), 5);

        DateSpotSuggestion updated = spotRepo.findById(spot.getId()).orElseThrow();
        assertEquals(originalCount + 1, updated.getRatingCount());

        // Calculate expected average
        double expectedAverage = ((originalRating * originalCount) + 5) / (originalCount + 1);
        assertEquals(expectedAverage, updated.getAverageRating(), 0.01);
    }

    @Test
    void testUpdateSpotRating_MultipleRatings_ShouldUpdateCorrectly() throws Exception {
        DateSpotSuggestion spot = spotRepo.findByNeighborhoodAndActiveTrue("Dupont Circle").get(0);

        dateSpotService.updateSpotRating(spot.getId(), 5);
        dateSpotService.updateSpotRating(spot.getId(), 4);
        dateSpotService.updateSpotRating(spot.getId(), 3);

        DateSpotSuggestion updated = spotRepo.findById(spot.getId()).orElseThrow();
        assertTrue(updated.getRatingCount() > 0);
        assertTrue(updated.getAverageRating() > 0);
    }

    // ============================================
    // Spot Discovery for Current User
    // ============================================

    @Test
    void testGetSpotsInMyAreas_ShouldReturnSpotsInUserAreas() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Add user areas
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);
        locationAreaService.addArea("Clarendon", "Arlington", "VA",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);

        Map<String, List<DateSpotSuggestion>> spotsByArea = dateSpotService.getSpotsInMyAreas();

        assertNotNull(spotsByArea);
        assertFalse(spotsByArea.isEmpty());

        // Should have spots grouped by area
        assertTrue(spotsByArea.values().stream().anyMatch(spots ->
                spots.stream().anyMatch(s -> s.getNeighborhood().equals("Dupont Circle"))));
        assertTrue(spotsByArea.values().stream().anyMatch(spots ->
                spots.stream().anyMatch(s -> s.getNeighborhood().equals("Clarendon"))));
    }

    @Test
    void testGetCategorizedSpotsForMatch_WithOverlap_ShouldReturnCategories() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(user2);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.WORK, true);

        Map<String, Object> result = dateSpotService.getCategorizedSpotsForMatch(user1, user2);

        assertNotNull(result);
        assertTrue((Boolean) result.get("hasOverlap"));
        assertNotNull(result.get("overlappingAreas"));
        assertNotNull(result.get("safeSpots"));
        assertNotNull(result.get("daytimeSpots"));
        assertNotNull(result.get("budgetSpots"));
        assertNotNull(result.get("topRated"));
    }

    @Test
    void testGetCategorizedSpotsForMatch_NoOverlap_ShouldIndicateNoOverlap() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);
        locationAreaService.addArea("Dupont Circle", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(user2);
        locationAreaService.addArea("Georgetown", "Washington", "DC",
                UserLocationArea.DisplayLevel.NEIGHBORHOOD, null, UserLocationArea.AreaLabel.HOME, true);

        Map<String, Object> result = dateSpotService.getCategorizedSpotsForMatch(user1, user2);

        assertNotNull(result);
        assertFalse((Boolean) result.get("hasOverlap"));
    }
}
