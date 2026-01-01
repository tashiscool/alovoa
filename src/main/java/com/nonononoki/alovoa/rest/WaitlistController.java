package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.WaitlistEntry;
import com.nonononoki.alovoa.entity.WaitlistEntry.*;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.WaitlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Public API for waitlist signups.
 * No authentication required.
 */
@RestController
@RequestMapping("/api/v1/waitlist")
@CrossOrigin(origins = "*") // Allow from landing page
public class WaitlistController {

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private AuthService authService;

    /**
     * Sign up for the waitlist.
     * Called from landing page.
     */
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest request) {
        try {
            // Parse enums
            Gender gender = parseGender(request.gender());
            Seeking seeking = parseSeeking(request.seeking());
            Location location = parseLocation(request.location());

            // Create entry
            WaitlistEntry entry = waitlistService.signup(
                    request.email(),
                    gender,
                    seeking,
                    location,
                    request.referralCode(),
                    request.source(),
                    request.utmSource(),
                    request.utmMedium(),
                    request.utmCampaign()
            );

            if (entry == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Unable to add to waitlist"
                ));
            }

            long position = waitlistService.getPositionInLine(entry);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "You're on the list!",
                    "inviteCode", entry.getInviteCode(),
                    "position", position,
                    "priorityNote", gender == Gender.WOMAN ? "Women get priority access!" : ""
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid input: " + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Something went wrong. Please try again."
            ));
        }
    }

    /**
     * Check waitlist status by email.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkStatus(@RequestParam String email) {
        Optional<WaitlistEntry> entry = waitlistService.getByEmail(email);

        if (entry.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "Email not found on waitlist"
            ));
        }

        WaitlistEntry e = entry.get();
        long position = waitlistService.getPositionInLine(e);
        long referrals = waitlistService.getReferralCount(e);

        return ResponseEntity.ok(Map.of(
                "found", true,
                "status", e.getStatus().name(),
                "position", position,
                "inviteCode", e.getInviteCode(),
                "referrals", referrals,
                "inviteCodesRemaining", e.getInviteCodesRemaining()
        ));
    }

    /**
     * Get public waitlist count (for landing page counter).
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getCount() {
        Map<String, Object> stats = waitlistService.getStats();
        return ResponseEntity.ok(Map.of(
                "total", stats.get("total"),
                "displayMessage", String.format("%,d people on the waitlist", (Long) stats.get("total"))
        ));
    }

    /**
     * Get full stats (admin only).
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        if (!user.isAdmin()) {
            throw new AlovoaException("not_authorized");
        }
        return ResponseEntity.ok(waitlistService.getStats());
    }

    // ============================================
    // Request DTOs
    // ============================================

    public record SignupRequest(
            String email,
            String gender,
            String seeking,
            String location,
            String referralCode,
            String source,
            String utmSource,
            String utmMedium,
            String utmCampaign
    ) {}

    // ============================================
    // Enum Parsers
    // ============================================

    private Gender parseGender(String gender) {
        if (gender == null) throw new IllegalArgumentException("Gender is required");
        return switch (gender.toLowerCase()) {
            case "woman", "female" -> Gender.WOMAN;
            case "man", "male" -> Gender.MAN;
            case "nonbinary", "non-binary" -> Gender.NONBINARY;
            default -> Gender.OTHER;
        };
    }

    private Seeking parseSeeking(String seeking) {
        if (seeking == null) throw new IllegalArgumentException("Seeking is required");
        return switch (seeking.toLowerCase()) {
            case "men", "male" -> Seeking.MEN;
            case "women", "female" -> Seeking.WOMEN;
            case "everyone", "all", "both" -> Seeking.EVERYONE;
            default -> Seeking.EVERYONE;
        };
    }

    private Location parseLocation(String location) {
        if (location == null) throw new IllegalArgumentException("Location is required");
        return switch (location.toLowerCase()) {
            case "dc", "washington" -> Location.DC;
            case "arlington" -> Location.ARLINGTON;
            case "alexandria" -> Location.ALEXANDRIA;
            case "nova-other", "nova" -> Location.NOVA_OTHER;
            case "maryland" -> Location.MARYLAND;
            default -> Location.OTHER;
        };
    }
}
