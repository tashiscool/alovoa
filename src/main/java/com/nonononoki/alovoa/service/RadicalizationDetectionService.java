package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.RadicalizationEvent;
import com.nonononoki.alovoa.entity.user.RadicalizationEvent.ContentSource;
import com.nonononoki.alovoa.entity.user.RadicalizationEvent.InterventionType;
import com.nonononoki.alovoa.repo.RadicalizationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects radicalization vocabulary in user content for early intervention.
 * Part of AURA's anti-radicalization system based on documentary analysis
 * of the looksmaxing-to-extremism pipeline.
 *
 * Philosophy: This is NOT about punishing users. It's about detecting distress
 * signals and offering support before someone goes down a harmful path.
 */
@Service
public class RadicalizationDetectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RadicalizationDetectionService.class);

    @Value("${app.radicalization.detection.enabled:true}")
    private boolean detectionEnabled;

    @Value("${app.radicalization.tier2.threshold:3}")
    private int tier2PatternThreshold;

    @Autowired
    private RadicalizationEventRepository eventRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InterventionService interventionService;

    // Vocabulary lists by tier
    private Set<String> tier1Terms;
    private Set<String> tier2Terms;
    private Set<String> tier3Terms;

    // Intervention messages
    private String tier2Message;
    private String tier3Message;

    @PostConstruct
    public void init() {
        loadVocabulary();
    }

    private void loadVocabulary() {
        try {
            ClassPathResource resource = new ClassPathResource("data/radicalization-vocabulary.json");
            JsonNode root = objectMapper.readTree(resource.getInputStream());

            tier1Terms = new HashSet<>();
            tier2Terms = new HashSet<>();
            tier3Terms = new HashSet<>();

            if (root.has("tier1_monitoring") && root.get("tier1_monitoring").has("terms")) {
                root.get("tier1_monitoring").get("terms").forEach(node ->
                    tier1Terms.add(node.asText().toLowerCase())
                );
            }

            if (root.has("tier2_concern") && root.get("tier2_concern").has("terms")) {
                root.get("tier2_concern").get("terms").forEach(node ->
                    tier2Terms.add(node.asText().toLowerCase())
                );
            }

            if (root.has("tier3_urgent") && root.get("tier3_urgent").has("terms")) {
                root.get("tier3_urgent").get("terms").forEach(node ->
                    tier3Terms.add(node.asText().toLowerCase())
                );
            }

            // Load intervention messages
            if (root.has("supportive_responses")) {
                JsonNode responses = root.get("supportive_responses");
                if (responses.has("tier2") && responses.get("tier2").has("message")) {
                    tier2Message = responses.get("tier2").get("message").asText();
                }
                if (responses.has("tier3") && responses.get("tier3").has("message")) {
                    tier3Message = responses.get("tier3").get("message").asText();
                }
            }

            LOGGER.info("Loaded radicalization vocabulary: {} tier1, {} tier2, {} tier3 terms",
                    tier1Terms.size(), tier2Terms.size(), tier3Terms.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load radicalization vocabulary", e);
            tier1Terms = new HashSet<>();
            tier2Terms = new HashSet<>();
            tier3Terms = new HashSet<>();
        }
    }

    /**
     * Scan content for radicalization vocabulary.
     * Returns detection result with tier level and detected terms.
     */
    public DetectionResult scanContent(String content, User user, ContentSource source) {
        if (!detectionEnabled || content == null || content.trim().isEmpty()) {
            return DetectionResult.none();
        }

        String lowerContent = content.toLowerCase();
        String contentHash = hashContent(content);

        // Check for duplicates
        if (eventRepo.existsByUserAndContentHash(user, contentHash)) {
            return DetectionResult.none(); // Already processed
        }

        // Detect terms by tier (check highest tier first)
        List<String> detectedTier3 = findTerms(lowerContent, tier3Terms);
        List<String> detectedTier2 = findTerms(lowerContent, tier2Terms);
        List<String> detectedTier1 = findTerms(lowerContent, tier1Terms);

        // Determine highest tier detected
        int highestTier = 0;
        List<String> allDetected = new ArrayList<>();

        if (!detectedTier3.isEmpty()) {
            highestTier = 3;
            allDetected.addAll(detectedTier3);
        }
        if (!detectedTier2.isEmpty()) {
            if (highestTier < 2) highestTier = 2;
            allDetected.addAll(detectedTier2);
        }
        if (!detectedTier1.isEmpty()) {
            if (highestTier < 1) highestTier = 1;
            allDetected.addAll(detectedTier1);
        }

        if (highestTier == 0) {
            return DetectionResult.none();
        }

        // Log the event
        RadicalizationEvent event = new RadicalizationEvent();
        event.setUser(user);
        event.setTier(highestTier);
        event.setContentSource(source);
        event.setDetectedTerms(String.join(",", allDetected.stream().limit(10).collect(Collectors.toList())));
        event.setTermCount(allDetected.size());
        event.setContentHash(contentHash);
        event.setInterventionSent(false);
        eventRepo.save(event);

        LOGGER.info("Detected tier {} vocabulary for user {} from {}: {} terms",
                highestTier, user.getId(), source, allDetected.size());

        return new DetectionResult(highestTier, allDetected, event);
    }

    /**
     * Find matching terms in content
     */
    private List<String> findTerms(String content, Set<String> terms) {
        List<String> found = new ArrayList<>();
        for (String term : terms) {
            // Use word boundary matching to avoid false positives
            if (content.contains(term)) {
                // Additional check for word boundaries
                String pattern = "\\b" + term.replace(" ", "\\s+") + "\\b";
                if (content.matches(".*" + pattern + ".*") || content.contains(" " + term + " ") ||
                    content.startsWith(term + " ") || content.endsWith(" " + term) ||
                    content.equals(term) || content.contains(term)) {
                    found.add(term);
                }
            }
        }
        return found;
    }

    /**
     * Process intervention for a detection event
     */
    @Transactional
    public InterventionResult processIntervention(RadicalizationEvent event) {
        if (event.getInterventionSent()) {
            return new InterventionResult(false, "Intervention already sent");
        }

        InterventionType interventionType;
        String message = null;

        switch (event.getTier()) {
            case 1:
                interventionType = InterventionType.LOGGED_ONLY;
                // No direct intervention for Tier 1, just monitoring
                break;
            case 2:
                interventionType = InterventionType.GENTLE_MESSAGE;
                message = tier2Message;
                break;
            case 3:
                interventionType = InterventionType.URGENT_OUTREACH;
                message = tier3Message;
                break;
            default:
                interventionType = InterventionType.NONE;
        }

        event.setInterventionSent(true);
        event.setInterventionType(interventionType);
        event.setInterventionSentAt(new Date());
        eventRepo.save(event);

        // Send actual intervention through the intervention service
        if (event.getTier() >= 2 && message != null) {
            interventionService.sendIntervention(event.getUser(), event, event.getTier(), message);
            LOGGER.info("Sent tier {} intervention to user {} via InterventionService",
                    event.getTier(), event.getUser().getId());
        }

        return new InterventionResult(true, message, interventionType);
    }

    /**
     * Check if user needs intervention based on pattern of Tier 2 events
     */
    public boolean checkForPatternIntervention(User user) {
        Date thirtyDaysAgo = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        long tier2Count = eventRepo.countByUserAndTierAndCreatedAtAfter(user, 2, thirtyDaysAgo);

        return tier2Count >= tier2PatternThreshold;
    }

    /**
     * Mark that a user accessed intervention resources
     */
    @Transactional
    public void markResourcesAccessed(User user) {
        Date sevenDaysAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
        List<RadicalizationEvent> recentEvents = eventRepo.findByUserAndCreatedAtAfterOrderByCreatedAtDesc(user, sevenDaysAgo);

        for (RadicalizationEvent event : recentEvents) {
            if (event.getInterventionSent() && !event.getResourcesAccessed()) {
                event.setResourcesAccessed(true);
                eventRepo.save(event);
            }
        }
    }

    /**
     * Scheduled task to check for users needing urgent intervention
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processUrgentInterventions() {
        if (!detectionEnabled) return;

        LOGGER.info("Running urgent intervention check...");

        List<User> usersNeedingIntervention = eventRepo.findUsersNeedingUrgentIntervention();
        int processed = 0;

        for (User user : usersNeedingIntervention) {
            List<RadicalizationEvent> events = eventRepo.findByUserOrderByCreatedAtDesc(user);
            for (RadicalizationEvent event : events) {
                if (event.getTier() == 3 && !event.getInterventionSent()) {
                    processIntervention(event);
                    processed++;
                    LOGGER.warn("Processed urgent intervention for user {}", user.getId());
                }
            }
        }

        LOGGER.info("Urgent intervention check completed. Processed {} interventions.", processed);
    }

    /**
     * Get analytics for radicalization detection effectiveness
     */
    public Map<String, Object> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();

        analytics.put("tier1Events", eventRepo.countByTier(1));
        analytics.put("tier2Events", eventRepo.countByTier(2));
        analytics.put("tier3Events", eventRepo.countByTier(3));

        long interventionsSent = eventRepo.countByInterventionSentAndResourcesAccessed(true, false)
                + eventRepo.countByInterventionSentAndResourcesAccessed(true, true);
        long resourcesAccessed = eventRepo.countByInterventionSentAndResourcesAccessed(true, true);

        analytics.put("interventionsSent", interventionsSent);
        analytics.put("resourcesAccessed", resourcesAccessed);

        if (interventionsSent > 0) {
            analytics.put("resourceAccessRate", (double) resourcesAccessed / interventionsSent);
        }

        return analytics;
    }

    /**
     * Hash content for deduplication (privacy-preserving)
     */
    private String hashContent(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    // Result classes

    public static class DetectionResult {
        private final int tier;
        private final List<String> detectedTerms;
        private final RadicalizationEvent event;

        public DetectionResult(int tier, List<String> detectedTerms, RadicalizationEvent event) {
            this.tier = tier;
            this.detectedTerms = detectedTerms;
            this.event = event;
        }

        public static DetectionResult none() {
            return new DetectionResult(0, List.of(), null);
        }

        public int getTier() { return tier; }
        public List<String> getDetectedTerms() { return detectedTerms; }
        public RadicalizationEvent getEvent() { return event; }
        public boolean isDetected() { return tier > 0; }
    }

    public static class InterventionResult {
        private final boolean sent;
        private final String message;
        private final InterventionType type;

        public InterventionResult(boolean sent, String message) {
            this(sent, message, null);
        }

        public InterventionResult(boolean sent, String message, InterventionType type) {
            this.sent = sent;
            this.message = message;
            this.type = type;
        }

        public boolean isSent() { return sent; }
        public String getMessage() { return message; }
        public InterventionType getType() { return type; }
    }
}
