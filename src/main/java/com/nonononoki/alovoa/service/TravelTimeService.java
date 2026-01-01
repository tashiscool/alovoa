package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.AreaCentroid;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import com.nonononoki.alovoa.repo.AreaCentroidRepository;
import com.nonononoki.alovoa.repo.UserLocationAreaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for calculating approximate travel times between area centroids.
 *
 * PRIVACY DESIGN:
 * - Uses AREA CENTROIDS, not user locations
 * - Cannot be triangulated because centroids are fixed public points
 * - Travel times are deliberately fuzzy (rounded to 5-min increments)
 * - Shows "~X min from your areas" not "X min from you"
 */
@Service
public class TravelTimeService {

    private static final int TIME_ROUNDING_MINUTES = 5;  // Round to nearest 5 min
    private static final double AVERAGE_DRIVING_SPEED_MPH = 25;  // Urban average
    private static final double EARTH_RADIUS_MILES = 3959;

    @Autowired
    private AreaCentroidRepository centroidRepo;

    @Autowired
    private UserLocationAreaRepository areaRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calculate the minimum travel time between two users' areas.
     * Uses centroid-to-centroid calculation, NOT user locations.
     *
     * @return Approximate travel time in minutes, rounded to 5-min increments
     */
    public int calculateTravelTime(User userA, User userB) {
        List<UserLocationArea> areasA = areaRepo.findByUserOrderByDisplayOrderAsc(userA);
        List<UserLocationArea> areasB = areaRepo.findByUserOrderByDisplayOrderAsc(userB);

        if (areasA.isEmpty() || areasB.isEmpty()) {
            return -1;  // Cannot calculate
        }

        int minTime = Integer.MAX_VALUE;

        for (UserLocationArea areaA : areasA) {
            AreaCentroid centroidA = findCentroid(areaA);
            if (centroidA == null) continue;

            for (UserLocationArea areaB : areasB) {
                AreaCentroid centroidB = findCentroid(areaB);
                if (centroidB == null) continue;

                int time = calculateTimeBetweenCentroids(centroidA, centroidB);
                if (time < minTime) {
                    minTime = time;
                }
            }
        }

        return minTime == Integer.MAX_VALUE ? -1 : minTime;
    }

    /**
     * Get travel time display string.
     * Format: "~X min from your areas"
     */
    public String getTravelTimeDisplay(User currentUser, User otherUser) {
        int minutes = calculateTravelTime(currentUser, otherUser);
        if (minutes < 0) {
            return null;  // Cannot calculate
        }
        return "~" + minutes + " min";
    }

    /**
     * Get travel time bucket for filtering/sorting.
     */
    public TravelTimeBucket getTravelTimeBucket(User currentUser, User otherUser) {
        int minutes = calculateTravelTime(currentUser, otherUser);
        if (minutes < 0) return TravelTimeBucket.UNKNOWN;
        if (minutes <= 15) return TravelTimeBucket.UNDER_15;
        if (minutes <= 30) return TravelTimeBucket.UNDER_30;
        if (minutes <= 45) return TravelTimeBucket.UNDER_45;
        if (minutes <= 60) return TravelTimeBucket.UNDER_60;
        return TravelTimeBucket.OVER_60;
    }

    /**
     * Filter users by maximum travel time.
     */
    public List<User> filterByTravelTime(User currentUser, List<User> candidates, int maxMinutes) {
        return candidates.stream()
                .filter(candidate -> {
                    int time = calculateTravelTime(currentUser, candidate);
                    return time >= 0 && time <= maxMinutes;
                })
                .toList();
    }

    /**
     * Sort users by travel time (closest first).
     */
    public List<User> sortByTravelTime(User currentUser, List<User> candidates) {
        return candidates.stream()
                .sorted((a, b) -> {
                    int timeA = calculateTravelTime(currentUser, a);
                    int timeB = calculateTravelTime(currentUser, b);
                    // Put unknown at the end
                    if (timeA < 0) return 1;
                    if (timeB < 0) return -1;
                    return Integer.compare(timeA, timeB);
                })
                .toList();
    }

    /**
     * Group users by travel time bucket.
     */
    public Map<TravelTimeBucket, List<User>> groupByTravelTime(User currentUser, List<User> candidates) {
        Map<TravelTimeBucket, List<User>> grouped = new EnumMap<>(TravelTimeBucket.class);
        for (TravelTimeBucket bucket : TravelTimeBucket.values()) {
            grouped.put(bucket, new ArrayList<>());
        }

        for (User candidate : candidates) {
            TravelTimeBucket bucket = getTravelTimeBucket(currentUser, candidate);
            grouped.get(bucket).add(candidate);
        }

        return grouped;
    }

    /**
     * Get detailed travel time info for a match.
     */
    public TravelTimeInfo getTravelTimeInfo(User currentUser, User otherUser) {
        int minutes = calculateTravelTime(currentUser, otherUser);
        List<String> overlappingAreas = areaRepo.findOverlappingCities(currentUser.getId(), otherUser.getId());

        TravelTimeInfo info = new TravelTimeInfo();
        info.setMinutes(minutes);
        info.setDisplay(minutes >= 0 ? "~" + minutes + " min from your areas" : null);
        info.setBucket(getTravelTimeBucket(currentUser, otherUser));
        info.setHasOverlappingAreas(!overlappingAreas.isEmpty());
        info.setOverlappingAreas(overlappingAreas);

        return info;
    }

    // ============================================
    // Private Helpers
    // ============================================

    private AreaCentroid findCentroid(UserLocationArea area) {
        // Try to find neighborhood-level centroid first
        List<AreaCentroid> matches = centroidRepo.findBestMatchCentroid(
                area.getNeighborhood(), area.getCity(), area.getState());

        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        // Fall back to city-level
        return centroidRepo.findByCityAndState(area.getCity(), area.getState()).orElse(null);
    }

    private int calculateTimeBetweenCentroids(AreaCentroid a, AreaCentroid b) {
        // Check if we have cached travel time
        if (a.getTravelTimeCache() != null) {
            try {
                Map<String, Integer> cache = objectMapper.readValue(
                        a.getTravelTimeCache(), new TypeReference<>() {});
                Integer cached = cache.get(b.getAreaKey());
                if (cached != null) {
                    return cached;
                }
            } catch (Exception e) {
                // Fall through to calculation
            }
        }

        // Calculate using Haversine formula + estimated drive time
        if (a.getCentroidLat() == null || a.getCentroidLng() == null ||
            b.getCentroidLat() == null || b.getCentroidLng() == null) {
            return -1;
        }

        double distance = haversineDistance(
                a.getCentroidLat(), a.getCentroidLng(),
                b.getCentroidLat(), b.getCentroidLng());

        // Convert to time and round to nearest 5 minutes
        double rawMinutes = (distance / AVERAGE_DRIVING_SPEED_MPH) * 60;

        // Add urban penalty (traffic, lights, etc.) - roughly 40% overhead
        rawMinutes *= 1.4;

        // Round to nearest 5 minutes
        int roundedMinutes = (int) Math.round(rawMinutes / TIME_ROUNDING_MINUTES) * TIME_ROUNDING_MINUTES;

        // Minimum 5 minutes
        return Math.max(5, roundedMinutes);
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                   Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));

        return EARTH_RADIUS_MILES * c;
    }

    // ============================================
    // Enums and DTOs
    // ============================================

    public enum TravelTimeBucket {
        UNDER_15("Under 15 min", 0, 15),
        UNDER_30("15-30 min", 15, 30),
        UNDER_45("30-45 min", 30, 45),
        UNDER_60("45-60 min", 45, 60),
        OVER_60("Over 60 min", 60, Integer.MAX_VALUE),
        UNKNOWN("Unknown", -1, -1);

        private final String displayName;
        private final int minMinutes;
        private final int maxMinutes;

        TravelTimeBucket(String displayName, int minMinutes, int maxMinutes) {
            this.displayName = displayName;
            this.minMinutes = minMinutes;
            this.maxMinutes = maxMinutes;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getMinMinutes() {
            return minMinutes;
        }

        public int getMaxMinutes() {
            return maxMinutes;
        }
    }

    @lombok.Data
    public static class TravelTimeInfo {
        private int minutes;
        private String display;
        private TravelTimeBucket bucket;
        private boolean hasOverlappingAreas;
        private List<String> overlappingAreas;
    }
}
