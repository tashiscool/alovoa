package com.nonononoki.alovoa.service.ai;

/**
 * Exception thrown when an AI provider operation fails.
 */
public class AiProviderException extends Exception {

    private final String providerName;

    public AiProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public AiProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
