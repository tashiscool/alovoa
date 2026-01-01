package com.nonononoki.alovoa.entity.user;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_video_introduction")
public class UserVideoIntroduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private UUID uuid;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    // Video data stored as blob for privacy/security
    @Lob
    @Column(name = "video_data", columnDefinition = "LONGBLOB")
    private byte[] videoData;

    @Column(name = "mime_type", length = 50)
    private String mimeType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // AI-generated transcript of speech
    @Lob
    @Column(columnDefinition = "TEXT")
    private String transcript;

    // AI-extracted worldview summary
    @Lob
    @Column(name = "worldview_summary", columnDefinition = "TEXT")
    private String worldviewSummary;

    // AI-extracted background information
    @Lob
    @Column(name = "background_summary", columnDefinition = "TEXT")
    private String backgroundSummary;

    // AI-extracted life story narrative
    @Lob
    @Column(name = "life_story_summary", columnDefinition = "TEXT")
    private String lifeStorySummary;

    // AI-inferred personality indicators (JSON format)
    @Lob
    @Column(name = "personality_indicators", columnDefinition = "TEXT")
    private String personalityIndicators;

    // Which AI provider processed this video (null if manual entry)
    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    // Whether user opted for AI analysis or manual entry
    @Column(name = "manual_entry")
    private Boolean manualEntry = false;

    @Column(name = "uploaded_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date uploadedAt;

    @Column(name = "analyzed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date analyzedAt;

    // Processing status
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AnalysisStatus status = AnalysisStatus.PENDING;

    public enum AnalysisStatus {
        PENDING,        // Uploaded, awaiting processing
        TRANSCRIBING,   // Speech-to-text in progress
        ANALYZING,      // AI analysis in progress
        COMPLETED,      // All analysis complete
        FAILED,         // Processing failed
        SKIPPED         // User opted for manual entry, no AI analysis
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (uploadedAt == null) {
            uploadedAt = new Date();
        }
    }

    /**
     * Check if AI analysis is complete or user used manual entry
     */
    public boolean isAnalysisComplete() {
        // Manual entry counts as complete
        if (Boolean.TRUE.equals(manualEntry) || status == AnalysisStatus.SKIPPED) {
            return true;
        }
        return status == AnalysisStatus.COMPLETED
                && transcript != null
                && worldviewSummary != null;
    }

    /**
     * Check if profile info (worldview, background, life story) is filled
     * Either via AI or manual entry
     */
    public boolean hasProfileInfo() {
        return worldviewSummary != null || backgroundSummary != null || lifeStorySummary != null;
    }
}
