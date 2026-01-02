package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ReportEvidence;
import com.nonononoki.alovoa.entity.user.ReportEvidence.EvidenceType;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport.*;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.AccountabilityService;
import com.nonononoki.alovoa.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for Public Accountability System.
 * Handles report submission, evidence upload, and feedback display.
 */
@RestController
@RequestMapping("/api/v1/accountability")
public class AccountabilityController {

    @Autowired
    private AccountabilityService accountabilityService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepo;

    /**
     * Submit a new accountability report
     */
    @PostMapping("/report")
    public ResponseEntity<?> submitReport(@RequestBody Map<String, Object> request) {
        try {
            User reporter = authService.getCurrentUser(true);

            UUID subjectUuid = UUID.fromString((String) request.get("subjectUuid"));
            User subject = userRepo.findOptionalByUuid(subjectUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            AccountabilityCategory category =
                AccountabilityCategory.valueOf((String) request.get("category"));

            BehaviorType behaviorType = null;
            if (request.containsKey("behaviorType")) {
                behaviorType = BehaviorType.valueOf((String) request.get("behaviorType"));
            }

            String title = (String) request.get("title");
            String description = (String) request.get("description");
            boolean anonymous = Boolean.TRUE.equals(request.get("anonymous"));

            Long conversationId = null;
            if (request.containsKey("conversationId")) {
                conversationId = ((Number) request.get("conversationId")).longValue();
            }

            UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, category, behaviorType, title, description,
                anonymous, conversationId
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "reportUuid", report.getUuid().toString(),
                "message", "Report submitted. Please add evidence to support your report."
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload evidence for a report
     */
    @PostMapping("/report/{reportUuid}/evidence")
    public ResponseEntity<?> uploadEvidence(
            @PathVariable UUID reportUuid,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String evidenceType,
            @RequestParam(value = "caption", required = false) String caption) {
        try {
            User uploader = authService.getCurrentUser(true);

            ReportEvidence evidence = accountabilityService.uploadEvidence(
                reportUuid,
                file,
                EvidenceType.valueOf(evidenceType),
                caption,
                uploader
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "evidenceUuid", evidence.getUuid().toString(),
                "message", "Evidence uploaded successfully."
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get my submitted reports
     */
    @GetMapping("/reports/submitted")
    public ResponseEntity<?> getMySubmittedReports() {
        try {
            User user = authService.getCurrentUser(true);
            List<UserAccountabilityReport> reports =
                user.getAccountabilityReportsSubmitted();

            List<Map<String, Object>> result = reports.stream()
                .map(this::mapReportToResponse)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("reports", result));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get reports about me
     */
    @GetMapping("/reports/received")
    public ResponseEntity<?> getMyReceivedReports() {
        try {
            User user = authService.getCurrentUser(true);
            List<UserAccountabilityReport> reports =
                user.getAccountabilityReportsReceived();

            List<Map<String, Object>> result = reports.stream()
                .filter(r -> r.getStatus() == ReportStatus.VERIFIED ||
                            r.getStatus() == ReportStatus.PUBLISHED)
                .map(this::mapReportToResponse)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("reports", result));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Respond to a report about me
     */
    @PostMapping("/report/{reportUuid}/respond")
    public ResponseEntity<?> respondToReport(
            @PathVariable UUID reportUuid,
            @RequestBody Map<String, String> request) {
        try {
            User subject = authService.getCurrentUser(true);
            String response = request.get("response");

            accountabilityService.submitSubjectResponse(reportUuid, subject, response);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Response submitted."
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retract a report I submitted
     */
    @PostMapping("/report/{reportUuid}/retract")
    public ResponseEntity<?> retractReport(@PathVariable UUID reportUuid) {
        try {
            User reporter = authService.getCurrentUser(true);

            accountabilityService.retractReport(reportUuid, reporter);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Report retracted."
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get public feedback for a user's profile
     */
    @GetMapping("/feedback/{userUuid}")
    public ResponseEntity<?> getUserFeedback(@PathVariable UUID userUuid) {
        try {
            User viewer = authService.getCurrentUser(true);
            User subject = userRepo.findOptionalByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            List<UserAccountabilityReport> feedback =
                accountabilityService.getPublicFeedback(subject, viewer);

            // Get summary
            Map<String, Object> summary = accountabilityService.getFeedbackSummary(subject);

            // Map reports
            List<Map<String, Object>> reports = feedback.stream()
                .map(r -> mapReportForPublic(r))
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "summary", summary,
                "feedback", reports
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Mark a report as helpful
     */
    @PostMapping("/report/{reportUuid}/helpful")
    public ResponseEntity<?> markHelpful(@PathVariable UUID reportUuid) {
        try {
            User user = authService.getCurrentUser(true);
            accountabilityService.markHelpful(reportUuid, user);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Flag a report as potentially false
     */
    @PostMapping("/report/{reportUuid}/flag")
    public ResponseEntity<?> flagReport(@PathVariable UUID reportUuid) {
        try {
            User user = authService.getCurrentUser(true);
            accountabilityService.flagReport(reportUuid, user);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get available report categories
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();

        for (AccountabilityCategory category : AccountabilityCategory.values()) {
            List<Map<String, String>> behaviors = new ArrayList<>();

            // Add relevant behavior types for each category
            for (BehaviorType behavior : BehaviorType.values()) {
                if (behaviorBelongsToCategory(behavior, category)) {
                    behaviors.add(Map.of(
                        "value", behavior.name(),
                        "label", formatEnumName(behavior.name())
                    ));
                }
            }

            categories.add(Map.of(
                "value", category.name(),
                "label", formatEnumName(category.name()),
                "isPositive", category == AccountabilityCategory.POSITIVE_EXPERIENCE,
                "behaviors", behaviors
            ));
        }

        return ResponseEntity.ok(Map.of("categories", categories));
    }

    // === Admin Endpoints ===

    /**
     * Get pending reports for review (admin only)
     */
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            Page<UserAccountabilityReport> pending =
                accountabilityService.getPendingReports(page, size);

            return ResponseEntity.ok(Map.of(
                "reports", pending.getContent().stream()
                    .map(this::mapReportToResponse)
                    .collect(Collectors.toList()),
                "totalPages", pending.getTotalPages(),
                "currentPage", page
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Verify a report (admin only)
     */
    @PostMapping("/admin/verify/{reportUuid}")
    public ResponseEntity<?> verifyReport(
            @PathVariable UUID reportUuid,
            @RequestBody Map<String, Object> request) {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            boolean verified = Boolean.TRUE.equals(request.get("verified"));
            String notes = (String) request.get("notes");

            accountabilityService.verifyReport(reportUuid, verified, notes);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Publish a verified report (admin only)
     */
    @PostMapping("/admin/publish/{reportUuid}")
    public ResponseEntity<?> publishReport(
            @PathVariable UUID reportUuid,
            @RequestBody Map<String, String> request) {
        try {
            User user = authService.getCurrentUser(true);
            if (!user.isAdmin()) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            ReportVisibility visibility =
                ReportVisibility.valueOf(request.getOrDefault("visibility", "PUBLIC"));

            accountabilityService.publishReport(reportUuid, visibility);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // === Helper Methods ===

    private Map<String, Object> mapReportToResponse(UserAccountabilityReport report) {
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", report.getUuid().toString());
        result.put("category", report.getCategory().name());
        if (report.getBehaviorType() != null) {
            result.put("behaviorType", report.getBehaviorType().name());
        }
        result.put("title", report.getTitle());
        result.put("description", report.getDescription());
        result.put("status", report.getStatus().name());
        result.put("createdAt", report.getCreatedAt());
        result.put("evidenceVerified", report.isEvidenceVerified());
        result.put("helpfulCount", report.getHelpfulCount());

        if (report.getSubjectResponse() != null) {
            result.put("subjectResponse", report.getSubjectResponse());
            result.put("subjectResponseDate", report.getSubjectResponseDate());
        }

        return result;
    }

    private Map<String, Object> mapReportForPublic(UserAccountabilityReport report) {
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", report.getUuid().toString());
        result.put("category", report.getCategory().name());
        result.put("categoryLabel", formatEnumName(report.getCategory().name()));
        if (report.getBehaviorType() != null) {
            result.put("behaviorType", report.getBehaviorType().name());
            result.put("behaviorLabel", formatEnumName(report.getBehaviorType().name()));
        }
        result.put("title", report.getTitle());
        result.put("description", report.getDescription());
        result.put("createdAt", report.getCreatedAt());
        result.put("helpfulCount", report.getHelpfulCount());
        result.put("evidenceVerified", report.isEvidenceVerified());
        result.put("isPositive", report.getCategory() == AccountabilityCategory.POSITIVE_EXPERIENCE);

        // Only show reporter name if not anonymous
        if (!report.isAnonymous()) {
            result.put("reporterName", report.getReporter().getFirstName());
        }

        if (report.getSubjectResponse() != null) {
            result.put("subjectResponse", report.getSubjectResponse());
        }

        return result;
    }

    private boolean behaviorBelongsToCategory(BehaviorType behavior, AccountabilityCategory category) {
        return switch (category) {
            case GHOSTING -> behavior.name().startsWith("UNMATCHED") ||
                            behavior.name().startsWith("STOPPED") ||
                            behavior.name().startsWith("STOOD");
            case DISHONESTY -> behavior.name().startsWith("FAKE") ||
                              behavior.name().startsWith("LIED") ||
                              behavior.name().startsWith("MISREPRESENTED");
            case DISRESPECT -> behavior.name().startsWith("INSULTS") ||
                              behavior.name().contains("SHAMING") ||
                              behavior.name().startsWith("CRUDE");
            case HARASSMENT -> behavior.name().startsWith("SPAM") ||
                              behavior.name().contains("OUTSIDE") ||
                              behavior.name().contains("REFUSED");
            case MANIPULATION -> behavior.name().contains("BOMB") ||
                                behavior.name().contains("GASLIGHT") ||
                                behavior.name().contains("GUILT");
            case POSITIVE_EXPERIENCE -> behavior.name().startsWith("RESPECTFUL") ||
                                       behavior.name().startsWith("HONEST") ||
                                       behavior.name().startsWith("GRACEFUL") ||
                                       behavior.name().startsWith("GREAT");
            default -> false;
        };
    }

    private String formatEnumName(String name) {
        return Arrays.stream(name.split("_"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }
}
