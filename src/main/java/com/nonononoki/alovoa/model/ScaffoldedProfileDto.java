package com.nonononoki.alovoa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DTO for presenting a scaffolded profile to the user for review.
 * Contains AI-inferred scores with confidence indicators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaffoldedProfileDto {

    /**
     * Big Five personality scores with confidence
     */
    private Map<String, ScoreWithConfidence> bigFive;

    /**
     * Values scores with confidence
     */
    private Map<String, ScoreWithConfidence> values;

    /**
     * Lifestyle scores with confidence
     */
    private Map<String, ScoreWithConfidence> lifestyle;

    /**
     * Attachment inference with confidence
     */
    private AttachmentInference attachment;

    /**
     * AI-suggested dealbreakers for user confirmation
     */
    private List<DealbreakeSuggestion> suggestedDealbreakers;

    /**
     * Areas where AI confidence is low and clarification may help
     */
    private List<String> lowConfidenceAreas;

    /**
     * Overall confidence in the scaffolded profile
     */
    private Double overallConfidence;

    /**
     * Whether user has reviewed this scaffolded profile
     */
    private boolean reviewed;

    /**
     * Whether user has confirmed this scaffolded profile
     */
    private boolean confirmed;

    /**
     * Timestamp when profile was confirmed
     */
    private Date confirmedAt;

    /**
     * Video segments used to create this scaffolding
     */
    private List<SegmentSummary> segments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreWithConfidence {
        private Double score;
        private Double confidence;
        private String explanation;

        public boolean isHighConfidence() {
            return confidence != null && confidence >= 0.7;
        }

        public boolean isLowConfidence() {
            return confidence == null || confidence < 0.5;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentInference {
        private Double anxietyScore;
        private Double avoidanceScore;
        private String style;  // SECURE, ANXIOUS, AVOIDANT, FEARFUL
        private Double confidence;
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DealbreakeSuggestion {
        private String key;
        private String label;
        private Double confidence;
        private String sourceQuote;
        private boolean confirmed;
        private boolean rejected;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentSummary {
        private String promptKey;
        private String promptTitle;
        private String status;
        private boolean analyzed;
        private Date uploadedAt;
    }

    /**
     * Check if all required segments are complete
     */
    public boolean allSegmentsComplete() {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        return segments.stream().allMatch(SegmentSummary::isAnalyzed);
    }

    /**
     * Check if profile has sufficient confidence for matching
     */
    public boolean hasSufficientConfidence(double threshold) {
        return overallConfidence != null && overallConfidence >= threshold;
    }

    /**
     * Get the number of low confidence areas
     */
    public int getLowConfidenceCount() {
        return lowConfidenceAreas != null ? lowConfidenceAreas.size() : 0;
    }
}
