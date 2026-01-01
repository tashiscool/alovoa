package com.nonononoki.alovoa.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.model.VideoAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

/**
 * Local media service provider that uses the Python media-service
 * for both transcription and analysis (no external API costs).
 */
@Service
@ConditionalOnProperty(name = "app.aura.ai.provider", havingValue = "local")
public class LocalMediaServiceProvider implements AiAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalMediaServiceProvider.class);
    private static final String PROVIDER_NAME = "local";

    @Value("${app.aura.media-service.url:http://localhost:8001}")
    private String mediaServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LocalMediaServiceProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String transcribeVideo(byte[] videoData, String mimeType) throws AiProviderException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String base64Video = Base64.getEncoder().encodeToString(videoData);

            Map<String, Object> request = Map.of(
                    "video_data", base64Video,
                    "mime_type", mimeType
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    mediaServiceUrl + "/video/transcribe",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("transcript");
            } else {
                throw new AiProviderException(PROVIDER_NAME, "Transcription failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            LOGGER.error("Local transcription failed", e);
            throw new AiProviderException(PROVIDER_NAME, "Transcription failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public VideoAnalysisResult analyzeTranscript(String transcript) throws AiProviderException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = Map.of(
                    "transcript", transcript,
                    "extract_worldview", true,
                    "extract_background", true,
                    "extract_lifestory", true,
                    "extract_personality", true
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    mediaServiceUrl + "/video/analyze-transcript",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                return VideoAnalysisResult.builder()
                        .worldviewSummary((String) body.get("worldview_summary"))
                        .backgroundSummary((String) body.get("background_summary"))
                        .lifeStorySummary((String) body.get("life_story_summary"))
                        .personalityIndicators((Map<String, Object>) body.get("personality_indicators"))
                        .providerName(PROVIDER_NAME)
                        .build();
            } else {
                throw new AiProviderException(PROVIDER_NAME, "Analysis failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            LOGGER.error("Local analysis failed", e);
            throw new AiProviderException(PROVIDER_NAME, "Analysis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    mediaServiceUrl + "/health",
                    Map.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            LOGGER.warn("Media service not available: {}", e.getMessage());
            return false;
        }
    }
}
