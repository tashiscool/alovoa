package com.nonononoki.alovoa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mental health resources for intervention and support.
 * Part of AURA's crisis intervention system.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_resource_type", columnList = "resource_type"),
    @Index(name = "idx_resource_country", columnList = "country_code")
})
public class MentalHealthResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ResourceType resourceType;

    @Column(nullable = false, length = 200)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Column(length = 500)
    private String url;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(length = 20)
    private String language = "en";

    @Column(name = "available_24_7")
    private Boolean available247 = false;

    private Integer priority = 0;

    private Boolean active = true;

    public enum ResourceType {
        CRISIS_HOTLINE,     // Phone-based crisis support
        CRISIS_TEXT,        // Text-based crisis support
        ONLINE_CHAT,        // Web chat support
        MENTAL_HEALTH,      // General mental health services
        MEN_SUPPORT,        // Men-specific support services
        INTERNATIONAL,      // International resources
        LOCAL_SERVICE       // Local in-person services
    }
}
