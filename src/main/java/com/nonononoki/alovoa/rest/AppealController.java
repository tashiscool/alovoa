package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAppeal;
import com.nonononoki.alovoa.entity.user.UserAppeal.AppealStatus;
import com.nonononoki.alovoa.entity.user.UserAppeal.AppealType;
import com.nonononoki.alovoa.entity.user.UserAppeal.AppealOutcome;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.ReputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for the Appeals Mechanism.
 * Part of the AURA Anti-Radicalization system.
 *
 * Allows RESTRICTED users to appeal their status and provides admins
 * with tools to review and process appeals.
 */
@RestController
@RequestMapping("/api/v1/appeals")
public class AppealController {

    @Autowired
    private ReputationService reputationService;

    @Autowired
    private AuthService authService;

    /**
     * Submit a new appeal.
     * Users can only submit if they have RESTRICTED status and haven't appealed in 30 days.
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitAppeal(@RequestBody AppealRequest request) {
        try {
            User user = authService.getCurrentUser(true);

            UserAppeal appeal = reputationService.submitAppeal(
                    user,
                    request.getAppealType(),
                    request.getAppealReason(),
                    request.getSupportingStatement(),
                    request.getLinkedReportUuid()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "appealUuid", appeal.getUuid().toString(),
                    "status", appeal.getStatus().name(),
                    "message", "Your appeal has been submitted and will be reviewed within 30 days."
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "code", "APPEAL_NOT_ALLOWED"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user's appeal eligibility and status.
     */
    @GetMapping("/eligibility")
    public ResponseEntity<?> getEligibility() {
        try {
            User user = authService.getCurrentUser(true);
            Map<String, Object> eligibility = reputationService.getAppealEligibility(user);
            return ResponseEntity.ok(eligibility);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user's own appeals.
     */
    @GetMapping("/my-appeals")
    public ResponseEntity<?> getMyAppeals() {
        try {
            User user = authService.getCurrentUser(true);
            List<UserAppeal> appeals = reputationService.getUserAppeals(user);

            List<Map<String, Object>> appealList = appeals.stream()
                    .map(this::mapAppealToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("appeals", appealList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Withdraw a pending appeal.
     */
    @PostMapping("/{uuid}/withdraw")
    public ResponseEntity<?> withdrawAppeal(@PathVariable String uuid) {
        try {
            User user = authService.getCurrentUser(true);
            reputationService.withdrawAppeal(user, UUID.fromString(uuid));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Appeal withdrawn successfully."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // === Admin Endpoints ===

    /**
     * Get pending appeals for admin review.
     */
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            User admin = authService.getCurrentUser(true);
            if (!admin.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            Page<UserAppeal> appeals = reputationService.getPendingAppeals(page, size);

            List<Map<String, Object>> appealList = appeals.getContent().stream()
                    .map(this::mapAppealToAdminResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "appeals", appealList,
                    "page", page,
                    "totalPages", appeals.getTotalPages(),
                    "totalElements", appeals.getTotalElements()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Review and process an appeal (admin only).
     */
    @PostMapping("/admin/{uuid}/review")
    public ResponseEntity<?> reviewAppeal(
            @PathVariable String uuid,
            @RequestBody ReviewRequest request) {
        try {
            User admin = authService.getCurrentUser(true);
            if (!admin.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            reputationService.processAppeal(
                    UUID.fromString(uuid),
                    admin,
                    request.isApproved(),
                    request.getOutcome(),
                    request.getReviewNotes()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", request.isApproved() ? "Appeal approved" : "Appeal denied"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get appeal details (admin only).
     */
    @GetMapping("/admin/{uuid}")
    public ResponseEntity<?> getAppealDetails(@PathVariable String uuid) {
        try {
            User admin = authService.getCurrentUser(true);
            if (!admin.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            UserAppeal appeal = reputationService.getAppealByUuid(UUID.fromString(uuid));
            if (appeal == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(mapAppealToAdminResponse(appeal));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // === Helper Methods ===

    private Map<String, Object> mapAppealToResponse(UserAppeal appeal) {
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", appeal.getUuid().toString());
        result.put("type", appeal.getAppealType().name());
        result.put("status", appeal.getStatus().name());
        result.put("reason", appeal.getAppealReason());
        result.put("createdAt", appeal.getCreatedAt());

        if (appeal.getOutcome() != null) {
            result.put("outcome", appeal.getOutcome().name());
        }
        if (appeal.getReviewedAt() != null) {
            result.put("reviewedAt", appeal.getReviewedAt());
        }
        if (appeal.getProbationEndDate() != null) {
            result.put("probationEndDate", appeal.getProbationEndDate());
        }

        return result;
    }

    private Map<String, Object> mapAppealToAdminResponse(UserAppeal appeal) {
        Map<String, Object> result = mapAppealToResponse(appeal);

        // Add admin-only fields
        result.put("supportingStatement", appeal.getSupportingStatement());
        result.put("userId", appeal.getUser().getId());
        result.put("userUuid", appeal.getUser().getUuid().toString());
        result.put("userEmail", appeal.getUser().getEmail());

        if (appeal.getLinkedReport() != null) {
            result.put("linkedReportUuid", appeal.getLinkedReport().getUuid().toString());
        }
        if (appeal.getReviewNotes() != null) {
            result.put("reviewNotes", appeal.getReviewNotes());
        }
        if (appeal.getReviewedBy() != null) {
            result.put("reviewedBy", appeal.getReviewedBy().getId());
        }

        return result;
    }

    // === Request DTOs ===

    public static class AppealRequest {
        private AppealType appealType;
        private String appealReason;
        private String supportingStatement;
        private String linkedReportUuid;

        public AppealType getAppealType() { return appealType; }
        public void setAppealType(AppealType appealType) { this.appealType = appealType; }
        public String getAppealReason() { return appealReason; }
        public void setAppealReason(String appealReason) { this.appealReason = appealReason; }
        public String getSupportingStatement() { return supportingStatement; }
        public void setSupportingStatement(String supportingStatement) { this.supportingStatement = supportingStatement; }
        public String getLinkedReportUuid() { return linkedReportUuid; }
        public void setLinkedReportUuid(String linkedReportUuid) { this.linkedReportUuid = linkedReportUuid; }
    }

    public static class ReviewRequest {
        private boolean approved;
        private AppealOutcome outcome;
        private String reviewNotes;

        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        public AppealOutcome getOutcome() { return outcome; }
        public void setOutcome(AppealOutcome outcome) { this.outcome = outcome; }
        public String getReviewNotes() { return reviewNotes; }
        public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    }
}
