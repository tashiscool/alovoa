package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.VideoVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
@RequestMapping("/verification")
public class VerificationResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private VideoVerificationService videoVerificationService;

    /**
     * Main verification page
     */
    @GetMapping("")
    public String verification(Model model) throws AlovoaException {
        // Ensure user is authenticated
        authService.getCurrentUser(true);
        model.addAttribute("pageTitle", "Video Verification");
        return "verification";
    }

    /**
     * Start a new verification session
     */
    @PostMapping("/api/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startSession() {
        try {
            Map<String, Object> result = videoVerificationService.startVerificationSession();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Submit verification video
     */
    @PostMapping(value = "/api/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitVerification(
            @RequestParam("video") MultipartFile video,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "metadata", required = false) String metadata) {
        try {
            Map<String, Object> result = videoVerificationService.submitVerification(video, sessionId, metadata);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get current verification status
     */
    @GetMapping("/api/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> result = videoVerificationService.getVerificationStatus();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }
}
