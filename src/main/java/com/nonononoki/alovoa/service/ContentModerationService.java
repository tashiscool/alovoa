package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ContentModerationEvent;
import com.nonononoki.alovoa.model.ModerationResult;
import com.nonononoki.alovoa.repo.ContentModerationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ContentModerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentModerationService.class);

    @Value("${app.moderation.enabled:true}")
    private boolean moderationEnabled;

    @Value("${app.moderation.api-key:}")
    private String perspectiveApiKey;

    @Value("${app.moderation.toxicity-threshold:0.7}")
    private double toxicityThreshold;

    @Value("${app.moderation.insult-threshold:0.8}")
    private double insultThreshold;

    @Value("${app.moderation.profanity-threshold:0.9}")
    private double profanityThreshold;

    @Autowired
    private ContentModerationEventRepository moderationEventRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    private Set<String> blockedWords;
    private Set<String> warningWords;
    private List<Pattern> blockedPatterns;

    @PostConstruct
    public void init() {
        loadKeywords();
    }

    private void loadKeywords() {
        try {
            ClassPathResource resource = new ClassPathResource("data/moderation-keywords.json");
            JsonNode root = objectMapper.readTree(resource.getInputStream());

            blockedWords = new HashSet<>();
            warningWords = new HashSet<>();
            blockedPatterns = new ArrayList<>();

            if (root.has("blockedWords")) {
                root.get("blockedWords").forEach(node -> blockedWords.add(node.asText().toLowerCase()));
            }

            if (root.has("warningWords")) {
                root.get("warningWords").forEach(node -> warningWords.add(node.asText().toLowerCase()));
            }

            if (root.has("blockedPatterns")) {
                root.get("blockedPatterns").forEach(node ->
                    blockedPatterns.add(Pattern.compile(node.asText(), Pattern.CASE_INSENSITIVE))
                );
            }

            LOGGER.info("Loaded moderation keywords: {} blocked words, {} warning words, {} patterns",
                    blockedWords.size(), warningWords.size(), blockedPatterns.size());
        } catch (IOException e) {
            LOGGER.warn("Failed to load moderation keywords, using empty lists", e);
            blockedWords = new HashSet<>();
            warningWords = new HashSet<>();
            blockedPatterns = new ArrayList<>();
        }
    }

    /**
     * Moderate content using Perspective API or fallback to keyword filter
     */
    public ModerationResult moderateContent(String content) {
        return moderateContent(content, null, null);
    }

    /**
     * Moderate content and log the event
     */
    public ModerationResult moderateContent(String content, User user, String contentType) {
        if (!moderationEnabled) {
            return ModerationResult.allowed();
        }

        if (content == null || content.trim().isEmpty()) {
            return ModerationResult.allowed();
        }

        ModerationResult result;

        // Try Perspective API first if configured
        if (perspectiveApiKey != null && !perspectiveApiKey.isBlank()) {
            try {
                result = moderateWithPerspectiveAPI(content);
            } catch (Exception e) {
                LOGGER.warn("Perspective API moderation failed, falling back to keyword filter", e);
                result = keywordFilter(content);
            }
        } else {
            // Use keyword-based filtering
            result = keywordFilter(content);
        }

        // Log moderation event if user and content type provided
        if (user != null && contentType != null) {
            logModerationEvent(user, contentType, result);
        }

        return result;
    }

    /**
     * Use Perspective API for moderation
     */
    private ModerationResult moderateWithPerspectiveAPI(String content) {
        String url = "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze?key=" + perspectiveApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> comment = new HashMap<>();
        comment.put("text", content);
        requestBody.put("comment", comment);

        Map<String, Object> requestedAttributes = new HashMap<>();
        requestedAttributes.put("TOXICITY", new HashMap<>());
        requestedAttributes.put("INSULT", new HashMap<>());
        requestedAttributes.put("PROFANITY", new HashMap<>());
        requestedAttributes.put("THREAT", new HashMap<>());
        requestedAttributes.put("IDENTITY_ATTACK", new HashMap<>());
        requestBody.put("requestedAttributes", requestedAttributes);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode attributeScores = root.path("attributeScores");

                double toxicityScore = attributeScores.path("TOXICITY").path("summaryScore").path("value").asDouble(0.0);
                double insultScore = attributeScores.path("INSULT").path("summaryScore").path("value").asDouble(0.0);
                double profanityScore = attributeScores.path("PROFANITY").path("summaryScore").path("value").asDouble(0.0);
                double threatScore = attributeScores.path("THREAT").path("summaryScore").path("value").asDouble(0.0);
                double identityAttackScore = attributeScores.path("IDENTITY_ATTACK").path("summaryScore").path("value").asDouble(0.0);

                List<String> flaggedCategories = new ArrayList<>();
                boolean blocked = false;

                if (toxicityScore >= toxicityThreshold) {
                    flaggedCategories.add("TOXICITY");
                    blocked = true;
                }
                if (insultScore >= insultThreshold) {
                    flaggedCategories.add("INSULT");
                    blocked = true;
                }
                if (profanityScore >= profanityThreshold) {
                    flaggedCategories.add("PROFANITY");
                    blocked = true;
                }
                if (threatScore >= 0.7) {
                    flaggedCategories.add("THREAT");
                    blocked = true;
                }
                if (identityAttackScore >= 0.7) {
                    flaggedCategories.add("IDENTITY_ATTACK");
                    blocked = true;
                }

                if (blocked) {
                    return ModerationResult.blocked(
                            toxicityScore,
                            flaggedCategories,
                            "Content flagged by automated moderation: " + String.join(", ", flaggedCategories)
                    );
                } else {
                    return ModerationResult.allowed();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error calling Perspective API", e);
            throw new RuntimeException("Perspective API error", e);
        }

        return ModerationResult.allowed();
    }

    /**
     * Keyword-based fallback filter
     */
    private ModerationResult keywordFilter(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ModerationResult.allowed();
        }

        String lowerContent = content.toLowerCase();
        List<String> flaggedCategories = new ArrayList<>();

        // Check blocked words
        for (String word : blockedWords) {
            if (lowerContent.contains(word)) {
                flaggedCategories.add("BLOCKED_WORD");
                return ModerationResult.blocked(
                        1.0,
                        flaggedCategories,
                        "Content contains prohibited language"
                );
            }
        }

        // Check blocked patterns
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(content).find()) {
                flaggedCategories.add("BLOCKED_PATTERN");
                return ModerationResult.blocked(
                        1.0,
                        flaggedCategories,
                        "Content matches prohibited pattern"
                );
            }
        }

        // Check warning words (log but don't block)
        for (String word : warningWords) {
            if (lowerContent.contains(word)) {
                flaggedCategories.add("WARNING_WORD");
                LOGGER.info("Content contains warning word: {}", word);
                // Don't block, just log
            }
        }

        return ModerationResult.allowed();
    }

    /**
     * Log moderation event to database
     */
    private void logModerationEvent(User user, String contentType, ModerationResult result) {
        try {
            ContentModerationEvent event = new ContentModerationEvent();
            event.setUser(user);
            event.setContentType(contentType);
            event.setToxicityScore(result.getToxicityScore());
            event.setFlaggedCategories(String.join(",", result.getFlaggedCategories()));
            event.setBlocked(!result.isAllowed());
            event.setCreatedAt(new Date());

            moderationEventRepo.save(event);
        } catch (Exception e) {
            LOGGER.error("Failed to log moderation event", e);
            // Don't fail the moderation check if logging fails
        }
    }

    /**
     * Get moderation statistics for a user
     */
    public long getBlockedContentCount(User user) {
        return moderationEventRepo.countByUserAndBlocked(user, true);
    }
}
