package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.service.MatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

    @Autowired
    private MatchingService matchingService;

    @GetMapping("/daily")
    public ResponseEntity<?> getDailyMatches() {
        try {
            Map<String, Object> result = matchingService.getDailyMatches();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/compatibility/{matchUuid}")
    public ResponseEntity<?> getCompatibilityExplanation(@PathVariable String matchUuid) {
        try {
            Map<String, Object> result = matchingService.getCompatibilityExplanation(matchUuid);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
