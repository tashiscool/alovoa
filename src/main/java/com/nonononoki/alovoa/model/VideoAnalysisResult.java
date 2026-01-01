package com.nonononoki.alovoa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result of AI video analysis containing extracted information
 * about the user from their video introduction.
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

    /**
     * Whether the analysis was successful
     */
    public boolean isSuccess() {
        return error == null && transcript != null;
    }
}
