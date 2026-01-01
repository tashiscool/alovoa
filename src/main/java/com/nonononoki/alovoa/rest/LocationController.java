package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.DateSpotSuggestion;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.entity.user.UserLocationPreferences;
import com.nonononoki.alovoa.entity.user.UserTravelingMode;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.DateSpotService;
import com.nonononoki.alovoa.service.LocationAreaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for privacy-safe location management.
 * NO GPS tracking - all locations are user-declared areas.
 */
@RestController
@RequestMapping("/location")
public class LocationController {

    @Autowired
    private LocationAreaService locationService;

    @Autowired
    private DateSpotService dateSpotService;

    @Autowired
    private UserRepository userRepo;

    // ============================================
    // Area Management
    // ============================================

    /**
     * Get all location areas for current user.
     */
    @GetMapping("/areas")
    public ResponseEntity<List<UserLocationArea>> getMyAreas() {
        return ResponseEntity.ok(locationService.getMyAreas());
    }

    /**
     * Add a new location area.
     */
    @PostMapping("/areas")
    public ResponseEntity<?> addArea(@RequestBody AreaRequest request) {
        try {
            UserLocationArea area = locationService.addArea(
                    request.neighborhood,
                    request.city,
                    request.state,
                    request.displayLevel,
                    request.displayAs,
                    request.label,
                    request.visibleOnProfile
            );
            return ResponseEntity.ok(area);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update an existing area.
     */
    @PutMapping("/areas/{areaId}")
    public ResponseEntity<?> updateArea(
            @PathVariable Long areaId,
            @RequestBody AreaUpdateRequest request) {
        try {
            UserLocationArea area = locationService.updateArea(
                    areaId,
                    request.displayLevel,
                    request.displayAs,
                    request.label,
                    request.visibleOnProfile
            );
            return ResponseEntity.ok(area);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove an area.
     */
    @DeleteMapping("/areas/{areaId}")
    public ResponseEntity<?> removeArea(@PathVariable Long areaId) {
        try {
            locationService.removeArea(areaId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================
    // Location Preferences
    // ============================================

    /**
     * Get location preferences for current user.
     */
    @GetMapping("/preferences")
    public ResponseEntity<UserLocationPreferences> getPreferences() {
        return ResponseEntity.ok(locationService.getMyPreferences());
    }

    /**
     * Update location preferences.
     */
    @PutMapping("/preferences")
    public ResponseEntity<UserLocationPreferences> updatePreferences(@RequestBody PreferencesRequest request) {
        UserLocationPreferences prefs = locationService.updatePreferences(
                request.maxTravelMinutes,
                request.requireAreaOverlap,
                request.showExceptionalMatches
        );
        return ResponseEntity.ok(prefs);
    }

    /**
     * Set "moving to" location.
     */
    @PostMapping("/moving-to")
    public ResponseEntity<UserLocationPreferences> setMovingTo(@RequestBody MovingToRequest request) {
        UserLocationPreferences prefs = locationService.setMovingTo(
                request.city,
                request.state,
                request.movingDate
        );
        return ResponseEntity.ok(prefs);
    }

    /**
     * Clear "moving to" location.
     */
    @DeleteMapping("/moving-to")
    public ResponseEntity<UserLocationPreferences> clearMovingTo() {
        UserLocationPreferences prefs = locationService.setMovingTo(null, null, null);
        return ResponseEntity.ok(prefs);
    }

    // ============================================
    // Traveling Mode
    // ============================================

    /**
     * Get current traveling mode status.
     */
    @GetMapping("/traveling")
    public ResponseEntity<?> getTravelingMode() {
        Optional<UserTravelingMode> traveling = locationService.getMyTravelingMode();
        if (traveling.isPresent()) {
            return ResponseEntity.ok(traveling.get());
        }
        return ResponseEntity.ok(Map.of("active", false));
    }

    /**
     * Enable traveling mode.
     */
    @PostMapping("/traveling")
    public ResponseEntity<?> enableTravelingMode(@RequestBody TravelingRequest request) {
        try {
            UserTravelingMode traveling = locationService.enableTravelingMode(
                    request.destinationCity,
                    request.destinationState,
                    request.arrivingDate,
                    request.leavingDate,
                    request.showMeThere,
                    request.showLocalsToMe
            );
            return ResponseEntity.ok(traveling);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disable traveling mode.
     */
    @DeleteMapping("/traveling")
    public ResponseEntity<?> disableTravelingMode() {
        locationService.disableTravelingMode();
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ============================================
    // Date Spot Suggestions
    // ============================================

    /**
     * Get date spots in all of user's areas.
     */
    @GetMapping("/date-spots")
    public ResponseEntity<Map<String, List<DateSpotSuggestion>>> getDateSpots() {
        return ResponseEntity.ok(dateSpotService.getSpotsInMyAreas());
    }

    /**
     * Get date spot suggestions for a specific match.
     */
    @GetMapping("/date-spots/match/{userId}")
    public ResponseEntity<?> getDateSpotsForMatch(@PathVariable Long userId) throws AlovoaException {
        User otherUser = userRepo.findById(userId)
                .orElseThrow(() -> new AlovoaException("User not found"));

        // Note: Actual current user would come from auth service
        // For now, we return the categorized spots
        List<DateSpotSuggestion> spots = dateSpotService.getSpotsForMatch(otherUser, otherUser);
        return ResponseEntity.ok(spots);
    }

    /**
     * Get safe spots in a neighborhood (near transit, well-lit, public).
     */
    @GetMapping("/date-spots/safe")
    public ResponseEntity<List<DateSpotSuggestion>> getSafeSpots(
            @RequestParam String neighborhood) {
        return ResponseEntity.ok(dateSpotService.getSafeSpots(neighborhood));
    }

    /**
     * Get daytime-friendly spots.
     */
    @GetMapping("/date-spots/daytime")
    public ResponseEntity<List<DateSpotSuggestion>> getDaytimeSpots(
            @RequestParam String neighborhood) {
        return ResponseEntity.ok(dateSpotService.getDaytimeSpots(neighborhood));
    }

    /**
     * Get budget-friendly spots.
     */
    @GetMapping("/date-spots/budget")
    public ResponseEntity<List<DateSpotSuggestion>> getBudgetSpots(
            @RequestParam String neighborhood) {
        return ResponseEntity.ok(dateSpotService.getBudgetFriendlySpots(neighborhood));
    }

    /**
     * Get spots by venue type.
     */
    @GetMapping("/date-spots/type/{venueType}")
    public ResponseEntity<List<DateSpotSuggestion>> getSpotsByType(
            @PathVariable DateSpotSuggestion.VenueType venueType,
            @RequestParam String neighborhood) {
        return ResponseEntity.ok(dateSpotService.getSpotsByType(neighborhood, venueType));
    }

    // ============================================
    // Location Display for Profile
    // ============================================

    /**
     * Get location display info for a user's profile.
     */
    @GetMapping("/display/{userId}")
    public ResponseEntity<?> getLocationDisplay(@PathVariable Long userId) throws AlovoaException {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AlovoaException("User not found"));

        return ResponseEntity.ok(locationService.getLocationDisplay(user));
    }

    // ============================================
    // Match Location Helpers
    // ============================================

    /**
     * Check if current user has overlapping areas with another user.
     */
    @GetMapping("/overlap/{userId}")
    public ResponseEntity<?> checkOverlap(@PathVariable Long userId) throws AlovoaException {
        User otherUser = userRepo.findById(userId)
                .orElseThrow(() -> new AlovoaException("User not found"));

        // Get current user from auth service
        List<UserLocationArea> myAreas = locationService.getMyAreas();
        if (myAreas.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "hasOverlap", false,
                    "reason", "You haven't set up your location areas yet"
            ));
        }

        User currentUser = myAreas.get(0).getUser();
        boolean hasOverlap = locationService.hasOverlappingAreas(currentUser, otherUser);
        List<String> overlappingAreas = locationService.getOverlappingAreas(currentUser, otherUser);

        return ResponseEntity.ok(Map.of(
                "hasOverlap", hasOverlap,
                "overlappingAreas", overlappingAreas
        ));
    }

    // ============================================
    // Request DTOs
    // ============================================

    public static class AreaRequest {
        public String neighborhood;
        public String city;
        public String state;
        public UserLocationArea.DisplayLevel displayLevel;
        public String displayAs;
        public UserLocationArea.AreaLabel label;
        public boolean visibleOnProfile = true;
    }

    public static class AreaUpdateRequest {
        public UserLocationArea.DisplayLevel displayLevel;
        public String displayAs;
        public UserLocationArea.AreaLabel label;
        public boolean visibleOnProfile = true;
    }

    public static class PreferencesRequest {
        public int maxTravelMinutes = 20;
        public boolean requireAreaOverlap = true;
        public boolean showExceptionalMatches = true;
    }

    public static class MovingToRequest {
        public String city;
        public String state;
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        public Date movingDate;
    }

    public static class TravelingRequest {
        public String destinationCity;
        public String destinationState;
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        public Date arrivingDate;
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        public Date leavingDate;
        public boolean showMeThere = true;
        public boolean showLocalsToMe = true;
    }
}
