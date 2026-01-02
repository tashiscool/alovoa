package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentProfile;
import com.nonononoki.alovoa.entity.VideoSegmentPrompt;
import com.nonononoki.alovoa.entity.user.InferredDealbreaker;
import com.nonononoki.alovoa.entity.user.UserScaffoldingProgress;
import com.nonononoki.alovoa.entity.user.UserScaffoldingProgress.ScaffoldingStatus;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.entity.user.UserVideoSegment;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.ScaffoldedProfileDto;
import com.nonononoki.alovoa.model.ScaffoldedProfileDto.*;
import com.nonononoki.alovoa.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing profile scaffolding from video/voice intros.
 * Enables users to get matchable in ~10 minutes by recording short videos
 * that AI analyzes to infer their assessment profile.
 */
@Service
public class ProfileScaffoldingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileScaffoldingService.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private UserVideoIntroductionRepository videoIntroRepo;

    @Autowired
    private UserVideoSegmentRepository segmentRepo;

    @Autowired
    private VideoSegmentPromptRepository promptRepo;

    @Autowired
    private UserScaffoldingProgressRepository progressRepo;

    @Autowired
    private InferredDealbreakerRepository dealbreakerRepo;

    @Autowired
    private UserAssessmentProfileRepository assessmentProfileRepo;

    @Autowired
    private S3StorageService s3StorageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.aura.scaffolding.confidence-threshold:0.5}")
    private double confidenceThreshold;

    /**
     * Get available video segment prompts for recording
     */
    public List<Map<String, Object>> getAvailablePrompts() {
        List<VideoSegmentPrompt> prompts = promptRepo.findAllActiveOrdered();
        return prompts.stream().map(this::promptToMap).collect(Collectors.toList());
    }

    /**
     * Get the user's scaffolded profile based on their video analysis
     */
    public ScaffoldedProfileDto getScaffoldedProfile() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        UserVideoIntroduction intro = videoIntroRepo.findByUser(user).orElse(null);

        if (intro == null || !intro.hasInferenceData()) {
            throw new AlovoaException("no_scaffolded_profile");
        }

        // Get segments
        List<UserVideoSegment> segments = segmentRepo.findByUserOrderByPromptDisplayOrderAsc(user);

        // Get inferred dealbreakers
        List<InferredDealbreaker> dealbreakers = dealbreakerRepo.findByUserOrderByConfidenceDesc(user);

        return buildScaffoldedProfileDto(intro, segments, dealbreakers);
    }

    /**
     * Save user adjustments to the scaffolded profile
     */
    @Transactional
    public void saveAdjustments(Map<String, Object> adjustments) throws AlovoaException {
        User user = authService.getCurrentUser(true);
        UserVideoIntroduction intro = videoIntroRepo.findByUser(user).orElseThrow(
                () -> new AlovoaException("no_scaffolded_profile"));

        UserScaffoldingProgress progress = progressRepo.findByUser(user).orElseGet(() -> {
            UserScaffoldingProgress p = new UserScaffoldingProgress();
            p.setUser(user);
            return p;
        });

        // Store adjustments as JSON
        try {
            progress.setUserAdjustments(objectMapper.writeValueAsString(adjustments));
        } catch (JsonProcessingException e) {
            throw new AlovoaException("invalid_adjustments");
        }

        // Apply adjustments to inference data
        applyAdjustments(intro, adjustments);

        progress.setStatus(ScaffoldingStatus.ADJUSTING);
        progressRepo.save(progress);
        videoIntroRepo.save(intro);
    }

    /**
     * Confirm the scaffolded profile and create UserAssessmentProfile
     */
    @Transactional
    public Map<String, Object> confirmScaffoldedProfile() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        UserVideoIntroduction intro = videoIntroRepo.findByUser(user).orElseThrow(
                () -> new AlovoaException("no_scaffolded_profile"));

        if (!intro.hasInferenceData()) {
            throw new AlovoaException("no_inference_data");
        }

        // Mark as confirmed
        intro.setInferenceReviewed(true);
        intro.setInferenceConfirmed(true);
        intro.setConfirmedAt(new Date());
        videoIntroRepo.save(intro);

        // Create or update UserAssessmentProfile
        UserAssessmentProfile profile = createAssessmentProfileFromInference(user, intro);

        // Update progress
        UserScaffoldingProgress progress = progressRepo.findByUser(user).orElseGet(() -> {
            UserScaffoldingProgress p = new UserScaffoldingProgress();
            p.setUser(user);
            return p;
        });
        progress.setStatus(ScaffoldingStatus.CONFIRMED);
        progress.setInferenceConfirmed(true);
        progress.setConfirmedAt(new Date());
        progressRepo.save(progress);

        // Confirm selected dealbreakers
        List<InferredDealbreaker> dealbreakers = dealbreakerRepo.findByUser(user);
        for (InferredDealbreaker db : dealbreakers) {
            if (!db.isReviewed()) {
                // Auto-confirm high confidence dealbreakers
                if (db.getConfidence() != null && db.getConfidence() >= 0.7) {
                    db.setConfirmed(true);
                }
            }
        }
        dealbreakerRepo.saveAll(dealbreakers);

        return Map.of(
                "success", true,
                "message", "Profile confirmed successfully",
                "profileId", profile.getId(),
                "matchable", true
        );
    }

    /**
     * Re-record: Clear existing video intro and inference data
     */
    @Transactional
    public Map<String, Object> reRecordVideoIntro() throws AlovoaException {
        User user = authService.getCurrentUser(true);

        // Delete existing segments
        List<UserVideoSegment> segments = segmentRepo.findByUser(user);
        for (UserVideoSegment segment : segments) {
            // Delete from S3
            if (segment.getS3Key() != null) {
                try {
                    s3StorageService.deleteMedia(segment.getS3Key());
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete segment from S3: {}", segment.getS3Key());
                }
            }
        }
        segmentRepo.deleteByUser(user);

        // Clear inference data from video intro
        UserVideoIntroduction intro = videoIntroRepo.findByUser(user).orElse(null);
        if (intro != null) {
            if (intro.getS3Key() != null) {
                try {
                    s3StorageService.deleteMedia(intro.getS3Key());
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete intro from S3: {}", intro.getS3Key());
                }
            }
            intro.clearInferenceData();
            intro.setTranscript(null);
            intro.setWorldviewSummary(null);
            intro.setBackgroundSummary(null);
            intro.setLifeStorySummary(null);
            intro.setPersonalityIndicators(null);
            intro.setS3Key(null);
            intro.setStatus(UserVideoIntroduction.AnalysisStatus.PENDING);
            videoIntroRepo.save(intro);
        }

        // Delete inferred dealbreakers
        dealbreakerRepo.deleteByUser(user);

        // Reset progress
        UserScaffoldingProgress progress = progressRepo.findByUser(user).orElse(null);
        if (progress != null) {
            progress.setStatus(ScaffoldingStatus.NOT_STARTED);
            progress.setSegmentsCompleted(0);
            progress.setInferenceGenerated(false);
            progress.setInferenceReviewed(false);
            progress.setInferenceConfirmed(false);
            progress.setUserAdjustments(null);
            progress.setCompletedAt(null);
            progress.setConfirmedAt(null);
            progressRepo.save(progress);
        }

        return Map.of(
                "success", true,
                "message", "Ready for re-recording",
                "prompts", getAvailablePrompts()
        );
    }

    /**
     * Get the user's current scaffolding progress
     */
    public Map<String, Object> getProgress() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        UserScaffoldingProgress progress = progressRepo.findByUser(user).orElse(null);

        if (progress == null) {
            return Map.of(
                    "status", "NOT_STARTED",
                    "segmentsCompleted", 0,
                    "segmentsRequired", promptRepo.countRequiredPrompts(),
                    "prompts", getAvailablePrompts()
            );
        }

        List<UserVideoSegment> segments = segmentRepo.findByUserOrderByPromptDisplayOrderAsc(user);

        Map<String, Object> result = new HashMap<>();
        result.put("status", progress.getStatus().name());
        result.put("segmentsCompleted", progress.getSegmentsCompleted());
        result.put("segmentsRequired", progress.getSegmentsRequired());
        result.put("inferenceGenerated", progress.getInferenceGenerated());
        result.put("inferenceReviewed", progress.getInferenceReviewed());
        result.put("inferenceConfirmed", progress.getInferenceConfirmed());
        result.put("segments", segments.stream().map(this::segmentToMap).collect(Collectors.toList()));
        result.put("prompts", getAvailablePrompts());

        return result;
    }

    /**
     * Check if user has a reviewable scaffolded profile
     */
    public boolean hasScaffoldedProfileReady(User user) {
        UserVideoIntroduction intro = videoIntroRepo.findByUser(user).orElse(null);
        return intro != null && intro.hasInferenceData() && !intro.isInferenceConfirmed();
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private ScaffoldedProfileDto buildScaffoldedProfileDto(
            UserVideoIntroduction intro,
            List<UserVideoSegment> segments,
            List<InferredDealbreaker> dealbreakers) {

        return ScaffoldedProfileDto.builder()
                .bigFive(buildBigFiveMap(intro))
                .values(buildValuesMap(intro))
                .lifestyle(buildLifestyleMap(intro))
                .attachment(buildAttachmentInference(intro))
                .suggestedDealbreakers(buildDealbreakeSuggestions(dealbreakers))
                .lowConfidenceAreas(getLowConfidenceAreas(intro))
                .overallConfidence(intro.getOverallInferenceConfidence())
                .reviewed(Boolean.TRUE.equals(intro.getInferenceReviewed()))
                .confirmed(Boolean.TRUE.equals(intro.getInferenceConfirmed()))
                .confirmedAt(intro.getConfirmedAt())
                .segments(segments.stream().map(this::buildSegmentSummary).collect(Collectors.toList()))
                .build();
    }

    private Map<String, ScoreWithConfidence> buildBigFiveMap(UserVideoIntroduction intro) {
        Map<String, ScoreWithConfidence> map = new LinkedHashMap<>();
        map.put("openness", ScoreWithConfidence.builder()
                .score(intro.getInferredOpenness())
                .confidence(getConfidenceFor(intro, "bigFive"))
                .build());
        map.put("conscientiousness", ScoreWithConfidence.builder()
                .score(intro.getInferredConscientiousness())
                .confidence(getConfidenceFor(intro, "bigFive"))
                .build());
        map.put("extraversion", ScoreWithConfidence.builder()
                .score(intro.getInferredExtraversion())
                .confidence(getConfidenceFor(intro, "bigFive"))
                .build());
        map.put("agreeableness", ScoreWithConfidence.builder()
                .score(intro.getInferredAgreeableness())
                .confidence(getConfidenceFor(intro, "bigFive"))
                .build());
        map.put("neuroticism", ScoreWithConfidence.builder()
                .score(intro.getInferredNeuroticism())
                .confidence(getConfidenceFor(intro, "bigFive"))
                .build());
        return map;
    }

    private Map<String, ScoreWithConfidence> buildValuesMap(UserVideoIntroduction intro) {
        Map<String, ScoreWithConfidence> map = new LinkedHashMap<>();
        map.put("progressive", ScoreWithConfidence.builder()
                .score(intro.getInferredValuesProgressive())
                .confidence(getConfidenceFor(intro, "values"))
                .build());
        map.put("egalitarian", ScoreWithConfidence.builder()
                .score(intro.getInferredValuesEgalitarian())
                .confidence(getConfidenceFor(intro, "values"))
                .build());
        return map;
    }

    private Map<String, ScoreWithConfidence> buildLifestyleMap(UserVideoIntroduction intro) {
        Map<String, ScoreWithConfidence> map = new LinkedHashMap<>();
        map.put("social", ScoreWithConfidence.builder()
                .score(intro.getInferredLifestyleSocial())
                .confidence(getConfidenceFor(intro, "lifestyle"))
                .build());
        map.put("health", ScoreWithConfidence.builder()
                .score(intro.getInferredLifestyleHealth())
                .confidence(getConfidenceFor(intro, "lifestyle"))
                .build());
        map.put("workLife", ScoreWithConfidence.builder()
                .score(intro.getInferredLifestyleWorkLife())
                .confidence(getConfidenceFor(intro, "lifestyle"))
                .build());
        map.put("finance", ScoreWithConfidence.builder()
                .score(intro.getInferredLifestyleFinance())
                .confidence(getConfidenceFor(intro, "lifestyle"))
                .build());
        return map;
    }

    private AttachmentInference buildAttachmentInference(UserVideoIntroduction intro) {
        return AttachmentInference.builder()
                .anxietyScore(intro.getInferredAttachmentAnxiety())
                .avoidanceScore(intro.getInferredAttachmentAvoidance())
                .style(intro.getInferredAttachmentStyle())
                .confidence(getConfidenceFor(intro, "attachment"))
                .build();
    }

    private List<DealbreakeSuggestion> buildDealbreakeSuggestions(List<InferredDealbreaker> dealbreakers) {
        return dealbreakers.stream().map(db -> DealbreakeSuggestion.builder()
                .key(db.getDealbreakerKey())
                .label(db.getLabel())
                .confidence(db.getConfidence())
                .sourceQuote(db.getSourceQuote())
                .confirmed(Boolean.TRUE.equals(db.getConfirmed()))
                .rejected(Boolean.TRUE.equals(db.getRejected()))
                .build()).collect(Collectors.toList());
    }

    private SegmentSummary buildSegmentSummary(UserVideoSegment segment) {
        return SegmentSummary.builder()
                .promptKey(segment.getPromptKey())
                .promptTitle(segment.getPrompt() != null ? segment.getPrompt().getTitle() : null)
                .status(segment.getStatus().name())
                .analyzed(segment.isAnalyzed())
                .uploadedAt(segment.getUploadedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Double getConfidenceFor(UserVideoIntroduction intro, String category) {
        if (intro.getInferredAssessmentJson() == null) {
            return intro.getOverallInferenceConfidence();
        }
        try {
            Map<String, Object> json = objectMapper.readValue(intro.getInferredAssessmentJson(), Map.class);
            Map<String, Object> confidenceScores = (Map<String, Object>) json.get("confidence_scores");
            if (confidenceScores != null && confidenceScores.containsKey(category)) {
                Object val = confidenceScores.get(category);
                return val instanceof Number ? ((Number) val).doubleValue() : null;
            }
        } catch (Exception ignored) {}
        return intro.getOverallInferenceConfidence();
    }

    @SuppressWarnings("unchecked")
    private List<String> getLowConfidenceAreas(UserVideoIntroduction intro) {
        if (intro.getInferredAssessmentJson() == null) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> json = objectMapper.readValue(intro.getInferredAssessmentJson(), Map.class);
            List<String> areas = (List<String>) json.get("low_confidence_areas");
            return areas != null ? areas : Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAdjustments(UserVideoIntroduction intro, Map<String, Object> adjustments) {
        // Big Five
        if (adjustments.containsKey("bigFive")) {
            Map<String, Object> bf = (Map<String, Object>) adjustments.get("bigFive");
            if (bf.containsKey("openness")) intro.setInferredOpenness(toDouble(bf.get("openness")));
            if (bf.containsKey("conscientiousness")) intro.setInferredConscientiousness(toDouble(bf.get("conscientiousness")));
            if (bf.containsKey("extraversion")) intro.setInferredExtraversion(toDouble(bf.get("extraversion")));
            if (bf.containsKey("agreeableness")) intro.setInferredAgreeableness(toDouble(bf.get("agreeableness")));
            if (bf.containsKey("neuroticism")) intro.setInferredNeuroticism(toDouble(bf.get("neuroticism")));
        }
        // Values
        if (adjustments.containsKey("values")) {
            Map<String, Object> v = (Map<String, Object>) adjustments.get("values");
            if (v.containsKey("progressive")) intro.setInferredValuesProgressive(toDouble(v.get("progressive")));
            if (v.containsKey("egalitarian")) intro.setInferredValuesEgalitarian(toDouble(v.get("egalitarian")));
        }
        // Lifestyle
        if (adjustments.containsKey("lifestyle")) {
            Map<String, Object> ls = (Map<String, Object>) adjustments.get("lifestyle");
            if (ls.containsKey("social")) intro.setInferredLifestyleSocial(toDouble(ls.get("social")));
            if (ls.containsKey("health")) intro.setInferredLifestyleHealth(toDouble(ls.get("health")));
            if (ls.containsKey("workLife")) intro.setInferredLifestyleWorkLife(toDouble(ls.get("workLife")));
            if (ls.containsKey("finance")) intro.setInferredLifestyleFinance(toDouble(ls.get("finance")));
        }
        // Attachment
        if (adjustments.containsKey("attachment")) {
            Map<String, Object> att = (Map<String, Object>) adjustments.get("attachment");
            if (att.containsKey("anxiety")) intro.setInferredAttachmentAnxiety(toDouble(att.get("anxiety")));
            if (att.containsKey("avoidance")) intro.setInferredAttachmentAvoidance(toDouble(att.get("avoidance")));
            if (att.containsKey("style")) intro.setInferredAttachmentStyle((String) att.get("style"));
        }
    }

    private Double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) return Double.parseDouble((String) obj);
        return null;
    }

    private UserAssessmentProfile createAssessmentProfileFromInference(User user, UserVideoIntroduction intro) {
        UserAssessmentProfile profile = assessmentProfileRepo.findByUser(user).orElseGet(() -> {
            UserAssessmentProfile p = new UserAssessmentProfile();
            p.setUser(user);
            return p;
        });

        // Big Five
        profile.setOpennessScore(intro.getInferredOpenness());
        profile.setConscientiousnessScore(intro.getInferredConscientiousness());
        profile.setExtraversionScore(intro.getInferredExtraversion());
        profile.setAgreeablenessScore(intro.getInferredAgreeableness());
        profile.setNeuroticismScore(intro.getInferredNeuroticism());
        // Emotional stability is inverse of neuroticism
        if (intro.getInferredNeuroticism() != null) {
            profile.setEmotionalStabilityScore(100.0 - intro.getInferredNeuroticism());
        }

        // Attachment
        profile.setAttachmentAnxietyScore(intro.getInferredAttachmentAnxiety());
        profile.setAttachmentAvoidanceScore(intro.getInferredAttachmentAvoidance());
        if (intro.getInferredAttachmentStyle() != null) {
            try {
                profile.setAttachmentStyle(
                        UserAssessmentProfile.AttachmentStyle.valueOf(intro.getInferredAttachmentStyle()));
            } catch (IllegalArgumentException ignored) {}
        }

        // Values
        profile.setValuesProgressiveScore(intro.getInferredValuesProgressive());
        profile.setValuesEgalitarianScore(intro.getInferredValuesEgalitarian());

        // Lifestyle
        profile.setLifestyleSocialScore(intro.getInferredLifestyleSocial());
        profile.setLifestyleHealthScore(intro.getInferredLifestyleHealth());
        profile.setLifestyleWorkLifeScore(intro.getInferredLifestyleWorkLife());
        profile.setLifestyleFinanceScore(intro.getInferredLifestyleFinance());

        // Mark as complete (since inferred from video)
        profile.setBigFiveComplete(true);
        profile.setAttachmentComplete(true);
        profile.setValuesComplete(true);
        profile.setLifestyleComplete(true);
        profile.setProfileComplete(true);

        // Set question counts to minimum thresholds (indicates video-inferred)
        profile.setBigFiveQuestionsAnswered(25);
        profile.setAttachmentQuestionsAnswered(4);
        profile.setValuesQuestionsAnswered(5);
        profile.setLifestyleQuestionsAnswered(5);
        profile.setDealbreakerQuestionsAnswered(5);  // Required for profileComplete
        profile.setDealbreakerComplete(true);

        return assessmentProfileRepo.save(profile);
    }

    private Map<String, Object> promptToMap(VideoSegmentPrompt prompt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", prompt.getId());
        map.put("key", prompt.getPromptKey());
        map.put("category", prompt.getCategory().name());
        map.put("title", prompt.getTitle());
        map.put("description", prompt.getDescription());
        map.put("suggestedTopics", prompt.getSuggestedTopicsArray());
        map.put("durationMin", prompt.getDurationMinSeconds());
        map.put("durationMax", prompt.getDurationMaxSeconds());
        map.put("required", prompt.getRequiredForMatching());
        return map;
    }

    private Map<String, Object> segmentToMap(UserVideoSegment segment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", segment.getUuid());
        map.put("promptKey", segment.getPromptKey());
        map.put("status", segment.getStatus().name());
        map.put("analyzed", segment.isAnalyzed());
        map.put("uploadedAt", segment.getUploadedAt());
        if (segment.getPrompt() != null) {
            map.put("promptTitle", segment.getPrompt().getTitle());
        }
        return map;
    }
}
