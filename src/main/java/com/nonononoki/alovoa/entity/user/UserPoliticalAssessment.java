package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Political/Economic Assessment for AURA matching.
 * Implements class consciousness testing and economic values compatibility.
 *
 * Gating Logic:
 * 1. Capital-class conservatives are auto-rejected (incompatible with working-class values)
 * 2. Working-class conservatives must provide explanation for assessment
 * 3. Pro-forced-birth males must provide vasectomy verification
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_political_user", columnList = "user_id"),
    @Index(name = "idx_political_class", columnList = "economic_class"),
    @Index(name = "idx_political_orientation", columnList = "political_orientation"),
    @Index(name = "idx_political_gate_status", columnList = "gate_status")
})
public class UserPoliticalAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID uuid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    // === Economic Class Assessment ===

    /**
     * Annual income bracket
     */
    @Enumerated(EnumType.STRING)
    private IncomeBracket incomeBracket;

    /**
     * Primary source of income
     */
    @Enumerated(EnumType.STRING)
    private IncomeSource primaryIncomeSource;

    /**
     * Net worth bracket (optional)
     */
    @Enumerated(EnumType.STRING)
    private WealthBracket wealthBracket;

    /**
     * Whether user owns rental properties
     */
    private Boolean ownsRentalProperties;

    /**
     * Whether user employs others for profit (business owner with employees)
     */
    private Boolean employsOthers;

    /**
     * Whether user derives >50% income from investments/capital gains
     */
    private Boolean livesOffCapital;

    /**
     * Calculated economic class based on answers
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "economic_class")
    private EconomicClass economicClass;

    // === Political/Economic Beliefs ===

    /**
     * Self-reported political orientation (for matching preferences)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "political_orientation")
    private PoliticalOrientation politicalOrientation;

    /**
     * View on wealth redistribution (1-5 scale, 5 = strongly support)
     */
    private Integer wealthRedistributionView;

    /**
     * View on worker ownership/unions (1-5 scale)
     */
    private Integer workerOwnershipView;

    /**
     * View on universal basic services (healthcare, education) (1-5 scale)
     */
    private Integer universalServicesView;

    /**
     * View on landlord/tenant relations (1-5 scale, 5 = tenant rights)
     */
    private Integer housingRightsView;

    /**
     * View on billionaires existing (1-5 scale, 5 = shouldn't exist)
     */
    private Integer billionaireExistenceView;

    /**
     * Agreement: "Hard work alone determines economic success" (1-5, 5 = disagree)
     */
    private Integer meritocracyBeliefView;

    /**
     * KEY GATING QUESTION: Do the wealthy contribute enough to society?
     * If YES and user's income+wealth > median → redirect to Raya
     * If NO or user is below median → continue
     */
    @Enumerated(EnumType.STRING)
    private WealthContributionView wealthContributionView;

    /**
     * JSON storage for additional economic values questions
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String additionalValuesJson;

    /**
     * Calculated economic values alignment score
     * Higher = more aligned with working-class/progressive values
     */
    private Double economicValuesScore;

    // === Reproductive Rights Section (for male users) ===

    /**
     * View on abortion rights
     */
    @Enumerated(EnumType.STRING)
    private ReproductiveRightsView reproductiveRightsView;

    /**
     * For males with FORCED_BIRTH view: vasectomy status
     */
    @Enumerated(EnumType.STRING)
    private VasectomyStatus vasectomyStatus;

    /**
     * Vasectomy verification evidence URL
     */
    @Column(length = 500)
    @JsonIgnore
    private String vasectomyVerificationUrl;

    /**
     * Date vasectomy was verified
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date vasectomyVerifiedAt;

    /**
     * Whether user has acknowledged the vasectomy requirement
     */
    private Boolean acknowledgedVasectomyRequirement;

    /**
     * Optional: Frozen sperm verification for users who want kids but have vasectomy
     */
    @Enumerated(EnumType.STRING)
    private FrozenSpermStatus frozenSpermStatus;

    /**
     * Frozen sperm verification evidence URL (optional)
     */
    @Column(length = 500)
    @JsonIgnore
    private String frozenSpermVerificationUrl;

    /**
     * Date frozen sperm was verified
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date frozenSpermVerifiedAt;

    /**
     * Whether user wants kids (relevant for vasectomy + frozen sperm)
     */
    private Boolean wantsKids;

    // === Gate Status ===

    /**
     * Current gate status for this user
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gate_status", nullable = false)
    private GateStatus gateStatus = GateStatus.PENDING_ASSESSMENT;

    /**
     * Reason for rejection if applicable
     */
    @Enumerated(EnumType.STRING)
    private GateRejectionReason rejectionReason;

    /**
     * For working-class conservatives: their explanation
     */
    @Column(columnDefinition = "TEXT")
    private String conservativeExplanation;

    /**
     * Whether explanation has been reviewed
     */
    private Boolean explanationReviewed;

    /**
     * Admin notes on review
     */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String reviewNotes;

    // === Class Consciousness Test ===

    /**
     * Score on class consciousness questions (0-100)
     */
    private Double classConsciousnessScore;

    /**
     * Whether user can identify which class interests various policies serve
     */
    private Integer policyClassAnalysisScore;

    /**
     * Historical class consciousness (understanding of labor history)
     */
    private Integer laborHistoryScore;

    // === Timestamps ===

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    private Date assessmentCompletedAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdatedAt;

    /**
     * Version of the assessment (for question updates)
     */
    private Integer assessmentVersion = 1;

    // === Enums ===

    public enum IncomeBracket {
        UNDER_25K("Under $25,000"),
        BRACKET_25K_50K("$25,000 - $50,000"),
        BRACKET_50K_75K("$50,000 - $75,000"),
        BRACKET_75K_100K("$75,000 - $100,000"),
        BRACKET_100K_150K("$100,000 - $150,000"),
        BRACKET_150K_250K("$150,000 - $250,000"),
        BRACKET_250K_500K("$250,000 - $500,000"),
        BRACKET_500K_1M("$500,000 - $1,000,000"),
        OVER_1M("Over $1,000,000");

        private final String displayName;

        IncomeBracket(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum IncomeSource {
        WAGES_SALARY,           // Traditional employment
        SELF_EMPLOYED_SOLO,     // Self-employed, no employees
        BUSINESS_OWNER,         // Business owner with employees
        INVESTMENTS_DIVIDENDS,  // Investment income
        RENTAL_INCOME,          // Landlord
        INHERITANCE_TRUST,      // Inherited wealth
        MULTIPLE_SOURCES,       // Combination
        UNEMPLOYED_STUDENT,     // Not currently earning
        RETIRED                 // Retirement income
    }

    public enum WealthBracket {
        NEGATIVE("Negative (debt exceeds assets)"),
        UNDER_10K("Under $10,000"),
        BRACKET_10K_50K("$10,000 - $50,000"),
        BRACKET_50K_100K("$50,000 - $100,000"),
        BRACKET_100K_250K("$100,000 - $250,000"),
        BRACKET_250K_500K("$250,000 - $500,000"),
        BRACKET_500K_1M("$500,000 - $1,000,000"),
        BRACKET_1M_5M("$1,000,000 - $5,000,000"),
        BRACKET_5M_10M("$5,000,000 - $10,000,000"),
        OVER_10M("Over $10,000,000");

        private final String displayName;

        WealthBracket(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum EconomicClass {
        WORKING_CLASS,          // Works for wages, no significant capital
        PROFESSIONAL_CLASS,     // Higher income but still wage-dependent
        SMALL_BUSINESS_OWNER,   // Owns business but works in it
        PETITE_BOURGEOISIE,     // Small landlords, small capital
        CAPITAL_CLASS           // Derives income primarily from ownership
    }

    public enum PoliticalOrientation {
        SOCIALIST,
        PROGRESSIVE,
        LIBERAL,
        MODERATE,
        CONSERVATIVE,
        LIBERTARIAN,
        APOLITICAL,
        OTHER
    }

    public enum ReproductiveRightsView {
        FULL_BODILY_AUTONOMY,           // Full support for reproductive rights
        SENTIENCE_BASED,                // Life begins at sentience - wouldn't be murder to pull the plug on brain dead
        SOME_RESTRICTIONS_OK,           // Supports with some limits
        FORCED_BIRTH,                   // Opposes abortion access
        UNDECIDED,
        PREFER_NOT_TO_SAY
    }

    public enum VasectomyStatus {
        NOT_APPLICABLE,         // Not required (not forced-birth male)
        NOT_VERIFIED,           // Required but not provided
        VERIFICATION_PENDING,   // Submitted, awaiting review
        VERIFIED,               // Confirmed vasectomy
        DECLINED                // Declined to verify (remains gated)
    }

    public enum FrozenSpermStatus {
        NOT_APPLICABLE,         // Doesn't want kids or not vasectomized
        NOT_PROVIDED,           // Wants kids but no proof yet
        VERIFICATION_PENDING,   // Submitted, awaiting review
        VERIFIED,               // Confirmed frozen sperm
        NOT_NEEDED              // Has vasectomy but doesn't want kids
    }

    public enum WealthContributionView {
        CONTRIBUTE_ENOUGH,      // Wealthy contribute enough → if above median wealth, "go use Raya"
        CONTRIBUTE_TOO_LITTLE,  // Wealthy don't contribute enough → pass
        CONTRIBUTE_TOO_MUCH,    // Wealthy are overtaxed → further questioning
        NOT_SURE,               // Unsure → continue with context
        SYSTEM_IS_FINE          // Current system works → if above median, redirect to Raya
    }

    public enum GateStatus {
        PENDING_ASSESSMENT,     // Haven't completed assessment
        APPROVED,               // Passed all gates
        PENDING_EXPLANATION,    // Working-class conservative, awaiting explanation
        PENDING_VASECTOMY,      // Pro-forced-birth male, awaiting verification
        REJECTED,               // Failed gates (capital-class conservative)
        REDIRECT_RAYA,          // Above median wealth + thinks wealthy contribute enough → use Raya
        UNDER_REVIEW            // Manual review needed
    }

    public enum GateRejectionReason {
        CAPITAL_CLASS_CONSERVATIVE,     // Auto-reject: rich + conservative
        UNEXPLAINED_CONSERVATIVE,       // Working-class conservative, no explanation
        UNVERIFIED_FORCED_BIRTH,        // Pro-forced-birth male, no vasectomy proof
        ABOVE_MEDIAN_WEALTH_DEFENDER,   // Above median wealth + thinks wealthy contribute enough
        POLICY_VIOLATION                // Other policy violation
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

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = new Date();
    }

    /**
     * Calculate economic class based on assessment answers
     */
    public void calculateEconomicClass() {
        // Capital class if:
        // - Lives off capital gains primarily, OR
        // - Owns rental properties AND employs others, OR
        // - Very high wealth bracket with investment income
        if (Boolean.TRUE.equals(livesOffCapital)) {
            this.economicClass = EconomicClass.CAPITAL_CLASS;
            return;
        }

        if (Boolean.TRUE.equals(ownsRentalProperties) && Boolean.TRUE.equals(employsOthers)) {
            this.economicClass = EconomicClass.CAPITAL_CLASS;
            return;
        }

        if ((wealthBracket == WealthBracket.OVER_10M || wealthBracket == WealthBracket.BRACKET_5M_10M)
            && primaryIncomeSource == IncomeSource.INVESTMENTS_DIVIDENDS) {
            this.economicClass = EconomicClass.CAPITAL_CLASS;
            return;
        }

        // Petite bourgeoisie if:
        // - Owns rental properties OR employs others (but not both extensively)
        if (Boolean.TRUE.equals(ownsRentalProperties) ||
            primaryIncomeSource == IncomeSource.RENTAL_INCOME) {
            this.economicClass = EconomicClass.PETITE_BOURGEOISIE;
            return;
        }

        // Small business owner
        if (primaryIncomeSource == IncomeSource.BUSINESS_OWNER || Boolean.TRUE.equals(employsOthers)) {
            this.economicClass = EconomicClass.SMALL_BUSINESS_OWNER;
            return;
        }

        // Professional class if high income but wage-dependent
        if (incomeBracket != null &&
            (incomeBracket.ordinal() >= IncomeBracket.BRACKET_100K_150K.ordinal()) &&
            (primaryIncomeSource == IncomeSource.WAGES_SALARY ||
             primaryIncomeSource == IncomeSource.SELF_EMPLOYED_SOLO)) {
            this.economicClass = EconomicClass.PROFESSIONAL_CLASS;
            return;
        }

        // Default to working class
        this.economicClass = EconomicClass.WORKING_CLASS;
    }

    /**
     * Calculate economic values alignment score
     */
    public void calculateEconomicValuesScore() {
        double total = 0;
        int count = 0;

        if (wealthRedistributionView != null) {
            total += wealthRedistributionView;
            count++;
        }
        if (workerOwnershipView != null) {
            total += workerOwnershipView;
            count++;
        }
        if (universalServicesView != null) {
            total += universalServicesView;
            count++;
        }
        if (housingRightsView != null) {
            total += housingRightsView;
            count++;
        }
        if (billionaireExistenceView != null) {
            total += billionaireExistenceView;
            count++;
        }
        if (meritocracyBeliefView != null) {
            total += meritocracyBeliefView;
            count++;
        }

        if (count > 0) {
            // Convert to 0-100 scale
            this.economicValuesScore = (total / count / 5.0) * 100;
        }
    }

    /**
     * Determine gate status based on assessment
     */
    public void evaluateGateStatus(boolean isMale) {
        // Must complete assessment first
        if (economicClass == null || politicalOrientation == null) {
            this.gateStatus = GateStatus.PENDING_ASSESSMENT;
            return;
        }

        // Gate 0: Wealth contribution check - "Go use Raya" gate
        // These views require income + wealth checks: CONTRIBUTE_ENOUGH, CONTRIBUTE_TOO_MUCH, SYSTEM_IS_FINE, NOT_SURE
        // Only CONTRIBUTE_TOO_LITTLE gets a pass without checks
        if (wealthContributionView != null &&
            wealthContributionView != WealthContributionView.CONTRIBUTE_TOO_LITTLE) {

            // Check if user is above median wealth (using wealth + income as proxy)
            boolean isAboveMedianWealth = isAboveMedianWealth();

            if (isAboveMedianWealth) {
                // Above median AND defends wealthy/system = redirect to Raya
                this.gateStatus = GateStatus.REDIRECT_RAYA;
                this.rejectionReason = GateRejectionReason.ABOVE_MEDIAN_WEALTH_DEFENDER;
                return;
            }

            // Below median but defends wealthy/is unsure - needs explanation
            if (conservativeExplanation == null || conservativeExplanation.trim().length() < 100) {
                this.gateStatus = GateStatus.PENDING_EXPLANATION;
                return;
            }
        }

        // Gate 1: Capital class + conservative = auto-reject
        if (economicClass == EconomicClass.CAPITAL_CLASS &&
            (politicalOrientation == PoliticalOrientation.CONSERVATIVE ||
             politicalOrientation == PoliticalOrientation.LIBERTARIAN)) {
            this.gateStatus = GateStatus.REJECTED;
            this.rejectionReason = GateRejectionReason.CAPITAL_CLASS_CONSERVATIVE;
            return;
        }

        // Gate 2: Working-class conservative needs explanation
        if ((economicClass == EconomicClass.WORKING_CLASS ||
             economicClass == EconomicClass.PROFESSIONAL_CLASS) &&
            politicalOrientation == PoliticalOrientation.CONSERVATIVE) {
            if (conservativeExplanation == null || conservativeExplanation.trim().length() < 100) {
                this.gateStatus = GateStatus.PENDING_EXPLANATION;
                return;
            }
            // Has explanation, needs review
            if (explanationReviewed == null || !explanationReviewed) {
                this.gateStatus = GateStatus.UNDER_REVIEW;
                return;
            }
        }

        // Gate 3: Male + any non-pro-choice view = vasectomy requirement
        // Vasectomy required for: SOME_RESTRICTIONS_OK, FORCED_BIRTH, UNDECIDED, PREFER_NOT_TO_SAY
        if (isMale && reproductiveRightsView != null &&
            reproductiveRightsView != ReproductiveRightsView.FULL_BODILY_AUTONOMY &&
            reproductiveRightsView != ReproductiveRightsView.SENTIENCE_BASED) {

            if (vasectomyStatus == null ||
                vasectomyStatus == VasectomyStatus.NOT_VERIFIED ||
                vasectomyStatus == VasectomyStatus.DECLINED) {
                this.gateStatus = GateStatus.PENDING_VASECTOMY;
                this.rejectionReason = GateRejectionReason.UNVERIFIED_FORCED_BIRTH;
                return;
            }
            if (vasectomyStatus == VasectomyStatus.VERIFICATION_PENDING) {
                this.gateStatus = GateStatus.UNDER_REVIEW;
                return;
            }
        }

        // Passed all gates
        this.gateStatus = GateStatus.APPROVED;
        this.rejectionReason = null;
    }

    /**
     * Check if user's combined income + wealth is above median
     * US median household net worth ~$192,900 (2022)
     * US median household income ~$74,580 (2022)
     */
    public boolean isAboveMedianWealth() {
        // Convert brackets to estimated values and compare to median thresholds
        int incomeValue = getIncomeBracketMidpoint();
        int wealthValue = getWealthBracketMidpoint();

        // Above median if:
        // - Wealth > $200k OR
        // - Income > $100k AND Wealth > $100k OR
        // - Combined score suggests above median
        if (wealthValue >= 200000) return true;
        if (incomeValue >= 100000 && wealthValue >= 100000) return true;
        if (incomeValue + wealthValue > 275000) return true;

        return false;
    }

    private int getIncomeBracketMidpoint() {
        if (incomeBracket == null) return 0;
        return switch (incomeBracket) {
            case UNDER_25K -> 15000;
            case BRACKET_25K_50K -> 37500;
            case BRACKET_50K_75K -> 62500;
            case BRACKET_75K_100K -> 87500;
            case BRACKET_100K_150K -> 125000;
            case BRACKET_150K_250K -> 200000;
            case BRACKET_250K_500K -> 375000;
            case BRACKET_500K_1M -> 750000;
            case OVER_1M -> 1500000;
        };
    }

    private int getWealthBracketMidpoint() {
        if (wealthBracket == null) return 0;
        return switch (wealthBracket) {
            case NEGATIVE -> -10000;
            case UNDER_10K -> 5000;
            case BRACKET_10K_50K -> 30000;
            case BRACKET_50K_100K -> 75000;
            case BRACKET_100K_250K -> 175000;
            case BRACKET_250K_500K -> 375000;
            case BRACKET_500K_1M -> 750000;
            case BRACKET_1M_5M -> 3000000;
            case BRACKET_5M_10M -> 7500000;
            case OVER_10M -> 15000000;
        };
    }
}
