package com.nonononoki.alovoa.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.model.VideoAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude-based AI provider for transcript analysis.
 * Note: Claude doesn't have native speech-to-text, so transcription
 * falls back to the local media service.
 */
@Service
@ConditionalOnProperty(name = "app.aura.ai.provider", havingValue = "claude")
public class ClaudeProvider implements AiAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeProvider.class);
    private static final String PROVIDER_NAME = "claude";

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Value("${app.aura.ai.claude.api-key:}")
    private String apiKey;

    @Value("${app.aura.ai.claude.model:claude-3-opus-20240229}")
    private String model;

    @Value("${app.aura.media-service.url:http://localhost:8001}")
    private String mediaServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ClaudeProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String transcribeVideo(byte[] videoData, String mimeType) throws AiProviderException {
        // Claude doesn't have native transcription - use media service
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Convert video to base64 for transport
            String base64Video = java.util.Base64.getEncoder().encodeToString(videoData);

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
                throw new AiProviderException(PROVIDER_NAME, "Transcription failed via media service");
            }

        } catch (Exception e) {
            LOGGER.error("Transcription failed", e);
            throw new AiProviderException(PROVIDER_NAME, "Transcription failed: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoAnalysisResult analyzeTranscript(String transcript) throws AiProviderException {
        if (!isAvailable()) {
            throw new AiProviderException(PROVIDER_NAME, "Claude API key not configured");
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = "Analyze the following video introduction transcript:\n\n" + transcript;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 1024);
            requestBody.put("system", systemPrompt);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", userPrompt)
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", ANTHROPIC_VERSION);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    CLAUDE_API_URL,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseAnalysisResponse(response.getBody());
            } else {
                throw new AiProviderException(PROVIDER_NAME, "Analysis failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            LOGGER.error("Claude analysis failed", e);
            throw new AiProviderException(PROVIDER_NAME, "Analysis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }

    private String buildSystemPrompt() {
        return """
            You are an AI assistant that analyzes dating app video introductions.
            Extract the following information from the transcript and respond ONLY with valid JSON:

            {
              "worldview_summary": "A 2-3 sentence summary of their values, beliefs, and outlook on life",
              "background_summary": "A 2-3 sentence summary of their background (education, career, where from)",
              "life_story_summary": "A 2-3 sentence narrative of their life journey and key experiences",
              "personality_indicators": {
                "confidence": 0.0-1.0,
                "warmth": 0.0-1.0,
                "humor": 0.0-1.0,
                "openness": 0.0-1.0,
                "authenticity": 0.0-1.0
              }
            }

            Be objective and extract only what is stated or clearly implied.
            If information is not available, indicate "Not mentioned in video".
            Respond with ONLY the JSON object, no other text.
            """;
    }

    @SuppressWarnings("unchecked")
    private VideoAnalysisResult parseAnalysisResponse(Map<String, Object> response) throws AiProviderException {
        try {
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content == null || content.isEmpty()) {
                throw new AiProviderException(PROVIDER_NAME, "No content in response");
            }

            String text = (String) content.get(0).get("text");

            // Extract JSON from response (Claude might include extra text)
            int jsonStart = text.indexOf('{');
            int jsonEnd = text.lastIndexOf('}') + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                text = text.substring(jsonStart, jsonEnd);
            }

            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);

            return VideoAnalysisResult.builder()
                    .worldviewSummary((String) parsed.get("worldview_summary"))
                    .backgroundSummary((String) parsed.get("background_summary"))
                    .lifeStorySummary((String) parsed.get("life_story_summary"))
                    .personalityIndicators((Map<String, Object>) parsed.get("personality_indicators"))
                    .providerName(PROVIDER_NAME)
                    .build();

        } catch (Exception e) {
            throw new AiProviderException(PROVIDER_NAME, "Failed to parse response: " + e.getMessage(), e);
        }
    }
}
