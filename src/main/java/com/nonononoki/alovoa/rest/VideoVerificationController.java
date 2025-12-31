package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.service.VideoVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/video")
public class VideoVerificationController {

    @Autowired
    private VideoVerificationService videoVerificationService;

    @PostMapping("/intro/upload")
    public ResponseEntity<?> uploadIntroVideo(@RequestParam("video") MultipartFile video) {
        try {
            Map<String, Object> result = videoVerificationService.uploadIntroVideo(video);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verification/start")
    public ResponseEntity<?> startVerification() {
        try {
            Map<String, Object> result = videoVerificationService.startVerificationSession();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verification/submit")
    public ResponseEntity<?> submitVerification(
            @RequestParam("video") MultipartFile video,
            @RequestParam("sessionId") String sessionId) {
        try {
            Map<String, Object> result = videoVerificationService.submitVerification(video, sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/verification/status")
    public ResponseEntity<?> getVerificationStatus() {
        try {
            Map<String, Object> result = videoVerificationService.getVerificationStatus();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
