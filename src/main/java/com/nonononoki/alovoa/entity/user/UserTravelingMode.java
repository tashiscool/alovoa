package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Temporary traveling mode for users visiting other cities.
 * Allows users to see and be seen in a destination before/during travel.
 * Auto-disables after the trip ends.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "idx_traveling_user", columnList = "user_id"),
    @Index(name = "idx_traveling_destination", columnList = "destination_city, destination_state"),
    @Index(name = "idx_traveling_dates", columnList = "arriving_date, leaving_date")
})
public class UserTravelingMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // Destination (user-selected city)
    private String destinationCity;
    private String destinationState;
    private String destinationCountry;
    private String displayAs;  // e.g., "New York, NY"

    // Trip dates
    @Temporal(TemporalType.DATE)
    private Date arrivingDate;

    @Temporal(TemporalType.DATE)
    private Date leavingDate;

    // Visibility controls
    private boolean showMeThere = true;       // Let destination users see me

    @Column(name = "show_locals_to_me")
    private boolean showLocalsToMe = true;    // Show me destination users

    // Auto-disable after trip
    private boolean autoDisable = true;

    // Status
    private boolean active = true;

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

    // Check if traveling mode is currently active
    public boolean isCurrentlyActive() {
        if (!active) return false;
        Date now = new Date();
        // Active from arrival date onwards, until leaving date
        return !now.before(arrivingDate) && !now.after(leavingDate);
    }

    // Check if trip is upcoming (for "Visiting Jan 15-20" display)
    public boolean isUpcoming() {
        if (!active) return false;
        Date now = new Date();
        return now.before(arrivingDate);
    }

    // Check if trip has ended (for auto-disable)
    public boolean hasEnded() {
        Date now = new Date();
        return now.after(leavingDate);
    }

    // Get display string for profile
    public String getProfileDisplay() {
        if (!active) return null;
        // Format: "Visiting New York, NY · Jan 15-20"
        return "Visiting " + displayAs;
    }
}
