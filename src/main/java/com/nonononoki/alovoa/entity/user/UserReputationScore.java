package com.nonononoki.alovoa.entity.user;

import java.util.Date;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class UserReputationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false)
    private Double responseQuality = 50.0;

    @Column(nullable = false)
    private Double respectScore = 50.0;

    @Column(nullable = false)
    private Double authenticityScore = 50.0;

    @Column(nullable = false)
    private Double investmentScore = 50.0;

    private Integer ghostingCount = 0;

    private Integer reportsReceived = 0;

    private Integer reportsUpheld = 0;

    private Integer datesCompleted = 0;

    private Integer positiveFeedbackCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrustLevel trustLevel = TrustLevel.NEW_MEMBER;

    @Column(nullable = false)
    private Date updatedAt = new Date();

    // Appeal-related fields
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastAppealedAt;

    @Column(nullable = false)
    private Boolean appealPending = false;

    @Temporal(TemporalType.TIMESTAMP)
    private Date probationUntil;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timeDecayAppliedAt;

    public enum TrustLevel {
        NEW_MEMBER,
        VERIFIED,
        TRUSTED,
        HIGHLY_TRUSTED,
        UNDER_REVIEW,
        PROBATION,  // Between UNDER_REVIEW and RESTRICTED - recovery path
        RESTRICTED
    }

    public Double getOverallScore() {
        return (responseQuality + respectScore + authenticityScore + investmentScore) / 4.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}
