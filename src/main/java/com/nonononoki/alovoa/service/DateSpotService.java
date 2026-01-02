package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.DateSpotSuggestion;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.DateSpotSuggestionRepository;
import com.nonononoki.alovoa.repo.UserLocationAreaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing date spot suggestions.
 * Provides safe, public first-date locations for matched users.
 * All spots are curated for safety: well-lit, public, easy exits, near transit.
 */
@Service
public class DateSpotService {

    @Autowired
    private DateSpotSuggestionRepository spotRepo;

    @Autowired
    private UserLocationAreaRepository areaRepo;

    @Autowired
    private AuthService authService;

    // ============================================
    // Finding Date Spots for Matches
    // ============================================

    /**
     * Get date spot suggestions for two matched users based on overlapping areas.
     * Returns spots in areas where both users have declared presence.
     */
    public List<DateSpotSuggestion> getSpotsForMatch(User userA, User userB) {
        // Find overlapping cities/neighborhoods
        List<String> overlappingCities = areaRepo.findOverlappingCities(userA.getId(), userB.getId());

        if (overlappingCities.isEmpty()) {
            return Collections.emptyList();
        }

        // Get spots from overlapping areas, prioritizing safety
        Set<DateSpotSuggestion> spots = new LinkedHashSet<>();

        for (String city : overlappingCities) {
            // First add safe spots (near transit, well-lit, public)
            List<UserLocationArea> areasInCity = areaRepo.findByUserOrderByDisplayOrderAsc(userA)
                    .stream()
                    .filter(a -> city.equals(a.getCity()))
                    .collect(Collectors.toList());

            for (UserLocationArea area : areasInCity) {
                if (area.getNeighborhood() != null) {
                    spots.addAll(spotRepo.findSafeSpots(area.getNeighborhood()));
                }
            }
        }

        // Then add general spots from those cities
        for (String city : overlappingCities) {
            List<UserLocationArea> areasInCity = areaRepo.findByUserOrderByDisplayOrderAsc(userA)
                    .stream()
                    .filter(a -> city.equals(a.getCity()))
                    .collect(Collectors.toList());

            for (UserLocationArea area : areasInCity) {
                spots.addAll(spotRepo.findByCityAndStateAndActiveTrue(area.getCity(), area.getState()));
            }
        }

        return new ArrayList<>(spots);
    }

    /**
     * Get safe date spots for a specific neighborhood (prioritizes safety features).
     */
    public List<DateSpotSuggestion> getSafeSpots(String neighborhood) {
        return spotRepo.findSafeSpots(neighborhood);
    }

    /**
     * Get daytime-friendly spots (for users who prefer meeting during the day).
     */
    public List<DateSpotSuggestion> getDaytimeSpots(String neighborhood) {
        return spotRepo.findDaytimeSpots(neighborhood);
    }

    /**
     * Get budget-friendly spots.
     */
    public List<DateSpotSuggestion> getBudgetFriendlySpots(String neighborhood) {
        return spotRepo.findBudgetFriendly(neighborhood);
    }

    /**
     * Get spots by venue type.
     */
    public List<DateSpotSuggestion> getSpotsByType(String neighborhood, DateSpotSuggestion.VenueType venueType) {
        return spotRepo.findByNeighborhoodAndVenueTypeAndActiveTrue(neighborhood, venueType);
    }

    /**
     * Get top-rated spots in a neighborhood.
     */
    public List<DateSpotSuggestion> getTopRatedSpots(String neighborhood) {
        return spotRepo.findTopRatedInNeighborhood(neighborhood);
    }

    // ============================================
    // Spot Management (Admin functions)
    // ============================================

    /**
     * Add a new date spot suggestion.
     */
    @Transactional
    public DateSpotSuggestion addSpot(DateSpotSuggestion spot) {
        spot.setActive(true);
        spot.setCreatedAt(new Date());
        spot.setUpdatedAt(new Date());
        return spotRepo.save(spot);
    }

    /**
     * Update an existing spot.
     */
    @Transactional
    public DateSpotSuggestion updateSpot(Long spotId, DateSpotSuggestion updates) throws Exception {
        DateSpotSuggestion spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new Exception("Spot not found"));

        if (updates.getName() != null) spot.setName(updates.getName());
        if (updates.getAddress() != null) spot.setAddress(updates.getAddress());
        if (updates.getDescription() != null) spot.setDescription(updates.getDescription());
        if (updates.getVenueType() != null) spot.setVenueType(updates.getVenueType());
        if (updates.getPriceRange() != null) spot.setPriceRange(updates.getPriceRange());
        if (updates.getNearestTransit() != null) spot.setNearestTransit(updates.getNearestTransit());

        spot.setPublicSpace(updates.isPublicSpace());
        spot.setWellLit(updates.isWellLit());
        spot.setNearTransit(updates.isNearTransit());
        spot.setEasyExit(updates.isEasyExit());
        spot.setDaytimeFriendly(updates.isDaytimeFriendly());
        spot.setWalkMinutesFromTransit(updates.getWalkMinutesFromTransit());
        spot.setUpdatedAt(new Date());

        return spotRepo.save(spot);
    }

    /**
     * Deactivate a spot (soft delete).
     */
    @Transactional
    public void deactivateSpot(Long spotId) throws Exception {
        DateSpotSuggestion spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new Exception("Spot not found"));
        spot.setActive(false);
        spot.setUpdatedAt(new Date());
        spotRepo.save(spot);
    }

    /**
     * Update spot rating (called after users rate their date experience).
     */
    @Transactional
    public void updateSpotRating(Long spotId, int rating) throws Exception {
        DateSpotSuggestion spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new Exception("Spot not found"));

        int newCount = spot.getRatingCount() + 1;
        double newAverage = ((spot.getAverageRating() * spot.getRatingCount()) + rating) / newCount;

        spot.setAverageRating(newAverage);
        spot.setRatingCount(newCount);
        spot.setUpdatedAt(new Date());

        spotRepo.save(spot);
    }

    // ============================================
    // Spot Discovery for Current User
    // ============================================

    /**
     * Get spots in all of the current user's declared areas.
     */
    public Map<String, List<DateSpotSuggestion>> getSpotsInMyAreas() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        List<UserLocationArea> myAreas = areaRepo.findByUserOrderByDisplayOrderAsc(user);

        Map<String, List<DateSpotSuggestion>> spotsByArea = new LinkedHashMap<>();

        for (UserLocationArea area : myAreas) {
            String areaKey = area.getFormattedDisplay();
            if (areaKey == null) {
                areaKey = area.getCity() + ", " + area.getState();
            }

            List<DateSpotSuggestion> spots;
            if (area.getNeighborhood() != null) {
                spots = spotRepo.findByNeighborhoodAndActiveTrue(area.getNeighborhood());
            } else {
                spots = spotRepo.findByCityAndStateAndActiveTrue(area.getCity(), area.getState());
            }

            if (!spots.isEmpty()) {
                spotsByArea.put(areaKey, spots);
            }
        }

        return spotsByArea;
    }

    /**
     * Get a categorized view of spots for a specific match.
     */
    public Map<String, Object> getCategorizedSpotsForMatch(User userA, User userB) {
        Map<String, Object> result = new HashMap<>();

        List<String> overlappingCities = areaRepo.findOverlappingCities(userA.getId(), userB.getId());
        result.put("overlappingAreas", overlappingCities);

        if (overlappingCities.isEmpty()) {
            result.put("hasOverlap", false);
            return result;
        }

        result.put("hasOverlap", true);

        // Get spots from the first overlapping city/neighborhood
        List<UserLocationArea> areasInOverlap = areaRepo.findByUserOrderByDisplayOrderAsc(userA)
                .stream()
                .filter(a -> overlappingCities.contains(a.getCity()))
                .collect(Collectors.toList());

        if (!areasInOverlap.isEmpty()) {
            UserLocationArea primaryOverlap = areasInOverlap.get(0);
            String neighborhood = primaryOverlap.getNeighborhood();

            if (neighborhood != null) {
                result.put("safeSpots", spotRepo.findSafeSpots(neighborhood));
                result.put("daytimeSpots", spotRepo.findDaytimeSpots(neighborhood));
                result.put("budgetSpots", spotRepo.findBudgetFriendly(neighborhood));
                result.put("topRated", spotRepo.findTopRatedInNeighborhood(neighborhood));
            } else {
                // City-level spots
                List<DateSpotSuggestion> citySpots = spotRepo.findByCityAndStateAndActiveTrue(
                        primaryOverlap.getCity(), primaryOverlap.getState());
                result.put("allSpots", citySpots);
            }
        }

        return result;
    }

    // ============================================
    // Filtering helpers
    // ============================================

    /**
     * Filter spots by multiple criteria.
     */
    public List<DateSpotSuggestion> filterSpots(
            List<DateSpotSuggestion> spots,
            DateSpotSuggestion.VenueType venueType,
            DateSpotSuggestion.PriceRange maxPrice,
            boolean requireNearTransit,
            boolean requireDaytime) {

        return spots.stream()
                .filter(s -> venueType == null || s.getVenueType() == venueType)
                .filter(s -> maxPrice == null || s.getPriceRange().ordinal() <= maxPrice.ordinal())
                .filter(s -> !requireNearTransit || s.isNearTransit())
                .filter(s -> !requireDaytime || s.isDaytimeFriendly())
                .collect(Collectors.toList());
    }
}
