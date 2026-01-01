package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.StripeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Stripe webhooks and checkout sessions.
 *
 * Webhook endpoint: POST /api/v1/stripe/webhook
 * Configure in Stripe Dashboard: https://dashboard.stripe.com/webhooks
 *
 * Events to enable:
 * - checkout.session.completed
 * - payment_intent.succeeded
 * - charge.succeeded
 * - charge.refunded
 */
@RestController
@RequestMapping("/api/v1/stripe")
public class StripeWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeWebhookController.class);

    @Autowired
    private StripeService stripeService;

    @Autowired
    private AuthService authService;

    /**
     * Stripe webhook endpoint.
     * Must be publicly accessible (no auth).
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            stripeService.processWebhook(payload, sigHeader);
            return ResponseEntity.ok("Received");
        } catch (SecurityException e) {
            LOGGER.error("Webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        } catch (Exception e) {
            LOGGER.error("Webhook processing error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }
    }

    /**
     * Create a checkout session for donation.
     * Requires authentication.
     */
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> createCheckout(@RequestBody CheckoutRequest request) {
        try {
            if (!stripeService.isEnabled()) {
                return ResponseEntity.ok(Map.of(
                        "enabled", false,
                        "message", "Donations not yet configured"
                ));
            }

            // Get current user (optional)
            Long userId = null;
            try {
                var user = authService.getCurrentUser(false);
                if (user != null) {
                    userId = user.getId();
                }
            } catch (Exception e) {
                // Anonymous donation is OK
            }

            // Validate amount
            long amountCents = request.amountCents();
            if (amountCents < 100) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Minimum donation is $1"
                ));
            }
            if (amountCents > 100000) { // $1000 max
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Maximum donation is $1000"
                ));
            }

            Map<String, String> session = stripeService.createDonationSession(
                    userId, amountCents, request.promptId());

            return ResponseEntity.ok(Map.of(
                    "enabled", true,
                    "sessionId", session.get("sessionId"),
                    "url", session.get("url")
            ));

        } catch (Exception e) {
            LOGGER.error("Failed to create checkout session: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to create checkout session"
            ));
        }
    }

    /**
     * Get session details (for success page).
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        try {
            Map<String, Object> details = stripeService.getSessionDetails(sessionId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            LOGGER.error("Failed to get session details: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to get session"
            ));
        }
    }

    /**
     * Get Stripe public key (for frontend).
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "enabled", stripeService.isEnabled(),
                "publicKey", stripeService.getPublicKey() != null ? stripeService.getPublicKey() : "",
                "suggestedAmounts", new int[]{1000, 2500, 5000, 10000} // cents
        ));
    }

    // ============================================
    // Request DTOs
    // ============================================

    public record CheckoutRequest(
            long amountCents,
            Long promptId
    ) {}
}
