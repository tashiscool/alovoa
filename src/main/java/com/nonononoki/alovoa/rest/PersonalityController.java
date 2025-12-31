package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.model.PersonalityAssessmentDto;
import com.nonononoki.alovoa.service.PersonalityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/personality")
public class PersonalityController {

    @Autowired
    private PersonalityService personalityService;

    @GetMapping("/assessment")
    public ResponseEntity<?> getAssessmentQuestions() {
        try {
            Map<String, Object> result = personalityService.getAssessmentQuestions();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/assessment/submit")
    public ResponseEntity<?> submitAssessment(@RequestBody PersonalityAssessmentDto answers) {
        try {
            Map<String, Object> result = personalityService.submitAssessment(answers);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/results")
    public ResponseEntity<?> getPersonalityResults() {
        try {
            Map<String, Object> result = personalityService.getPersonalityResults();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/assessment/retake")
    public ResponseEntity<?> retakeAssessment() {
        try {
            Map<String, Object> result = personalityService.retakeAssessment();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
