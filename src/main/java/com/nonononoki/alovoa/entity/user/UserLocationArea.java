package com.nonononoki.alovoa.entity.user;

import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * User-declared location areas for privacy-safe matching.
 * Users can declare up to 3 areas where they're open to meeting people.
 * NO GPS coordinates are stored - only user-selected areas.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "idx_location_user", columnList = "user_id"),
    @Index(name = "idx_location_city_state", columnList = "city, state")
})
public class UserLocationArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Area type
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AreaType areaType = AreaType.PRIMARY;

    // Actual location (user-selected, not tracked)
    private String neighborhood;  // e.g., "Clarendon", "Dupont Circle"
    private String city;          // e.g., "Arlington", "Washington"
    private String state;         // e.g., "VA", "DC"
    private String country;       // e.g., "US"

    // Display settings (privacy control)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisplayLevel displayLevel = DisplayLevel.CITY;

    private String displayAs;     // What shows on profile, e.g., "Northern Virginia"

    // Optional label (not shown to others, helps user organize)
    @Enumerated(EnumType.STRING)
    private AreaLabel label;

    // Visibility
    private boolean visibleOnProfile = true;  // false = matching only

    // Order for display
    private int displayOrder = 0;

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

    // === ENUMS ===

    public enum AreaType {
        PRIMARY,    // Required home area
        SECONDARY,  // Optional additional area
        TERTIARY    // Optional third area
    }

    public enum DisplayLevel {
        NEIGHBORHOOD,  // Most specific: "Dupont Circle"
        CITY,          // City level: "Washington, DC"
        REGION,        // Regional: "Northern Virginia", "DC Metro"
        METRO,         // Vague: "DC Metro Area"
        HIDDEN         // Don't show this area on profile
    }

    public enum AreaLabel {
        HOME,
        WORK,
        HANGOUT,
        FRIENDS_LIVE,
        LOVE_THIS_AREA,
        MOVING_TO,
        PREFER_NOT_TO_SAY
    }

    // Helper to get formatted display string
    public String getFormattedDisplay() {
        if (displayAs != null && !displayAs.isBlank()) {
            return displayAs;
        }
        return switch (displayLevel) {
            case NEIGHBORHOOD -> neighborhood != null ? neighborhood + ", " + city : city;
            case CITY -> city + ", " + state;
            case REGION -> getRegionName();
            case METRO -> getMetroName();
            case HIDDEN -> null;
        };
    }

    private String getRegionName() {
        // Common DC Metro regions
        if ("VA".equals(state)) return "Northern Virginia";
        if ("MD".equals(state)) return "Maryland";
        if ("DC".equals(state)) return "Washington, DC";
        return city + ", " + state;
    }

    private String getMetroName() {
        // For DC area, show metro area
        if ("VA".equals(state) || "MD".equals(state) || "DC".equals(state)) {
            return "DC Metro Area";
        }
        return city + " Metro Area";
    }
}
