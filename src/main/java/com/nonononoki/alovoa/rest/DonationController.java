package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.User.DonationTier;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.DonationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST controller for donation-only monetization.
 *
 * All features work without donating.
 * This handles:
 * - Donation info/status
 * - Recording donations
 * - Dismissing prompts
 * - Thank you messages
 */
@RestController
@RequestMapping("/api/v1/donation")
public class DonationController {

    @Autowired
    private DonationService donationService;

    @Autowired
    private AuthService authService;

    /**
     * Get current user's donation info.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getDonationInfo() {
        return ResponseEntity.ok(donationService.getCurrentUserDonationInfo());
    }

    /**
     * Record a donation from current user.
     * Called after successful payment via Stripe/PayPal webhook or manual entry.
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, String>> recordDonation(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "promptId", required = false) Long promptId) {

        donationService.recordCurrentUserDonation(amount, promptId);

        // Get thank you message based on new tier
        Map<String, Object> info = donationService.getCurrentUserDonationInfo();
        DonationTier tier = (DonationTier) info.get("tier");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", donationService.getThankYouMessage(tier),
                "tier", tier.name()
        ));
    }

    /**
     * Dismiss a donation prompt.
     */
    @PostMapping("/dismiss/{promptId}")
    public ResponseEntity<Map<String, String>> dismissPrompt(@PathVariable Long promptId) {
        donationService.dismissPrompt(promptId);
        return ResponseEntity.ok(Map.of("status", "dismissed"));
    }

    /**
     * Get thank you message for a tier.
     */
    @GetMapping("/thank-you/{tier}")
    public ResponseEntity<Map<String, String>> getThankYouMessage(@PathVariable String tier) {
        try {
            DonationTier donationTier = DonationTier.valueOf(tier.toUpperCase());
            return ResponseEntity.ok(Map.of("message", donationService.getThankYouMessage(donationTier)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid tier"));
        }
    }

    /**
     * Get donation stats (admin only).
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDonationStats() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        if (!user.isAdmin()) {
            throw new AlovoaException("not_authorized");
        }
        return ResponseEntity.ok(donationService.getDonationStats());
    }

    /**
     * Get the relationship exit message and trigger the prompt.
     * Called when user marks relationship status as "in a relationship".
     */
    @GetMapping("/relationship-exit")
    public ResponseEntity<Map<String, Object>> getRelationshipExitPrompt() {
        return ResponseEntity.ok(Map.of(
                "message", donationService.getRelationshipExitMessage(),
                "amounts", new int[]{10, 25, 50, 100},
                "headline", "Looks like AURA worked!"
        ));
    }
}
