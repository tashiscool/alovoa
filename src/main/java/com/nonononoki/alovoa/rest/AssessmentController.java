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
            User matchUser = userRepository.findByUuid(UUID.fromString(userUuid))
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
            User matchUser = userRepository.findByUuid(UUID.fromString(userUuid))
                    .orElseThrow(() -> new Exception("User not found"));

            Map<String, Object> result = assessmentService.getMatchExplanation(currentUser, matchUser);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
