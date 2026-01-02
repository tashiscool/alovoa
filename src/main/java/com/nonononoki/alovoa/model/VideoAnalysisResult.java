package com.nonononoki.alovoa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of AI video analysis containing extracted information
 * about the user from their video introduction.
 * Extended to support profile scaffolding with inferred assessment scores.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoAnalysisResult {

    /**
     * Full transcript of speech from the video
     */
    private String transcript;

    /**
     * AI-extracted summary of the person's worldview
     * (values, beliefs, perspectives on life)
     */
    private String worldviewSummary;

    /**
     * AI-extracted background information
     * (education, career, where they're from)
     */
    private String backgroundSummary;

    /**
     * AI-extracted life story narrative
     * (key life events, journey, experiences)
     */
    private String lifeStorySummary;

    /**
     * Inferred personality indicators from speech patterns and content
     * Keys might include: confidence, openness, warmth, humor, etc.
     */
    private Map<String, Object> personalityIndicators;

    /**
     * Which AI provider performed the analysis
     */
    private String providerName;

    /**
     * Any error message if analysis failed
     */
    private String error;

    // ============================================================
    // PROFILE SCAFFOLDING: Inferred Assessment Scores
    // These enable users to get matchable in ~10 minutes
    // ============================================================

    /**
     * Inferred Big Five personality scores (0-100 scale)
     * Keys: openness, conscientiousness, extraversion, agreeableness, neuroticism
     */
    private Map<String, Double> inferredBigFive;

    /**
     * Inferred values scores (0-100 scale)
     * Keys: progressive, egalitarian
     */
    private Map<String, Double> inferredValues;

    /**
     * Inferred lifestyle scores (0-100 scale)
     * Keys: social, health, workLife, finance
     */
    private Map<String, Double> inferredLifestyle;

    /**
     * Inferred attachment scores (0-100 scale)
     * Keys: anxiety, avoidance
     */
    private Map<String, Double> inferredAttachment;

    /**
     * Inferred attachment style based on anxiety/avoidance scores
     * Values: SECURE, ANXIOUS, AVOIDANT, FEARFUL
     */
    private String inferredAttachmentStyle;

    /**
     * AI-detected dealbreakers from video content
     * Values like: "smoking", "kids_must_have", "religious_match"
     */
    private List<String> suggestedDealbreakers;

    /**
     * Confidence scores for each inference category (0.0 to 1.0)
     * Keys: bigFive, values, lifestyle, attachment, dealbreakers
     */
    private Map<String, Double> confidenceScores;

    /**
     * Areas where AI confidence is low and clarification may be needed
     */
    private List<String> lowConfidenceAreas;

    /**
     * Overall inference confidence (0.0 to 1.0)
     */
    private Double overallConfidence;

    /**
     * Whether the analysis was successful
     */
    public boolean isSuccess() {
        return error == null && transcript != null;
    }

    /**
     * Whether scaffolding inference data is available
     */
    public boolean hasInferenceData() {
        return inferredBigFive != null && !inferredBigFive.isEmpty();
    }

    /**
     * Check if inference confidence is sufficient for scaffolding
     */
    public boolean hasHighConfidence(double threshold) {
        return overallConfidence != null && overallConfidence >= threshold;
    }
}
