package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.AssessmentResponseDto;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.AssessmentService;
import com.nonononoki.alovoa.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/assessment")
public class AssessmentController {

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/questions/{category}")
    public ResponseEntity<?> getQuestionsByCategory(@PathVariable String category) {
        try {
            Map<String, Object> result = assessmentService.getQuestionsByCategory(category);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid category",
                    "validCategories", List.of("BIG_FIVE", "ATTACHMENT", "DEALBREAKER", "VALUES", "LIFESTYLE", "RED_FLAG")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<?> getAssessmentProgress() {
        try {
            Map<String, Object> result = assessmentService.getAssessmentProgress();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitResponses(@RequestBody List<AssessmentResponseDto> responses) {
        try {
            Map<String, Object> result = assessmentService.submitResponses(responses);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/results")
    public ResponseEntity<?> getAssessmentResults() {
        try {
            Map<String, Object> result = assessmentService.getAssessmentResults();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetAssessment(@RequestParam(required = false) String category) {
        try {
            Map<String, Object> result = assessmentService.resetAssessment(category);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid category",
                    "validCategories", List.of("BIG_FIVE", "ATTACHMENT", "DEALBREAKER", "VALUES", "LIFESTYLE", "RED_FLAG")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/admin/reload-questions")
    public ResponseEntity<?> reloadQuestions() {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }
            assessmentService.loadQuestionsFromJson();
            return ResponseEntity.ok(Map.of("success", true, "message", "Questions reloaded from JSON"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/match/{userUuid}")
    public ResponseEntity<?> calculateMatch(@PathVariable String userUuid) {
        try {
            User currentUser = authService.getCurrentUser(true);
            User matchUser = userRepository.findOptionalByUuid(UUID.fromString(userUuid))
                    .orElseThrow(() -> new Exception("User not found"));

            Map<String, Object> result = assessmentService.calculateOkCupidMatch(currentUser, matchUser);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/match/{userUuid}/explain")
    public ResponseEntity<?> getMatchExplanation(@PathVariable String userUuid) {
        try {
            User currentUser = authService.getCurrentUser(true);
            User matchUser = userRepository.findOptionalByUuid(UUID.fromString(userUuid))
                    .orElseThrow(() -> new Exception("User not found"));

            Map<String, Object> result = assessmentService.getMatchExplanation(currentUser, matchUser);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ============== Question Bank API Endpoints ==============

    /**
     * Get the next unanswered question for the current user.
     * Used for progressive questionnaire flow.
     */
    @GetMapping("/next")
    public ResponseEntity<?> getNextQuestion(@RequestParam(required = false) String category) {
        try {
            Map<String, Object> result = assessmentService.getNextQuestion(category);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid category",
                    "validCategories", List.of("BIG_FIVE", "ATTACHMENT", "DEALBREAKER", "VALUES", "LIFESTYLE", "RED_FLAG")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a batch of unanswered questions.
     * Useful for preloading questions in the UI.
     */
    @GetMapping("/batch")
    public ResponseEntity<?> getNextQuestionBatch(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            Map<String, Object> result = assessmentService.getNextUnansweredQuestions(category, Math.min(limit, 50));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid category",
                    "validCategories", List.of("BIG_FIVE", "ATTACHMENT", "DEALBREAKER", "VALUES", "LIFESTYLE", "RED_FLAG")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validate an answer before submitting.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateAnswer(@RequestBody AssessmentResponseDto response) {
        try {
            Map<String, Object> result = assessmentService.validateAnswer(
                    response.getQuestionId(),
                    response.getNumericResponse(),
                    response.getTextResponse()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit a single answer with validation.
     * Alternative to batch submit for progressive questionnaire.
     */
    @PostMapping("/answer")
    public ResponseEntity<?> submitSingleAnswer(@RequestBody AssessmentResponseDto response) {
        try {
            Map<String, Object> result = assessmentService.submitSingleAnswer(
                    response.getQuestionId(),
                    response.getNumericResponse(),
                    response.getTextResponse(),
                    response.getImportance()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get question bank statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getQuestionBankStats() {
        try {
            Map<String, Object> stats = new java.util.HashMap<>();

            // Get counts by category
            for (String category : List.of("BIG_FIVE", "ATTACHMENT", "DEALBREAKER", "VALUES", "LIFESTYLE", "RED_FLAG")) {
                try {
                    Map<String, Object> categoryData = assessmentService.getQuestionsByCategory(category);
                    stats.put(category, Map.of(
                            "totalQuestions", categoryData.get("totalQuestions"),
                            "answeredQuestions", categoryData.get("answeredQuestions"),
                            "subcategories", categoryData.get("subcategories")
                    ));
                } catch (Exception ignored) {
                    // Category might not exist
                }
            }

            // Get overall progress
            Map<String, Object> progress = assessmentService.getAssessmentProgress();
            stats.put("overallProgress", progress);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
