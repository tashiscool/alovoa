package com.nonononoki.alovoa.entity;

import java.util.Date;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_a_id", "user_b_id"})
})
public class CompatibilityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    private Double valuesScore;
    private Double lifestyleScore;
    private Double personalityScore;
    private Double attractionScore;
    private Double circumstantialScore;
    private Double growthScore;
    private Double overallScore;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String explanationJson;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String topCompatibilities;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String potentialChallenges;

    @Column(nullable = false)
    private Date calculatedAt = new Date();

    private Date userAProfileUpdatedAt;
    private Date userBProfileUpdatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = new Date();
        }
    }
}
