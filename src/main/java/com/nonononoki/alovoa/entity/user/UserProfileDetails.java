package com.nonononoki.alovoa.entity.user;

import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Extended profile details matching OKCupid 2016 feature set.
 * Stores physical attributes, lifestyle, and background info.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
public class UserProfileDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // Physical attributes
    private Integer heightCm;  // Height in centimeters

    @Enumerated(EnumType.STRING)
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    private Ethnicity ethnicity;

    // Lifestyle
    @Enumerated(EnumType.STRING)
    private Diet diet;

    @Enumerated(EnumType.STRING)
    private PetStatus pets;

    private String petDetails;  // e.g., "2 cats, 1 dog"

    // Background
    @Enumerated(EnumType.STRING)
    private EducationLevel education;

    private String occupation;  // Free text job title

    private String employer;  // Optional employer name

    // Languages (comma-separated or JSON array)
    @Column(length = 500)
    private String languages;

    // Zodiac/Astrology
    @Enumerated(EnumType.STRING)
    private ZodiacSign zodiacSign;

    // Response behavior (calculated)
    @Enumerated(EnumType.STRING)
    private ResponseRate responseRate;

    // Income level (optional)
    @Enumerated(EnumType.STRING)
    private IncomeLevel income;

    // === ENUMS ===

    public enum BodyType {
        THIN,
        FIT,
        AVERAGE,
        CURVY,
        FULL_FIGURED,
        OVERWEIGHT,
        JACKED,
        RATHER_NOT_SAY
    }

    public enum Ethnicity {
        ASIAN,
        BLACK,
        HISPANIC_LATINO,
        INDIAN,
        MIDDLE_EASTERN,
        NATIVE_AMERICAN,
        PACIFIC_ISLANDER,
        WHITE,
        MIXED,
        OTHER,
        RATHER_NOT_SAY
    }

    public enum Diet {
        OMNIVORE,
        VEGETARIAN,
        VEGAN,
        PESCATARIAN,
        KOSHER,
        HALAL,
        GLUTEN_FREE,
        OTHER,
        RATHER_NOT_SAY
    }

    public enum PetStatus {
        HAS_PETS,
        HAS_DOGS,
        HAS_CATS,
        HAS_OTHER_PETS,
        NO_PETS_LIKES_THEM,
        NO_PETS_DOESNT_LIKE,
        ALLERGIC,
        RATHER_NOT_SAY
    }

    public enum EducationLevel {
        HIGH_SCHOOL,
        SOME_COLLEGE,
        TRADE_SCHOOL,
        ASSOCIATES,
        BACHELORS,
        MASTERS,
        DOCTORATE,
        OTHER,
        RATHER_NOT_SAY
    }

    public enum ZodiacSign {
        ARIES,
        TAURUS,
        GEMINI,
        CANCER,
        LEO,
        VIRGO,
        LIBRA,
        SCORPIO,
        SAGITTARIUS,
        CAPRICORN,
        AQUARIUS,
        PISCES
    }

    public enum ResponseRate {
        REPLIES_OFTEN,           // > 80% response rate
        REPLIES_SELECTIVELY,     // 40-80%
        REPLIES_VERY_SELECTIVELY // < 40%
    }

    public enum IncomeLevel {
        LESS_THAN_20K,           // Less than $20,000
        INCOME_20K_40K,          // $20,000 - $40,000
        INCOME_40K_60K,          // $40,000 - $60,000
        INCOME_60K_80K,          // $60,000 - $80,000
        INCOME_80K_100K,         // $80,000 - $100,000
        INCOME_100K_150K,        // $100,000 - $150,000
        INCOME_150K_250K,        // $150,000 - $250,000
        INCOME_250K_500K,        // $250,000 - $500,000
        INCOME_500K_PLUS,        // $500,000+
        RATHER_NOT_SAY
    }

    // Helper method to get height in feet/inches
    public String getHeightImperial() {
        if (heightCm == null) return null;
        int totalInches = (int) (heightCm / 2.54);
        int feet = totalInches / 12;
        int inches = totalInches % 12;
        return feet + "'" + inches + "\"";
    }

    // Helper to set height from feet/inches
    public void setHeightFromImperial(int feet, int inches) {
        int totalInches = feet * 12 + inches;
        this.heightCm = (int) (totalInches * 2.54);
    }
}
