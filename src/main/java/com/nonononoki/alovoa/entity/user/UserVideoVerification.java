package com.nonononoki.alovoa.entity.user;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class UserVideoVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private UUID uuid;

    @JsonIgnore
    @OneToOne
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(length = 500)
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status = VerificationStatus.PENDING;

    private Double faceMatchScore;

    private Double livenessScore;

    private Double deepfakeScore;

    private Date verifiedAt;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(length = 500)
    private String failureReason;

    @Column(length = 100)
    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String captureMetadata;  // JSON with mimeType, duration, resolution, challenge timestamps

    public enum VerificationStatus {
        PENDING,
        PROCESSING,
        VERIFIED,
        FAILED,
        EXPIRED
    }

    public boolean isVerified() {
        return status == VerificationStatus.VERIFIED;
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}
