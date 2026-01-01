package com.nonononoki.alovoa.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MatchRecommendationDto {
    private Long userId;
    private String userUuid;
    private Double compatibilityScore;
    private Double enemyScore;  // Incompatibility percentage (OKCupid 2016 parity)
    private List<String> conversationStarters;
    private List<String> sharedInterests;
    private String compatibilitySummary;

    // Privacy-safe location info (centroid-based, NOT GPS)
    private Integer travelTimeMinutes;      // ~X min from your areas
    private String travelTimeDisplay;        // Display string like "~15 min"
    private boolean hasOverlappingAreas;     // Do users share any declared areas?
    private List<String> overlappingAreas;   // Shared area names (e.g., ["Downtown DC"])
}
