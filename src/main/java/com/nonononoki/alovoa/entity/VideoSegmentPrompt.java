package com.nonononoki.alovoa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines prompts for guided video/voice segments.
 * Each prompt guides users on what to talk about in their 2-3 minute video.
 * Examples: worldview, background, personality, relationships, lifestyle
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "video_segment_prompt")
public class VideoSegmentPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt_key", unique = true, nullable = false, length = 50)
    private String promptKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromptCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "suggested_topics", columnDefinition = "TEXT")
    private String suggestedTopics;

    @Lob
    @Column(name = "example_responses", columnDefinition = "TEXT")
    private String exampleResponses;

    @Column(name = "duration_min_seconds")
    private Integer durationMinSeconds = 60;

    @Column(name = "duration_max_seconds")
    private Integer durationMaxSeconds = 180;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "required_for_matching")
    private Boolean requiredForMatching = false;

    @Column
    private Boolean active = true;

    public enum PromptCategory {
        CORE,       // Essential prompts (worldview, background, personality)
        DATING,     // Relationship-focused (what you're looking for, lifestyle)
        OPTIONAL    // Deep dives (values, politics, religion)
    }

    /**
     * Get suggested topics as an array
     */
    public String[] getSuggestedTopicsArray() {
        if (suggestedTopics == null || suggestedTopics.isEmpty()) {
            return new String[0];
        }
        return suggestedTopics.split(",\\s*");
    }
}
