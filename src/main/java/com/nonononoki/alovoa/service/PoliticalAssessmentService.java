package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Gender;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.*;
import com.nonononoki.alovoa.repo.UserPoliticalAssessmentRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Service for Political/Economic Assessment and gating.
 *
 * Implements the following gates:
 * 1. Capital-class conservatives are auto-rejected
 * 2. Working-class conservatives must provide explanation
 * 3. Pro-forced-birth males must verify vasectomy
 */
@Service
public class PoliticalAssessmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoliticalAssessmentService.class);

    // Current assessment version
    private static final int CURRENT_ASSESSMENT_VERSION = 1;

    @Autowired
    private UserPoliticalAssessmentRepository assessmentRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthService authService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.aura.media-service.url:http://localhost:8001}")
    private String mediaServiceUrl;

    /**
     * Start or get existing assessment for a user
     */
    public UserPoliticalAssessment getOrCreateAssessment(User user) {
        return assessmentRepo.findByUser(user)
            .orElseGet(() -> {
                UserPoliticalAssessment assessment = new UserPoliticalAssessment();
                assessment.setUuid(UUID.randomUUID());
                assessment.setUser(user);
                assessment.setGateStatus(GateStatus.PENDING_ASSESSMENT);
                assessment.setCreatedAt(new Date());
                assessment.setAssessmentVersion(CURRENT_ASSESSMENT_VERSION);
                return assessmentRepo.save(assessment);
            });
    }

    /**
     * Submit economic class assessment (step 1)
     */
    @Transactional
    public UserPoliticalAssessment submitEconomicClass(
            User user,
            IncomeBracket incomeBracket,
            IncomeSource primaryIncomeSource,
            WealthBracket wealthBracket,
            Boolean ownsRentalProperties,
            Boolean employsOthers,
            Boolean livesOffCapital) {

        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        assessment.setIncomeBracket(incomeBracket);
        assessment.setPrimaryIncomeSource(primaryIncomeSource);
        assessment.setWealthBracket(wealthBracket);
        assessment.setOwnsRentalProperties(ownsRentalProperties);
        assessment.setEmploysOthers(employsOthers);
        assessment.setLivesOffCapital(livesOffCapital);

        // Calculate economic class
        assessment.calculateEconomicClass();

        return assessmentRepo.save(assessment);
    }

    /**
     * Submit political orientation and economic values (step 2)
     */
    @Transactional
    public UserPoliticalAssessment submitPoliticalValues(
            User user,
            PoliticalOrientation politicalOrientation,
            Integer wealthRedistributionView,
            Integer workerOwnershipView,
            Integer universalServicesView,
            Integer housingRightsView,
            Integer billionaireExistenceView,
            Integer meritocracyBeliefView,
            Map<String, Object> additionalValues) {

        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        assessment.setPoliticalOrientation(politicalOrientation);
        assessment.setWealthRedistributionView(wealthRedistributionView);
        assessment.setWorkerOwnershipView(workerOwnershipView);
        assessment.setUniversalServicesView(universalServicesView);
        assessment.setHousingRightsView(housingRightsView);
        assessment.setBillionaireExistenceView(billionaireExistenceView);
        assessment.setMeritocracyBeliefView(meritocracyBeliefView);

        if (additionalValues != null) {
            try {
                assessment.setAdditionalValuesJson(objectMapper.writeValueAsString(additionalValues));
            } catch (JsonProcessingException e) {
                LOGGER.error("Failed to serialize additional values", e);
            }
        }

        // Calculate economic values score
        assessment.calculateEconomicValuesScore();

        return assessmentRepo.save(assessment);
    }

    /**
     * Submit reproductive rights view (step 3 for applicable users)
     */
    @Transactional
    public UserPoliticalAssessment submitReproductiveView(
            User user,
            ReproductiveRightsView reproductiveRightsView) {

        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        assessment.setReproductiveRightsView(reproductiveRightsView);

        // If male and forced-birth, set vasectomy status
        boolean isMale = user.getGender() != null &&
                        user.getGender().getId() == Gender.MALE;

        if (isMale && reproductiveRightsView == ReproductiveRightsView.FORCED_BIRTH) {
            assessment.setVasectomyStatus(VasectomyStatus.NOT_VERIFIED);
            assessment.setAcknowledgedVasectomyRequirement(false);
        } else {
            assessment.setVasectomyStatus(VasectomyStatus.NOT_APPLICABLE);
        }

        return assessmentRepo.save(assessment);
    }

    /**
     * Submit class consciousness test answers
     */
    @Transactional
    public UserPoliticalAssessment submitClassConsciousnessTest(
            User user,
            Map<String, Integer> answers) {

        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        // Calculate class consciousness score based on answers
        // Questions test understanding of class relations and interests
        double score = calculateClassConsciousnessScore(answers);
        assessment.setClassConsciousnessScore(score);

        // Specific subscores
        if (answers.containsKey("policyAnalysis")) {
            assessment.setPolicyClassAnalysisScore(answers.get("policyAnalysis"));
        }
        if (answers.containsKey("laborHistory")) {
            assessment.setLaborHistoryScore(answers.get("laborHistory"));
        }

        return assessmentRepo.save(assessment);
    }

    /**
     * Complete assessment and evaluate gates
     */
    @Transactional
    public UserPoliticalAssessment completeAssessment(User user) {
        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        boolean isMale = user.getGender() != null &&
                        user.getGender().getId() == Gender.MALE;

        // Ensure calculations are done
        if (assessment.getEconomicClass() == null) {
            assessment.calculateEconomicClass();
        }
        if (assessment.getEconomicValuesScore() == null) {
            assessment.calculateEconomicValuesScore();
        }

        // Evaluate gate status
        assessment.evaluateGateStatus(isMale);

        assessment.setAssessmentCompletedAt(new Date());

        return assessmentRepo.save(assessment);
    }

    /**
     * Submit conservative explanation (for working-class conservatives)
     */
    @Transactional
    public UserPoliticalAssessment submitConservativeExplanation(User user, String explanation) {
        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        if (assessment.getGateStatus() != GateStatus.PENDING_EXPLANATION) {
            throw new IllegalStateException("Explanation not required for your profile");
        }

        assessment.setConservativeExplanation(explanation);
        assessment.setExplanationReviewed(false);
        assessment.setGateStatus(GateStatus.UNDER_REVIEW);

        return assessmentRepo.save(assessment);
    }

    /**
     * Submit vasectomy verification (for pro-forced-birth males)
     */
    @Transactional
    public UserPoliticalAssessment submitVasectomyVerification(User user, MultipartFile document) throws Exception {
        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        if (assessment.getGateStatus() != GateStatus.PENDING_VASECTOMY) {
            throw new IllegalStateException("Vasectomy verification not required");
        }

        // Upload document to media service
        String documentUrl = uploadVerificationDocument(document);

        assessment.setVasectomyVerificationUrl(documentUrl);
        assessment.setVasectomyStatus(VasectomyStatus.VERIFICATION_PENDING);
        assessment.setGateStatus(GateStatus.UNDER_REVIEW);

        return assessmentRepo.save(assessment);
    }

    /**
     * Acknowledge vasectomy requirement (decline to verify)
     */
    @Transactional
    public UserPoliticalAssessment acknowledgeVasectomyRequirement(User user, boolean willVerify) {
        UserPoliticalAssessment assessment = getOrCreateAssessment(user);

        assessment.setAcknowledgedVasectomyRequirement(true);

        if (!willVerify) {
            assessment.setVasectomyStatus(VasectomyStatus.DECLINED);
            // Remains gated but acknowledged
        }

        return assessmentRepo.save(assessment);
    }

    /**
     * Admin review of conservative explanation
     */
    @Transactional
    public void reviewConservativeExplanation(UUID assessmentUuid, boolean approved, String notes) {
        UserPoliticalAssessment assessment = assessmentRepo.findByUuid(assessmentUuid)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));

        assessment.setExplanationReviewed(true);
        assessment.setReviewNotes(notes);

        if (approved) {
            assessment.setGateStatus(GateStatus.APPROVED);
        } else {
            assessment.setGateStatus(GateStatus.REJECTED);
            assessment.setRejectionReason(GateRejectionReason.UNEXPLAINED_CONSERVATIVE);
        }

        assessmentRepo.save(assessment);
    }

    /**
     * Admin verification of vasectomy
     */
    @Transactional
    public void verifyVasectomy(UUID assessmentUuid, boolean verified, String notes) {
        UserPoliticalAssessment assessment = assessmentRepo.findByUuid(assessmentUuid)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));

        assessment.setReviewNotes(notes);

        if (verified) {
            assessment.setVasectomyStatus(VasectomyStatus.VERIFIED);
            assessment.setVasectomyVerifiedAt(new Date());
            assessment.setGateStatus(GateStatus.APPROVED);
        } else {
            assessment.setVasectomyStatus(VasectomyStatus.NOT_VERIFIED);
            // Remains gated
        }

        assessmentRepo.save(assessment);
    }

    /**
     * Check if user can access matching features
     */
    public boolean canAccessMatching(User user) {
        Optional<UserPoliticalAssessment> assessment = assessmentRepo.findByUser(user);
        if (assessment.isEmpty()) {
            return false; // Must complete assessment
        }

        return assessment.get().getGateStatus() == GateStatus.APPROVED;
    }

    /**
     * Get gate status message for user
     */
    public String getGateStatusMessage(User user) {
        Optional<UserPoliticalAssessment> assessment = assessmentRepo.findByUser(user);
        if (assessment.isEmpty()) {
            return "Please complete the values assessment to access matching.";
        }

        return switch (assessment.get().getGateStatus()) {
            case PENDING_ASSESSMENT -> "Please complete the values assessment.";
            case APPROVED -> "Your profile is approved for matching.";
            case PENDING_EXPLANATION -> "Please explain your conservative values as a working-class person.";
            case PENDING_VASECTOMY -> "Vasectomy verification required for pro-forced-birth males.";
            case REJECTED -> "Your profile is not compatible with this platform's values.";
            case UNDER_REVIEW -> "Your profile is under review. Please check back later.";
        };
    }

    /**
     * Find compatible users based on economic values
     */
    public List<User> findEconomicallyCompatibleUsers(User user, double tolerance) {
        Optional<UserPoliticalAssessment> userAssessment = assessmentRepo.findByUser(user);
        if (userAssessment.isEmpty() || userAssessment.get().getEconomicValuesScore() == null) {
            return Collections.emptyList();
        }

        List<UserPoliticalAssessment> compatible = assessmentRepo.findCompatibleUsers(
            userAssessment.get().getEconomicValuesScore(),
            tolerance,
            user
        );

        return compatible.stream()
            .map(UserPoliticalAssessment::getUser)
            .toList();
    }

    /**
     * Get economic compatibility between two users
     */
    public double getEconomicCompatibility(User user1, User user2) {
        Optional<UserPoliticalAssessment> a1 = assessmentRepo.findByUser(user1);
        Optional<UserPoliticalAssessment> a2 = assessmentRepo.findByUser(user2);

        if (a1.isEmpty() || a2.isEmpty()) {
            return 50.0; // Neutral if not assessed
        }

        Double score1 = a1.get().getEconomicValuesScore();
        Double score2 = a2.get().getEconomicValuesScore();

        if (score1 == null || score2 == null) {
            return 50.0;
        }

        // Compatibility is inverse of difference
        double difference = Math.abs(score1 - score2);
        return 100.0 - difference;
    }

    /**
     * Get assessments pending review (for admin)
     */
    public Page<UserPoliticalAssessment> getPendingReviews(int page, int size) {
        return assessmentRepo.findByGateStatus(GateStatus.UNDER_REVIEW, PageRequest.of(page, size));
    }

    /**
     * Get statistics about assessment distribution
     */
    public Map<String, Object> getAssessmentStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalAssessments", assessmentRepo.count());
        stats.put("approved", assessmentRepo.countByGateStatus(GateStatus.APPROVED));
        stats.put("rejected", assessmentRepo.countRejected());
        stats.put("pendingReview", assessmentRepo.countByGateStatus(GateStatus.UNDER_REVIEW));
        stats.put("pendingExplanation", assessmentRepo.countByGateStatus(GateStatus.PENDING_EXPLANATION));
        stats.put("pendingVasectomy", assessmentRepo.countByGateStatus(GateStatus.PENDING_VASECTOMY));

        // Class/orientation distribution
        List<Object[]> distribution = assessmentRepo.getClassOrientationDistribution();
        stats.put("classOrientationDistribution", distribution);

        return stats;
    }

    // === Class Consciousness Questions ===

    /**
     * Get the class consciousness test questions
     */
    public List<Map<String, Object>> getClassConsciousnessQuestions() {
        List<Map<String, Object>> questions = new ArrayList<>();

        // Question 1: Understanding who benefits from tax cuts for wealthy
        questions.add(Map.of(
            "id", "taxCuts",
            "question", "When taxes on the wealthy are cut, who primarily benefits?",
            "options", List.of(
                Map.of("value", 1, "text", "Everyone benefits equally through trickle-down"),
                Map.of("value", 2, "text", "Small businesses benefit most"),
                Map.of("value", 3, "text", "Middle class workers benefit most"),
                Map.of("value", 4, "text", "Wealthy individuals and corporations benefit most"),
                Map.of("value", 5, "text", "The wealthy benefit while public services are cut")
            ),
            "correctAwareness", 5
        ));

        // Question 2: Understanding landlord-tenant relations
        questions.add(Map.of(
            "id", "rentRelations",
            "question", "In the landlord-tenant relationship, landlords' wealth comes from:",
            "options", List.of(
                Map.of("value", 1, "text", "Their hard work maintaining properties"),
                Map.of("value", 2, "text", "Smart investment decisions"),
                Map.of("value", 3, "text", "Taking risks with their capital"),
                Map.of("value", 4, "text", "Extracting portions of tenants' wages"),
                Map.of("value", 5, "text", "Owning a basic human need and charging for access")
            ),
            "correctAwareness", 5
        ));

        // Question 3: Understanding employer-employee relations
        questions.add(Map.of(
            "id", "employerRelations",
            "question", "Business profits are generated by:",
            "options", List.of(
                Map.of("value", 1, "text", "The genius and risk-taking of entrepreneurs"),
                Map.of("value", 2, "text", "Smart management decisions"),
                Map.of("value", 3, "text", "Market conditions and luck"),
                Map.of("value", 4, "text", "Workers producing more value than they're paid"),
                Map.of("value", 5, "text", "Extracting surplus value from workers' labor")
            ),
            "correctAwareness", 5
        ));

        // Question 4: Historical labor rights
        questions.add(Map.of(
            "id", "laborHistory",
            "question", "The 40-hour work week, weekends, and workplace safety regulations were achieved through:",
            "options", List.of(
                Map.of("value", 1, "text", "Generous employers who cared about workers"),
                Map.of("value", 2, "text", "Government officials acting independently"),
                Map.of("value", 3, "text", "Natural market evolution"),
                Map.of("value", 4, "text", "Labor unions and worker organizing"),
                Map.of("value", 5, "text", "Workers fighting, striking, and sometimes dying for rights")
            ),
            "correctAwareness", 5
        ));

        // Question 5: Class interests
        questions.add(Map.of(
            "id", "classInterests",
            "question", "Why might wealthy media owners promote the idea that 'anyone can become rich if they work hard enough'?",
            "options", List.of(
                Map.of("value", 1, "text", "Because it's simply true"),
                Map.of("value", 2, "text", "To inspire and motivate workers"),
                Map.of("value", 3, "text", "Because they believe in the American Dream"),
                Map.of("value", 4, "text", "To discourage workers from demanding better conditions"),
                Map.of("value", 5, "text", "To prevent class consciousness and collective action")
            ),
            "correctAwareness", 5
        ));

        return questions;
    }

    // === Private Helper Methods ===

    private double calculateClassConsciousnessScore(Map<String, Integer> answers) {
        if (answers == null || answers.isEmpty()) {
            return 0;
        }

        // Each question scores 0-5 for class awareness
        // Score is average across questions, scaled to 0-100
        double total = 0;
        int count = 0;

        for (Integer value : answers.values()) {
            if (value != null && value >= 1 && value <= 5) {
                total += value;
                count++;
            }
        }

        if (count == 0) return 0;

        // Scale 1-5 to 0-100
        return ((total / count) - 1) / 4.0 * 100;
    }

    private String uploadVerificationDocument(MultipartFile file) throws Exception {
        // Would upload to secure storage via media service
        // For now, return placeholder
        return mediaServiceUrl + "/secure/verification/" + UUID.randomUUID();
    }
}
