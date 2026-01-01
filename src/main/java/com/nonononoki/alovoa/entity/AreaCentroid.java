package com.nonononoki.alovoa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Static reference data for area centroids.
 * These are PUBLIC geographic data points - NOT user locations.
 * Used for calculating approximate travel times between areas.
 *
 * PRIVACY: These centroids represent general area centers (e.g., metro stations,
 * town centers), NOT specific addresses or user locations.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "idx_centroid_city_state", columnList = "city, state"),
    @Index(name = "idx_centroid_neighborhood", columnList = "neighborhood, city, state")
})
public class AreaCentroid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Location hierarchy
    private String neighborhood;  // Optional, e.g., "Clarendon", "Dupont Circle"
    private String city;          // e.g., "Arlington", "Washington"
    private String state;         // e.g., "VA", "DC"
    private String country;       // e.g., "US"

    // Centroid coordinates (public reference point, NOT user location)
    // Typically a central metro station, main intersection, or town center
    private Double centroidLat;
    private Double centroidLng;

    // Reference point name (for transparency)
    private String referencePoint;  // e.g., "Clarendon Metro Station", "Dupont Circle"

    // Pre-calculated travel times to other major areas (optional optimization)
    // Format: JSON map of "city,state" -> minutes
    @Column(columnDefinition = "TEXT")
    private String travelTimeCache;

    // Display name for this area
    private String displayName;

    // Metro area grouping
    private String metroArea;  // e.g., "DC Metro", "NYC Metro"

    public AreaCentroid(String city, String state, Double lat, Double lng) {
        this.city = city;
        this.state = state;
        this.centroidLat = lat;
        this.centroidLng = lng;
        this.country = "US";
    }

    public AreaCentroid(String neighborhood, String city, String state, Double lat, Double lng) {
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        this.centroidLat = lat;
        this.centroidLng = lng;
        this.country = "US";
    }

    /**
     * Get unique key for this area.
     */
    public String getAreaKey() {
        if (neighborhood != null && !neighborhood.isBlank()) {
            return neighborhood + "," + city + "," + state;
        }
        return city + "," + state;
    }
}
