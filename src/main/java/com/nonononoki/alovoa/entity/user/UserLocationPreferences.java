package com.nonononoki.alovoa.entity.user;

import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * User's location matching preferences.
 * Controls how location-based matching works without exposing actual location.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
public class UserLocationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // Maximum travel time for a first date (in minutes)
    // Options: 10, 15, 20, 30, 45, 60
    private int maxTravelMinutes = 30;

    // Matching preferences
    private boolean requireAreaOverlap = true;      // Only match with overlapping areas
    private boolean showExceptionalMatches = true;  // Show 90%+ matches outside areas
    private double exceptionalMatchThreshold = 0.90; // Threshold for exceptional matches

    // Future location (for "moving to" scenarios)
    private String movingToCity;
    private String movingToState;

    @Temporal(TemporalType.DATE)
    private Date movingDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }

    // Get moving display string
    public String getMovingDisplay() {
        if (movingToCity == null || movingDate == null) return null;
        return "Moving to " + movingToCity + ", " + movingToState;
    }
}
