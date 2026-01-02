package com.nonononoki.alovoa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AuraConfig {

    @Value("${app.aura.media-service.url:http://localhost:8001}")
    private String mediaServiceUrl;

    @Value("${app.aura.ai-service.url:http://localhost:8002}")
    private String aiServiceUrl;

    @Value("${app.aura.daily-match.limit.default:5}")
    private int defaultDailyMatchLimit;

    @Value("${app.aura.daily-match.limit.verified:10}")
    private int verifiedDailyMatchLimit;

    @Value("${app.aura.reputation.initial-score:50.0}")
    private double initialReputationScore;

    @Value("${app.aura.video-verification.enabled:true}")
    private boolean videoVerificationEnabled;

    @Value("${app.aura.reputation.enabled:true}")
    private boolean reputationEnabled;

    @Value("${app.aura.daily-match.enabled:true}")
    private boolean dailyMatchEnabled;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return builder
                .requestFactory(() -> factory)
                .build();
    }

    @Bean
    public AuraProperties auraProperties() {
        return new AuraProperties(
                mediaServiceUrl,
                aiServiceUrl,
                defaultDailyMatchLimit,
                verifiedDailyMatchLimit,
                initialReputationScore,
                videoVerificationEnabled,
                reputationEnabled,
                dailyMatchEnabled
        );
    }

    public static class AuraProperties {
        private final String mediaServiceUrl;
        private final String aiServiceUrl;
        private final int defaultDailyMatchLimit;
        private final int verifiedDailyMatchLimit;
        private final double initialReputationScore;
        private final boolean videoVerificationEnabled;
        private final boolean reputationEnabled;
        private final boolean dailyMatchEnabled;

        public AuraProperties(
                String mediaServiceUrl,
                String aiServiceUrl,
                int defaultDailyMatchLimit,
                int verifiedDailyMatchLimit,
                double initialReputationScore,
                boolean videoVerificationEnabled,
                boolean reputationEnabled,
                boolean dailyMatchEnabled
        ) {
            this.mediaServiceUrl = mediaServiceUrl;
            this.aiServiceUrl = aiServiceUrl;
            this.defaultDailyMatchLimit = defaultDailyMatchLimit;
            this.verifiedDailyMatchLimit = verifiedDailyMatchLimit;
            this.initialReputationScore = initialReputationScore;
            this.videoVerificationEnabled = videoVerificationEnabled;
            this.reputationEnabled = reputationEnabled;
            this.dailyMatchEnabled = dailyMatchEnabled;
        }

        public String getMediaServiceUrl() {
            return mediaServiceUrl;
        }

        public String getAiServiceUrl() {
            return aiServiceUrl;
        }

        public int getDefaultDailyMatchLimit() {
            return defaultDailyMatchLimit;
        }

        public int getVerifiedDailyMatchLimit() {
            return verifiedDailyMatchLimit;
        }

        public double getInitialReputationScore() {
            return initialReputationScore;
        }

        public boolean isVideoVerificationEnabled() {
            return videoVerificationEnabled;
        }

        public boolean isReputationEnabled() {
            return reputationEnabled;
        }

        public boolean isDailyMatchEnabled() {
            return dailyMatchEnabled;
        }
    }
}
