package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.DateSpotSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DateSpotSuggestionRepository extends JpaRepository<DateSpotSuggestion, Long> {

    // Find spots by neighborhood
    List<DateSpotSuggestion> findByNeighborhoodAndActiveTrue(String neighborhood);

    // Find spots by city
    List<DateSpotSuggestion> findByCityAndStateAndActiveTrue(String city, String state);

    // Find top rated spots in an area
    @Query("SELECT ds FROM DateSpotSuggestion ds " +
           "WHERE ds.neighborhood = :neighborhood " +
           "AND ds.active = true " +
           "ORDER BY ds.averageRating DESC")
    List<DateSpotSuggestion> findTopRatedInNeighborhood(@Param("neighborhood") String neighborhood);

    // Find spots near transit (safety feature)
    @Query("SELECT ds FROM DateSpotSuggestion ds " +
           "WHERE ds.neighborhood = :neighborhood " +
           "AND ds.active = true " +
           "AND ds.nearTransit = true " +
           "AND ds.publicSpace = true " +
           "AND ds.wellLit = true " +
           "ORDER BY ds.walkMinutesFromTransit ASC")
    List<DateSpotSuggestion> findSafeSpots(@Param("neighborhood") String neighborhood);

    // Find daytime-friendly spots
    @Query("SELECT ds FROM DateSpotSuggestion ds " +
           "WHERE ds.neighborhood = :neighborhood " +
           "AND ds.active = true " +
           "AND ds.daytimeFriendly = true " +
           "ORDER BY ds.averageRating DESC")
    List<DateSpotSuggestion> findDaytimeSpots(@Param("neighborhood") String neighborhood);

    // Find by venue type
    List<DateSpotSuggestion> findByNeighborhoodAndVenueTypeAndActiveTrue(
            String neighborhood, DateSpotSuggestion.VenueType venueType);

    // Find budget-friendly options
    @Query("SELECT ds FROM DateSpotSuggestion ds " +
           "WHERE ds.neighborhood = :neighborhood " +
           "AND ds.active = true " +
           "AND (ds.priceRange = 'FREE' OR ds.priceRange = 'BUDGET') " +
           "ORDER BY ds.averageRating DESC")
    List<DateSpotSuggestion> findBudgetFriendly(@Param("neighborhood") String neighborhood);
}
