package com.nonononoki.alovoa.entity.user;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoSegmentPrompt;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Individual video/voice segment uploaded by a user for a specific prompt.
 * Users record 2-3 minute segments for each prompt (worldview, background, etc.)
 * These segments are:
 * 1. Analyzed by AI to scaffold the user's profile
 * 2. Viewable by potential matches to get a feel for the person
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_video_segment",
    indexes = {
        @Index(name = "idx_segment_user", columnList = "user_id"),
        @Index(name = "idx_segment_status", columnList = "status")
    })
public class UserVideoSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String uuid;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "prompt_id", nullable = false)
    private VideoSegmentPrompt prompt;

    // S3 key for video/audio storage
    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(name = "mime_type", length = 50)
    private String mimeType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // Media type (video or voice-only)
    @Enumerated(EnumType.STRING)
    @Column(name = "intro_type", length = 20)
    private IntroType introType = IntroType.VIDEO;

    // AI-generated transcript
    @Lob
    @Column(columnDefinition = "TEXT")
    private String transcript;

    // AI-generated summary for this specific segment
    @Lob
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    // Which AI provider processed this segment
    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    // Processing status
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AnalysisStatus status = AnalysisStatus.PENDING;

    @Column(name = "uploaded_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date uploadedAt;

    @Column(name = "analyzed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date analyzedAt;

    public enum IntroType {
        VIDEO,       // Full video
        VOICE_ONLY   // Audio-only recording
    }

    public enum AnalysisStatus {
        PENDING,        // Uploaded, awaiting processing
        TRANSCRIBING,   // Speech-to-text in progress
        ANALYZING,      // AI analysis in progress
        COMPLETED,      // Analysis complete
        FAILED          // Processing failed
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (uploadedAt == null) {
            uploadedAt = new Date();
        }
    }

    /**
     * Check if this segment has been successfully analyzed
     */
    public boolean isAnalyzed() {
        return status == AnalysisStatus.COMPLETED && transcript != null;
    }

    /**
     * Get the prompt key for this segment
     */
    public String getPromptKey() {
        return prompt != null ? prompt.getPromptKey() : null;
    }
}
