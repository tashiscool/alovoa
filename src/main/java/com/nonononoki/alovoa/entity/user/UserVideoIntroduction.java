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

    // S3 key for video storage
    @Column(name = "s3_key")
    private String s3Key;

    // Public URL for video playback (CDN or signed URL)
    @Column(name = "public_video_url", length = 500)
    private String publicVideoUrl;

    // Thumbnail URL for video preview
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

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

    // === Voice Intro Support (Known app feature) ===
    // Short 2-3 minute voice intro that supplements the question bank

    @Enumerated(EnumType.STRING)
    @Column(name = "intro_type")
    private IntroType introType = IntroType.VIDEO;

    // S3 key for voice-only audio file
    @Column(name = "voice_only_url", length = 500)
    private String voiceOnlyUrl;

    // Duration of voice intro in seconds
    @Column(name = "voice_duration_seconds")
    private Integer voiceDurationSeconds;

    public enum IntroType {
        VIDEO,       // Full video intro (original)
        VOICE_ONLY,  // 2-3 minute voice recording (Known-style)
        TEXT_ONLY    // Written intro only (fallback)
    }

    public enum AnalysisStatus {
        PENDING,        // Uploaded, awaiting processing
        TRANSCRIBING,   // Speech-to-text in progress
        ANALYZING,      // AI analysis in progress
        COMPLETED,      // All analysis complete
        FAILED,         // Processing failed
        SKIPPED         // User opted for manual entry, no AI analysis
    }

    // ============================================================
    // PROFILE SCAFFOLDING: Inferred Assessment Scores
    // Aggregated from all video segments for quick profile creation
    // ============================================================

    @Lob
    @Column(name = "inferred_assessment_json", columnDefinition = "TEXT")
    private String inferredAssessmentJson;

    // Big Five scores (0-100)
    @Column(name = "inferred_openness")
    private Double inferredOpenness;

    @Column(name = "inferred_conscientiousness")
    private Double inferredConscientiousness;

    @Column(name = "inferred_extraversion")
    private Double inferredExtraversion;

    @Column(name = "inferred_agreeableness")
    private Double inferredAgreeableness;

    @Column(name = "inferred_neuroticism")
    private Double inferredNeuroticism;

    // Attachment scores
    @Column(name = "inferred_attachment_anxiety")
    private Double inferredAttachmentAnxiety;

    @Column(name = "inferred_attachment_avoidance")
    private Double inferredAttachmentAvoidance;

    @Column(name = "inferred_attachment_style", length = 30)
    private String inferredAttachmentStyle;

    // Values scores (0-100)
    @Column(name = "inferred_values_progressive")
    private Double inferredValuesProgressive;

    @Column(name = "inferred_values_egalitarian")
    private Double inferredValuesEgalitarian;

    // Lifestyle scores (0-100)
    @Column(name = "inferred_lifestyle_social")
    private Double inferredLifestyleSocial;

    @Column(name = "inferred_lifestyle_health")
    private Double inferredLifestyleHealth;

    @Column(name = "inferred_lifestyle_work_life")
    private Double inferredLifestyleWorkLife;

    @Column(name = "inferred_lifestyle_finance")
    private Double inferredLifestyleFinance;

    // Confidence
    @Column(name = "overall_inference_confidence")
    private Double overallInferenceConfidence;

    // Review status
    @Column(name = "inference_reviewed")
    private Boolean inferenceReviewed = false;

    @Column(name = "inference_confirmed")
    private Boolean inferenceConfirmed = false;

    @Column(name = "confirmed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date confirmedAt;

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
     * Check if inference data is available
     */
    public boolean hasInferenceData() {
        return inferredOpenness != null;
    }

    /**
     * Check if inference has been reviewed and confirmed
     */
    public boolean isInferenceConfirmed() {
        return Boolean.TRUE.equals(inferenceConfirmed);
    }

    /**
     * Clear all inference data (for re-record)
     */
    public void clearInferenceData() {
        inferredAssessmentJson = null;
        inferredOpenness = null;
        inferredConscientiousness = null;
        inferredExtraversion = null;
        inferredAgreeableness = null;
        inferredNeuroticism = null;
        inferredAttachmentAnxiety = null;
        inferredAttachmentAvoidance = null;
        inferredAttachmentStyle = null;
        inferredValuesProgressive = null;
        inferredValuesEgalitarian = null;
        inferredLifestyleSocial = null;
        inferredLifestyleHealth = null;
        inferredLifestyleWorkLife = null;
        inferredLifestyleFinance = null;
        overallInferenceConfidence = null;
        inferenceReviewed = false;
        inferenceConfirmed = false;
        confirmedAt = null;
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
