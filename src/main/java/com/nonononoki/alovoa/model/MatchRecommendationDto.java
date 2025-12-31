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
    private List<String> conversationStarters;
    private List<String> sharedInterests;
    private String compatibilitySummary;
}
