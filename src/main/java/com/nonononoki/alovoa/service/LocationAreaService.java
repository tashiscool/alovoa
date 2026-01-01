package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.entity.user.UserLocationPreferences;
import com.nonononoki.alovoa.entity.user.UserTravelingMode;
import com.nonononoki.alovoa.repo.UserLocationAreaRepository;
import com.nonononoki.alovoa.repo.UserLocationPreferencesRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.UserTravelingModeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for privacy-safe location management.
 * Users declare up to 3 areas where they're open to meeting people.
 * NO GPS tracking - only user-selected areas.
 */
@Service
public class LocationAreaService {

    private static final int MAX_AREAS = 3;

    @Autowired
    private UserLocationAreaRepository areaRepo;

    @Autowired
    private UserLocationPreferencesRepository prefsRepo;

    @Autowired
    private UserTravelingModeRepository travelingRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthService authService;

    // ============================================
    // Area Management
    // ============================================

    /**
     * Get all location areas for current user.
     */
    public List<UserLocationArea> getMyAreas() {
        User user = authService.getCurrentUser(true);
        return areaRepo.findByUserOrderByDisplayOrderAsc(user);
    }

    /**
     * Add a new location area.
     */
    @Transactional
    public UserLocationArea addArea(
            String neighborhood,
            String city,
            String state,
            UserLocationArea.DisplayLevel displayLevel,
            String displayAs,
            UserLocationArea.AreaLabel label,
            boolean visibleOnProfile) throws Exception {

        User user = authService.getCurrentUser(true);

        // Check max areas
        long currentCount = areaRepo.countByUser(user);
        if (currentCount >= MAX_AREAS) {
            throw new Exception("Maximum " + MAX_AREAS + " areas allowed");
        }

        // Determine area type
        UserLocationArea.AreaType areaType;
        if (currentCount == 0) {
            areaType = UserLocationArea.AreaType.PRIMARY;
        } else if (currentCount == 1) {
            areaType = UserLocationArea.AreaType.SECONDARY;
        } else {
            areaType = UserLocationArea.AreaType.TERTIARY;
        }

        UserLocationArea area = new UserLocationArea();
        area.setUser(user);
        area.setAreaType(areaType);
        area.setNeighborhood(neighborhood);
        area.setCity(city);
        area.setState(state);
        area.setCountry("US");  // Default to US for now
        area.setDisplayLevel(displayLevel);
        area.setDisplayAs(displayAs != null ? displayAs : generateDisplayAs(city, state, displayLevel));
        area.setLabel(label);
        area.setVisibleOnProfile(visibleOnProfile);
        area.setDisplayOrder((int) currentCount);

        return areaRepo.save(area);
    }

    /**
     * Update an existing area.
     */
    @Transactional
    public UserLocationArea updateArea(
            Long areaId,
            UserLocationArea.DisplayLevel displayLevel,
            String displayAs,
            UserLocationArea.AreaLabel label,
            boolean visibleOnProfile) throws Exception {

        User user = authService.getCurrentUser(true);
        UserLocationArea area = areaRepo.findById(areaId)
                .orElseThrow(() -> new Exception("Area not found"));

        if (!area.getUser().getId().equals(user.getId())) {
            throw new Exception("Not authorized to update this area");
        }

        area.setDisplayLevel(displayLevel);
        area.setDisplayAs(displayAs);
        area.setLabel(label);
        area.setVisibleOnProfile(visibleOnProfile);

        return areaRepo.save(area);
    }

    /**
     * Remove an area (cannot remove primary if it's the only one).
     */
    @Transactional
    public void removeArea(Long areaId) throws Exception {
        User user = authService.getCurrentUser(true);
        UserLocationArea area = areaRepo.findById(areaId)
                .orElseThrow(() -> new Exception("Area not found"));

        if (!area.getUser().getId().equals(user.getId())) {
            throw new Exception("Not authorized to remove this area");
        }

        // Can't remove primary if it's the only area
        if (area.getAreaType() == UserLocationArea.AreaType.PRIMARY) {
            long count = areaRepo.countByUser(user);
            if (count == 1) {
                throw new Exception("Cannot remove primary area. At least one area is required.");
            }
            // Promote secondary to primary
            areaRepo.findByUserAndAreaType(user, UserLocationArea.AreaType.SECONDARY)
                    .ifPresent(secondary -> {
                        secondary.setAreaType(UserLocationArea.AreaType.PRIMARY);
                        secondary.setDisplayOrder(0);
                        areaRepo.save(secondary);
                    });
        }

        areaRepo.delete(area);
    }

    // ============================================
    // Location Preferences
    // ============================================

    /**
     * Get location preferences for current user.
     */
    public UserLocationPreferences getMyPreferences() {
        User user = authService.getCurrentUser(true);
        return prefsRepo.findByUser(user)
                .orElseGet(() -> createDefaultPreferences(user));
    }

    /**
     * Update location preferences.
     */
    @Transactional
    public UserLocationPreferences updatePreferences(
            int maxTravelMinutes,
            boolean requireAreaOverlap,
            boolean showExceptionalMatches) {

        User user = authService.getCurrentUser(true);
        UserLocationPreferences prefs = prefsRepo.findByUser(user)
                .orElseGet(() -> createDefaultPreferences(user));

        prefs.setMaxTravelMinutes(maxTravelMinutes);
        prefs.setRequireAreaOverlap(requireAreaOverlap);
        prefs.setShowExceptionalMatches(showExceptionalMatches);

        return prefsRepo.save(prefs);
    }

    /**
     * Set "moving to" location.
     */
    @Transactional
    public UserLocationPreferences setMovingTo(String city, String state, Date movingDate) {
        User user = authService.getCurrentUser(true);
        UserLocationPreferences prefs = prefsRepo.findByUser(user)
                .orElseGet(() -> createDefaultPreferences(user));

        prefs.setMovingToCity(city);
        prefs.setMovingToState(state);
        prefs.setMovingDate(movingDate);

        return prefsRepo.save(prefs);
    }

    // ============================================
    // Traveling Mode
    // ============================================

    /**
     * Enable traveling mode.
     */
    @Transactional
    public UserTravelingMode enableTravelingMode(
            String destinationCity,
            String destinationState,
            Date arrivingDate,
            Date leavingDate,
            boolean showMeThere,
            boolean showLocalsToMe) throws Exception {

        User user = authService.getCurrentUser(true);

        // Validate dates
        if (arrivingDate.after(leavingDate)) {
            throw new Exception("Arrival date must be before leaving date");
        }

        // Remove existing traveling mode if any
        travelingRepo.findByUser(user).ifPresent(travelingRepo::delete);

        UserTravelingMode traveling = new UserTravelingMode();
        traveling.setUser(user);
        traveling.setDestinationCity(destinationCity);
        traveling.setDestinationState(destinationState);
        traveling.setDestinationCountry("US");
        traveling.setDisplayAs(destinationCity + ", " + destinationState);
        traveling.setArrivingDate(arrivingDate);
        traveling.setLeavingDate(leavingDate);
        traveling.setShowMeThere(showMeThere);
        traveling.setShowLocalsToMe(showLocalsToMe);
        traveling.setActive(true);
        traveling.setAutoDisable(true);

        return travelingRepo.save(traveling);
    }

    /**
     * Disable traveling mode.
     */
    @Transactional
    public void disableTravelingMode() {
        User user = authService.getCurrentUser(true);
        travelingRepo.findByUser(user).ifPresent(tm -> {
            tm.setActive(false);
            travelingRepo.save(tm);
        });
    }

    /**
     * Get current traveling mode.
     */
    public Optional<UserTravelingMode> getMyTravelingMode() {
        User user = authService.getCurrentUser(true);
        return travelingRepo.findByUser(user);
    }

    // ============================================
    // Matching Logic
    // ============================================

    /**
     * Check if two users have overlapping areas.
     */
    public boolean hasOverlappingAreas(User userA, User userB) {
        return areaRepo.hasOverlappingAreas(userA.getId(), userB.getId());
    }

    /**
     * Get overlapping area names between two users.
     */
    public List<String> getOverlappingAreas(User userA, User userB) {
        return areaRepo.findOverlappingCities(userA.getId(), userB.getId());
    }

    /**
     * Find potential matches with overlapping areas.
     */
    public List<User> findUsersWithOverlappingAreas(User user) {
        List<UserLocationArea> myAreas = areaRepo.findByUserOrderByDisplayOrderAsc(user);

        Set<User> matchingUsers = new HashSet<>();
        for (UserLocationArea area : myAreas) {
            List<User> usersInArea = areaRepo.findUsersInArea(
                    area.getCity(), area.getState(), user.getId());
            matchingUsers.addAll(usersInArea);
        }

        return new ArrayList<>(matchingUsers);
    }

    /**
     * Get location display for a user's profile.
     */
    public Map<String, Object> getLocationDisplay(User user) {
        Map<String, Object> display = new HashMap<>();

        List<UserLocationArea> areas = areaRepo.findByUserOrderByDisplayOrderAsc(user);
        List<String> visibleAreas = areas.stream()
                .filter(UserLocationArea::isVisibleOnProfile)
                .map(UserLocationArea::getFormattedDisplay)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        display.put("areas", visibleAreas);

        // Check for traveling mode
        travelingRepo.findByUser(user)
                .filter(tm -> tm.isActive() && (tm.isCurrentlyActive() || tm.isUpcoming()))
                .ifPresent(tm -> {
                    display.put("traveling", true);
                    display.put("travelingTo", tm.getDisplayAs());
                    display.put("travelingDates", tm.getArrivingDate() + " - " + tm.getLeavingDate());
                    display.put("travelingUpcoming", tm.isUpcoming());
                });

        // Check for "moving to"
        prefsRepo.findByUser(user)
                .filter(p -> p.getMovingToCity() != null)
                .ifPresent(p -> {
                    display.put("movingTo", p.getMovingDisplay());
                    display.put("movingDate", p.getMovingDate());
                });

        return display;
    }

    // ============================================
    // Scheduled Tasks
    // ============================================

    /**
     * Auto-disable expired traveling modes (runs daily).
     */
    @Scheduled(cron = "0 0 1 * * *")  // 1 AM daily
    @Transactional
    public void disableExpiredTravelingModes() {
        List<UserTravelingMode> expired = travelingRepo.findExpiredTrips(new Date());
        for (UserTravelingMode tm : expired) {
            tm.setActive(false);
            travelingRepo.save(tm);
        }
    }

    // ============================================
    // Helpers
    // ============================================

    private UserLocationPreferences createDefaultPreferences(User user) {
        UserLocationPreferences prefs = new UserLocationPreferences();
        prefs.setUser(user);
        prefs.setMaxTravelMinutes(20);
        prefs.setRequireAreaOverlap(true);
        prefs.setShowExceptionalMatches(true);
        prefs.setExceptionalMatchThreshold(0.90);
        return prefsRepo.save(prefs);
    }

    private String generateDisplayAs(String city, String state, UserLocationArea.DisplayLevel level) {
        return switch (level) {
            case NEIGHBORHOOD -> city + ", " + state;
            case CITY -> city + ", " + state;
            case REGION -> getRegionName(state);
            case METRO -> getMetroName(state);
            case HIDDEN -> null;
        };
    }

    private String getRegionName(String state) {
        return switch (state) {
            case "VA" -> "Northern Virginia";
            case "MD" -> "Maryland";
            case "DC" -> "Washington, DC";
            default -> state;
        };
    }

    private String getMetroName(String state) {
        if ("VA".equals(state) || "MD".equals(state) || "DC".equals(state)) {
            return "DC Metro Area";
        }
        return state + " Metro Area";
    }
}
