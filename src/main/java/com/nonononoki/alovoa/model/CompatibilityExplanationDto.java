package com.nonononoki.alovoa.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO for displaying compatibility explanation to users.
 * Shows why two users are compatible and where they might face challenges.
 */
@Getter
@Setter
public class CompatibilityExplanationDto {

    /**
     * Overall compatibility score (0-100)
     */
    private Double overallScore;

    /**
     * Enemy score - measures fundamental incompatibilities (0-100)
     * Higher = more incompatible (like OKCupid's enemy percentage)
     */
    private Double enemyScore;

    /**
     * List of top compatibility strengths
     * e.g., ["Similar communication styles", "Shared values around family"]
     */
    private List<String> topCompatibilities;

    /**
     * List of potential challenges or areas requiring work
     * e.g., ["Different sleep schedules", "Varying social energy levels"]
     */
    private List<String> potentialChallenges;

    /**
     * Scores for each compatibility dimension (0-100)
     * Keys: "values", "lifestyle", "personality", "attraction", "circumstantial", "growth"
     */
    private Map<String, Double> dimensionScores;

    /**
     * Brief summary of compatibility
     * e.g., "You two have strong alignment in values and personality traits..."
     */
    private String summary;

    /**
     * Detailed explanation from AI service (optional)
     * Contains structured breakdown of compatibility factors
     */
    private Map<String, Object> detailedExplanation;
}
