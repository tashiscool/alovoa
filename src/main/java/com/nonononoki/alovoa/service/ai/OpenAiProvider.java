package com.nonononoki.alovoa.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.model.VideoAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-based AI provider using Whisper for transcription and GPT-4 for analysis.
 */
@Service
@ConditionalOnProperty(name = "app.aura.ai.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiProvider implements AiAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String PROVIDER_NAME = "openai";

    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String CHAT_API_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${app.aura.ai.openai.api-key:}")
    private String apiKey;

    @Value("${app.aura.ai.openai.model:gpt-4-turbo}")
    private String chatModel;

    @Value("${app.aura.ai.openai.whisper-model:whisper-1}")
    private String whisperModel;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String transcribeVideo(byte[] videoData, String mimeType) throws AiProviderException {
        if (!isAvailable()) {
            throw new AiProviderException(PROVIDER_NAME, "OpenAI API key not configured");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Create multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(videoData) {
                @Override
                public String getFilename() {
                    return "video." + getExtension(mimeType);
                }
            });
            body.add("model", whisperModel);
            body.add("response_format", "text");

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    WHISPER_API_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().trim();
            } else {
                throw new AiProviderException(PROVIDER_NAME, "Transcription failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            LOGGER.error("OpenAI transcription failed", e);
            throw new AiProviderException(PROVIDER_NAME, "Transcription failed: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoAnalysisResult analyzeTranscript(String transcript) throws AiProviderException {
        if (!isAvailable()) {
            throw new AiProviderException(PROVIDER_NAME, "OpenAI API key not configured");
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = "Analyze the following video introduction transcript:\n\n" + transcript;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", chatModel);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("temperature", 0.7);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    CHAT_API_URL,
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
            LOGGER.error("OpenAI analysis failed", e);
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
            Extract the following information from the transcript and respond in JSON format:

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
            """;
    }

    @SuppressWarnings("unchecked")
    private VideoAnalysisResult parseAnalysisResponse(Map<String, Object> response) throws AiProviderException {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiProviderException(PROVIDER_NAME, "No choices in response");
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            Map<String, Object> parsed = objectMapper.readValue(content, Map.class);

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

    private String getExtension(String mimeType) {
        return switch (mimeType) {
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            case "audio/mpeg" -> "mp3";
            case "audio/wav" -> "wav";
            default -> "mp4";
        };
    }
}
