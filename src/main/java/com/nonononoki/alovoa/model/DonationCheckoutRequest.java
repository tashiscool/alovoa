package com.nonononoki.alovoa.model;

import lombok.Data;

/**
 * Request DTO for creating a Stripe Checkout session for donations.
 */
@Data
public class DonationCheckoutRequest {

    /**
     * Donation amount in dollars (must be between MIN and MAX).
     */
    private Integer amount;

    /**
     * Optional prompt ID that triggered this donation.
     */
    private Long promptId;

    /**
     * Type of prompt that triggered this donation (AFTER_MATCH, MONTHLY, etc).
     */
    private String promptType;
}
