package com.nonononoki.alovoa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Curated safe first date spots for matched users.
 * All spots are:
 * - Public and well-lit
 * - Easy to exit if needed
 * - Near public transit
 * - Well-reviewed
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "idx_datespot_city", columnList = "city, state"),
    @Index(name = "idx_datespot_neighborhood", columnList = "neighborhood")
})
public class DateSpotSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Location
    private String neighborhood;  // e.g., "Downtown DC", "Dupont Circle"
    private String city;
    private String state;

    // Venue info
    private String name;          // e.g., "Compass Coffee"
    private String address;       // Full address
    private String description;   // Brief description

    @Enumerated(EnumType.STRING)
    private VenueType venueType;

    @Enumerated(EnumType.STRING)
    private PriceRange priceRange;

    // Safety indicators
    private boolean publicSpace = true;       // Public venue
    private boolean wellLit = true;           // Well-lit area
    private boolean nearTransit = true;       // Near public transit
    private boolean easyExit = true;          // Multiple exits / easy to leave
    private boolean daytimeFriendly = true;   // Good for daytime dates

    // Transit info
    private String nearestTransit;  // e.g., "Metro Center", "Dupont Circle Metro"
    private int walkMinutesFromTransit = 5;

    // Ratings
    private Double averageRating;  // 1-5 stars

    @Column(name = "rating_count")
    private int ratingCount;

    // Status
    private boolean active = true;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // === ENUMS ===

    public enum VenueType {
        COFFEE_SHOP,
        RESTAURANT,
        BAR,
        MUSEUM,
        PARK,
        GALLERY,
        WATERFRONT,
        MARKET,
        BOOKSTORE,
        OTHER
    }

    public enum PriceRange {
        FREE,       // Free venues (parks, museums)
        BUDGET,     // $ - Under $15
        MODERATE,   // $$ - $15-30
        UPSCALE,    // $$$ - $30-60
        EXPENSIVE   // $$$$ - Over $60
    }

    // Helper for price display
    public String getPriceDisplay() {
        return switch (priceRange) {
            case FREE -> "Free";
            case BUDGET -> "$";
            case MODERATE -> "$$";
            case UPSCALE -> "$$$";
            case EXPENSIVE -> "$$$$";
        };
    }

    // Helper for type display
    public String getTypeDisplay() {
        return switch (venueType) {
            case COFFEE_SHOP -> "Coffee";
            case RESTAURANT -> "Restaurant";
            case BAR -> "Bar";
            case MUSEUM -> "Museum";
            case PARK -> "Park";
            case GALLERY -> "Gallery";
            case WATERFRONT -> "Waterfront";
            case MARKET -> "Market";
            case BOOKSTORE -> "Bookstore";
            case OTHER -> "Venue";
        };
    }
}
