package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Evidence attached to an accountability report.
 * Screenshots are verified against the message database when possible.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_evidence_report", columnList = "report_id"),
    @Index(name = "idx_evidence_type", columnList = "evidence_type"),
    @Index(name = "idx_evidence_verified", columnList = "verified")
})
public class ReportEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    /**
     * The accountability report this evidence belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    @JsonIgnore
    private UserAccountabilityReport report;

    /**
     * Type of evidence
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false)
    private EvidenceType evidenceType;

    /**
     * URL to the stored evidence file (screenshot, etc.)
     */
    @Column(length = 500)
    private String fileUrl;

    /**
     * Original filename
     */
    @Column(length = 255)
    private String originalFilename;

    /**
     * MIME type of the file
     */
    @Column(length = 100)
    private String mimeType;

    /**
     * File size in bytes
     */
    private Long fileSize;

    /**
     * Description/caption for this evidence
     */
    @Column(columnDefinition = "TEXT")
    private String caption;

    /**
     * OCR-extracted text from screenshot (for verification)
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    @JsonIgnore
    private String extractedText;

    /**
     * Whether this evidence has been verified
     */
    private boolean verified = false;

    /**
     * Verification method used
     */
    @Enumerated(EnumType.STRING)
    private VerificationMethod verificationMethod;

    /**
     * Confidence score of verification (0-100)
     */
    private Double verificationConfidence;

    /**
     * If verified against message DB, the matched message IDs
     */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String matchedMessageIds;

    /**
     * Hash of the image for duplicate detection
     */
    @Column(length = 64)
    @JsonIgnore
    private String imageHash;

    /**
     * Whether the image appears manipulated
     */
    private Boolean appearsTampered;

    /**
     * Notes about tampering detection
     */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String tamperAnalysisNotes;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date uploadedAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    private Date verifiedAt;

    /**
     * Order for displaying multiple evidence items
     */
    private int displayOrder = 0;

    // Enums

    public enum EvidenceType {
        SCREENSHOT_MESSAGE,     // Screenshot of messages in app
        SCREENSHOT_PROFILE,     // Screenshot of profile
        SCREENSHOT_EXTERNAL,    // Screenshot from external platform
        VIDEO_RECORDING,        // Screen recording
        DATE_LOCATION_PROOF,    // Photo showing stood up at location
        OTHER                   // Other evidence type
    }

    public enum VerificationMethod {
        NONE,                   // Not verified
        OCR_MESSAGE_MATCH,      // OCR text matched to message DB
        METADATA_ANALYSIS,      // EXIF/metadata verification
        MANUAL_ADMIN_REVIEW,    // Admin manually verified
        AI_ANALYSIS,            // AI-assisted verification
        HASH_DUPLICATE          // Matched known verified evidence
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
}
