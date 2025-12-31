package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.service.VideoDateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/video-date")
public class VideoDateController {

    @Autowired
    private VideoDateService videoDateService;

    @PostMapping("/propose")
    public ResponseEntity<?> proposeVideoDate(
            @RequestParam Long conversationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date proposedTime) {
        try {
            Map<String, Object> result = videoDateService.proposeVideoDate(conversationId, proposedTime);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<?> respondToProposal(
            @PathVariable Long id,
            @RequestParam boolean accept,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date counterTime) {
        try {
            Map<String, Object> result = videoDateService.respondToProposal(id, accept, counterTime);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> startVideoDate(@PathVariable Long id) {
        try {
            Map<String, Object> result = videoDateService.startVideoDate(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<?> endVideoDate(@PathVariable Long id) {
        try {
            Map<String, Object> result = videoDateService.endVideoDate(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<?> submitFeedback(
            @PathVariable Long id,
            @RequestBody Map<String, Object> feedback) {
        try {
            Map<String, Object> result = videoDateService.submitFeedback(id, feedback);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingDates() {
        try {
            Map<String, Object> result = videoDateService.getUpcomingDates();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/proposals")
    public ResponseEntity<?> getPendingProposals() {
        try {
            Map<String, Object> result = videoDateService.getPendingProposals();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getDateHistory() {
        try {
            Map<String, Object> result = videoDateService.getDateHistory();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
