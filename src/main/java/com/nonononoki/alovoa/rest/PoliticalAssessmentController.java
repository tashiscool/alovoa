package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.*;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.PoliticalAssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for Political/Economic Assessment.
 * Handles assessment submission and gating logic.
 */
@RestController
@RequestMapping("/api/v1/political-assessment")
public class PoliticalAssessmentController {

    @Autowired
    private PoliticalAssessmentService assessmentService;

    @Autowired
    private AuthService authService;

    /**
     * Get current user's assessment status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getAssessmentStatus() {
        try {
            User user = authService.getCurrentUser(true);
            UserPoliticalAssessment assessment = assessmentService.getOrCreateAssessment(user);

            Map<String, Object> result = new HashMap<>();
            result.put("gateStatus", assessment.getGateStatus().name());
            result.put("statusMessage", assessmentService.getGateStatusMessage(user));
            result.put("canAccessMatching", assessmentService.canAccessMatching(user));
            result.put("assessmentComplete", assessment.getAssessmentCompletedAt() != null);

            // Include partial data if available
            if (assessment.getEconomicClass() != null) {
                result.put("economicClass", assessment.getEconomicClass().name());
            }
            if (assessment.getPoliticalOrientation() != null) {
                result.put("politicalOrientation", assessment.getPoliticalOrientation().name());
            }
            if (assessment.getEconomicValuesScore() != null) {
                result.put("economicValuesScore", assessment.getEconomicValuesScore());
            }
            if (assessment.getClassConsciousnessScore() != null) {
                result.put("classConsciousnessScore", assessment.getClassConsciousnessScore());
            }

            // Indicate what's needed
            if (assessment.getGateStatus() == GateStatus.PENDING_EXPLANATION) {
                result.put("needsExplanation", true);
            }
            if (assessment.getGateStatus() == GateStatus.PENDING_VASECTOMY) {
                result.put("needsVasectomy", true);
                result.put("acknowledgedRequirement", assessment.getAcknowledgedVasectomyRequirement());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit economic class assessment (step 1)
     */
    @PostMapping("/economic-class")
    public ResponseEntity<?> submitEconomicClass(@RequestBody Map<String, Object> request) {
        try {
            User user = authService.getCurrentUser(true);

            IncomeBracket incomeBracket = IncomeBracket.valueOf((String) request.get("incomeBracket"));
            IncomeSource incomeSource = IncomeSource.valueOf((String) request.get("incomeSource"));

            WealthBracket wealthBracket = null;
            if (request.containsKey("wealthBracket")) {
                wealthBracket = WealthBracket.valueOf((String) request.get("wealthBracket"));
            }

            Boolean ownsRental = (Boolean) request.get("ownsRentalProperties");
            Boolean employsOthers = (Boolean) request.get("employsOthers");
            Boolean livesOffCapital = (Boolean) request.get("livesOffCapital");

            UserPoliticalAssessment assessment = assessmentService.submitEconomicClass(
                user, incomeBracket, incomeSource, wealthBracket,
                ownsRental, employsOthers, livesOffCapital
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "economicClass", assessment.getEconomicClass().name(),
                "message", "Economic class determined: " + formatEnumName(assessment.getEconomicClass().name())
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit political values (step 2)
     */
    @PostMapping("/political-values")
    public ResponseEntity<?> submitPoliticalValues(@RequestBody Map<String, Object> request) {
        try {
            User user = authService.getCurrentUser(true);

            PoliticalOrientation orientation =
                PoliticalOrientation.valueOf((String) request.get("politicalOrientation"));

            Integer wealthRedist = getInteger(request, "wealthRedistributionView");
            Integer workerOwnership = getInteger(request, "workerOwnershipView");
            Integer universalServices = getInteger(request, "universalServicesView");
            Integer housingRights = getInteger(request, "housingRightsView");
            Integer billionaireView = getInteger(request, "billionaireExistenceView");
            Integer meritocracyView = getInteger(request, "meritocracyBeliefView");

            @SuppressWarnings("unchecked")
            Map<String, Object> additionalValues = (Map<String, Object>) request.get("additionalValues");

            UserPoliticalAssessment assessment = assessmentService.submitPoliticalValues(
                user, orientation, wealthRedist, workerOwnership, universalServices,
                housingRights, billionaireView, meritocracyView, additionalValues
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "economicValuesScore", assessment.getEconomicValuesScore() != null ?
                    assessment.getEconomicValuesScore() : 0
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit reproductive rights view (step 3)
     */
    @PostMapping("/reproductive-view")
    public ResponseEntity<?> submitReproductiveView(@RequestBody Map<String, String> request) {
        try {
            User user = authService.getCurrentUser(true);

            ReproductiveRightsView view =
                ReproductiveRightsView.valueOf(request.get("reproductiveRightsView"));

            UserPoliticalAssessment assessment = assessmentService.submitReproductiveView(user, view);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);

            if (assessment.getVasectomyStatus() == VasectomyStatus.NOT_VERIFIED) {
                response.put("vasectomyRequired", true);
                response.put("message", "Vasectomy verification required for your stated views");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get class consciousness test questions
     */
    @GetMapping("/class-consciousness-test")
    public ResponseEntity<?> getClassConsciousnessTest() {
        try {
            authService.getCurrentUser(true); // Verify authenticated
            List<Map<String, Object>> questions = assessmentService.getClassConsciousnessQuestions();
            return ResponseEntity.ok(Map.of("questions", questions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit class consciousness test answers
     */
    @PostMapping("/class-consciousness-test")
    public ResponseEntity<?> submitClassConsciousnessTest(@RequestBody Map<String, Integer> answers) {
        try {
            User user = authService.getCurrentUser(true);
            UserPoliticalAssessment assessment =
                assessmentService.submitClassConsciousnessTest(user, answers);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "score", assessment.getClassConsciousnessScore()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Complete the assessment and evaluate gates
     */
    @PostMapping("/complete")
    public ResponseEntity<?> completeAssessment() {
        try {
            User user = authService.getCurrentUser(true);
            UserPoliticalAssessment assessment = assessmentService.completeAssessment(user);

            Map<String, Object> result = new HashMap<>();
            result.put("gateStatus", assessment.getGateStatus().name());
            result.put("statusMessage", assessmentService.getGateStatusMessage(user));
            result.put("canAccessMatching", assessmentService.canAccessMatching(user));

            if (assessment.getGateStatus() == GateStatus.REJECTED) {
                result.put("rejectionReason", assessment.getRejectionReason() != null ?
                    assessment.getRejectionReason().name() : "POLICY_VIOLATION");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit conservative explanation (if required)
     */
    @PostMapping("/conservative-explanation")
    public ResponseEntity<?> submitConservativeExplanation(@RequestBody Map<String, String> request) {
        try {
            User user = authService.getCurrentUser(true);
            String explanation = request.get("explanation");

            if (explanation == null || explanation.trim().length() < 100) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Explanation must be at least 100 characters"
                ));
            }

            UserPoliticalAssessment assessment =
                assessmentService.submitConservativeExplanation(user, explanation);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "gateStatus", assessment.getGateStatus().name(),
                "message", "Explanation submitted for review"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Acknowledge vasectomy requirement
     */
    @PostMapping("/vasectomy/acknowledge")
    public ResponseEntity<?> acknowledgeVasectomyRequirement(@RequestBody Map<String, Boolean> request) {
        try {
            User user = authService.getCurrentUser(true);
            Boolean willVerify = request.get("willVerify");

            UserPoliticalAssessment assessment =
                assessmentService.acknowledgeVasectomyRequirement(user, willVerify != null && willVerify);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "acknowledged", true,
                "status", assessment.getVasectomyStatus().name()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit vasectomy verification document
     */
    @PostMapping("/vasectomy/verify")
    public ResponseEntity<?> submitVasectomyVerification(
            @RequestParam("document") MultipartFile document) {
        try {
            User user = authService.getCurrentUser(true);

            UserPoliticalAssessment assessment =
                assessmentService.submitVasectomyVerification(user, document);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "status", assessment.getVasectomyStatus().name(),
                "message", "Verification submitted for review"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get dropdown options for assessment forms
     */
    @GetMapping("/options")
    public ResponseEntity<?> getOptions() {
        Map<String, Object> options = new HashMap<>();

        // Income brackets
        options.put("incomeBrackets", Arrays.stream(IncomeBracket.values())
            .map(b -> Map.of("value", b.name(), "label", b.getDisplayName()))
            .collect(Collectors.toList()));

        // Income sources
        options.put("incomeSources", Arrays.stream(IncomeSource.values())
            .map(s -> Map.of("value", s.name(), "label", formatEnumName(s.name())))
            .collect(Collectors.toList()));

        // Wealth brackets
        options.put("wealthBrackets", Arrays.stream(WealthBracket.values())
            .map(b -> Map.of("value", b.name(), "label", b.getDisplayName()))
            .collect(Collectors.toList()));

        // Political orientations
        options.put("politicalOrientations", Arrays.stream(PoliticalOrientation.values())
            .map(o -> Map.of("value", o.name(), "label", formatEnumName(o.name())))
            .collect(Collectors.toList()));

        // Reproductive rights views
        options.put("reproductiveViews", Arrays.stream(ReproductiveRightsView.values())
            .map(v -> Map.of("value", v.name(), "label", formatEnumName(v.name())))
            .collect(Collectors.toList()));

        return ResponseEntity.ok(options);
    }

    // === Admin Endpoints ===

    /**
     * Get assessments pending review (admin only)
     */
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            Page<UserPoliticalAssessment> pending =
                assessmentService.getPendingReviews(page, size);

            return ResponseEntity.ok(Map.of(
                "assessments", pending.getContent().stream()
                    .map(this::mapAssessmentForAdmin)
                    .collect(Collectors.toList()),
                "totalPages", pending.getTotalPages(),
                "currentPage", page
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Review conservative explanation (admin only)
     */
    @PostMapping("/admin/review-explanation/{assessmentUuid}")
    public ResponseEntity<?> reviewExplanation(
            @PathVariable UUID assessmentUuid,
            @RequestBody Map<String, Object> request) {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            boolean approved = Boolean.TRUE.equals(request.get("approved"));
            String notes = (String) request.get("notes");

            assessmentService.reviewConservativeExplanation(assessmentUuid, approved, notes);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Verify vasectomy (admin only)
     */
    @PostMapping("/admin/verify-vasectomy/{assessmentUuid}")
    public ResponseEntity<?> verifyVasectomy(
            @PathVariable UUID assessmentUuid,
            @RequestBody Map<String, Object> request) {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            boolean verified = Boolean.TRUE.equals(request.get("verified"));
            String notes = (String) request.get("notes");

            assessmentService.verifyVasectomy(assessmentUuid, verified, notes);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get assessment statistics (admin only)
     */
    @GetMapping("/admin/statistics")
    public ResponseEntity<?> getStatistics() {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            return ResponseEntity.ok(assessmentService.getAssessmentStatistics());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // === Helper Methods ===

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Map<String, Object> mapAssessmentForAdmin(UserPoliticalAssessment assessment) {
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", assessment.getUuid().toString());
        result.put("userId", assessment.getUser().getId());
        result.put("userName", assessment.getUser().getFirstName());
        result.put("gateStatus", assessment.getGateStatus().name());
        result.put("economicClass", assessment.getEconomicClass() != null ?
            assessment.getEconomicClass().name() : null);
        result.put("politicalOrientation", assessment.getPoliticalOrientation() != null ?
            assessment.getPoliticalOrientation().name() : null);
        result.put("economicValuesScore", assessment.getEconomicValuesScore());
        result.put("classConsciousnessScore", assessment.getClassConsciousnessScore());

        if (assessment.getConservativeExplanation() != null) {
            result.put("conservativeExplanation", assessment.getConservativeExplanation());
        }

        if (assessment.getVasectomyStatus() != null &&
            assessment.getVasectomyStatus() != VasectomyStatus.NOT_APPLICABLE) {
            result.put("vasectomyStatus", assessment.getVasectomyStatus().name());
            result.put("vasectomyVerificationUrl", assessment.getVasectomyVerificationUrl());
        }

        result.put("createdAt", assessment.getCreatedAt());
        result.put("assessmentCompletedAt", assessment.getAssessmentCompletedAt());

        return result;
    }

    private String formatEnumName(String name) {
        return Arrays.stream(name.split("_"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }
}
