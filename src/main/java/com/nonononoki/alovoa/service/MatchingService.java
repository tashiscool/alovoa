package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.CompatibilityScore;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentProfile;
import com.nonononoki.alovoa.entity.user.UserDailyMatchLimit;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import com.nonononoki.alovoa.model.MatchRecommendationDto;
import com.nonononoki.alovoa.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchingService.class);

    @Value("${app.aura.ai-service.url:http://localhost:8002}")
    private String aiServiceUrl;

    @Value("${app.aura.daily-match.limit.default:5}")
    private Integer dailyMatchLimit;

    @Value("${app.aura.compatibility.minimum:50}")
    private Integer minimumCompatibility;

    @Value("${app.aura.political-assessment.required:true}")
    private Boolean politicalAssessmentRequired;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CompatibilityScoreRepository compatibilityRepo;

    @Autowired
    private UserDailyMatchLimitRepository matchLimitRepo;

    @Autowired
    private UserPersonalityProfileRepository personalityRepo;

    @Autowired
    private PoliticalAssessmentService politicalAssessmentService;

    @Autowired
    private UserAssessmentProfileRepository assessmentProfileRepo;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    public Map<String, Object> getDailyMatches() throws Exception {
        User user = authService.getCurrentUser(true);

        // Check political assessment gate before allowing matching
        if (politicalAssessmentRequired && !politicalAssessmentService.canAccessMatching(user)) {
            String message = politicalAssessmentService.getGateStatusMessage(user);
            return Map.of(
                    "matches", Collections.emptyList(),
                    "gated", true,
                    "gateMessage", message,
                    "gateStatus", user.getPoliticalGateStatus().name()
            );
        }

        Date today = truncateToDay(new Date());

        UserDailyMatchLimit matchLimit = matchLimitRepo
                .findByUserAndMatchDate(user, today)
                .orElseGet(() -> createNewDailyLimit(user, today));

        if (matchLimit.hasReachedLimit()) {
            return Map.of(
                    "matches", Collections.emptyList(),
                    "dailyLimitReached", true,
                    "remaining", 0,
                    "resetsAt", getNextMidnight()
            );
        }

        int remaining = matchLimit.getRemainingMatches();
        List<MatchRecommendationDto> matches;

        try {
            // Try to call AI service
            matches = callAIMatchingService(user, remaining);
        } catch (Exception e) {
            LOGGER.warn("AI service unavailable, using fallback matching", e);
            matches = getFallbackMatches(user, remaining);
        }

        // Update daily limit
        matchLimit.setMatchesShown(matchLimit.getMatchesShown() + matches.size());
        updateShownUserIds(matchLimit, matches);
        matchLimitRepo.save(matchLimit);

        return Map.of(
                "matches", matches,
                "remaining", matchLimit.getRemainingMatches(),
                "dailyLimitReached", matchLimit.hasReachedLimit()
        );
    }

    public Map<String, Object> getCompatibilityExplanation(String matchUuid) throws Exception {
        User user = authService.getCurrentUser(true);
        User matchUser = userRepo.findByUuid(UUID.fromString(matchUuid))
                .orElseThrow(() -> new Exception("User not found"));

        CompatibilityScore compatibility = compatibilityRepo
                .findByUserAAndUserB(user, matchUser)
                .orElseGet(() -> calculateAndStoreCompatibility(user, matchUser));

        Map<String, Object> result = new HashMap<>();
        result.put("overallScore", compatibility.getOverallScore());
        result.put("breakdown", Map.of(
                "values", compatibility.getValuesScore(),
                "lifestyle", compatibility.getLifestyleScore(),
                "personality", compatibility.getPersonalityScore(),
                "attraction", compatibility.getAttractionScore(),
                "circumstantial", compatibility.getCircumstantialScore(),
                "growth", compatibility.getGrowthScore()
        ));

        if (compatibility.getExplanationJson() != null) {
            try {
                result.put("explanation", objectMapper.readValue(
                        compatibility.getExplanationJson(),
                        new TypeReference<Map<String, Object>>() {}
                ));
            } catch (JsonProcessingException e) {
                LOGGER.error("Failed to parse explanation JSON", e);
            }
        }

        return result;
    }

    private List<MatchRecommendationDto> callAIMatchingService(User user, int limit) {
        try {
            String url = aiServiceUrl + "/match/daily?user_id=" + user.getId() + "&limit=" + limit;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = buildUserProfileForMatching(user);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    List.class
            );

            if (response.getBody() != null) {
                return convertToMatchRecommendations(response.getBody());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to call AI matching service", e);
        }
        return Collections.emptyList();
    }

    private List<MatchRecommendationDto> getFallbackMatches(User user, int limit) {
        // Simple fallback: get random users who match basic criteria
        List<MatchRecommendationDto> matches = new ArrayList<>();

        // Use existing compatibility scores if available
        List<CompatibilityScore> cachedScores = compatibilityRepo
                .findByUserAOrderByOverallScoreDesc(user);

        for (CompatibilityScore score : cachedScores) {
            if (matches.size() >= limit) break;
            if (score.getOverallScore() >= minimumCompatibility) {
                MatchRecommendationDto dto = new MatchRecommendationDto();
                dto.setUserId(score.getUserB().getId());
                dto.setUserUuid(score.getUserB().getUuid().toString());
                dto.setCompatibilityScore(score.getOverallScore());
                matches.add(dto);
            }
        }

        return matches;
    }

    private CompatibilityScore calculateAndStoreCompatibility(User userA, User userB) {
        CompatibilityScore score = new CompatibilityScore();
        score.setUserA(userA);
        score.setUserB(userB);

        // Try to call AI service for calculation
        try {
            Map<String, Object> result = callAICompatibilityService(userA, userB);
            score.setValuesScore((Double) result.getOrDefault("values", 50.0));
            score.setLifestyleScore((Double) result.getOrDefault("lifestyle", 50.0));
            score.setPersonalityScore((Double) result.getOrDefault("personality", 50.0));
            score.setAttractionScore((Double) result.getOrDefault("attraction", 50.0));
            score.setCircumstantialScore((Double) result.getOrDefault("circumstantial", 50.0));
            score.setGrowthScore((Double) result.getOrDefault("growth", 50.0));
            score.setOverallScore((Double) result.getOrDefault("overall", 50.0));
            if (result.containsKey("explanation")) {
                score.setExplanationJson(objectMapper.writeValueAsString(result.get("explanation")));
            }
        } catch (Exception e) {
            LOGGER.warn("AI service unavailable, using personality-based calculation", e);
            calculatePersonalityBasedCompatibility(score, userA, userB);
        }

        return compatibilityRepo.save(score);
    }

    private Map<String, Object> callAICompatibilityService(User userA, User userB) throws Exception {
        String url = aiServiceUrl + "/match/calculate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "user_a", buildUserProfileForMatching(userA),
                "user_b", buildUserProfileForMatching(userB)
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
        );

        return response.getBody();
    }

    private void calculatePersonalityBasedCompatibility(CompatibilityScore score, User userA, User userB) {
        // Check dealbreakers first
        if (!assessmentService.checkDealbreakers(userA, userB)) {
            score.setPersonalityScore(0.0);
            score.setValuesScore(0.0);
            score.setLifestyleScore(0.0);
            score.setAttractionScore(0.0);
            score.setCircumstantialScore(0.0);
            score.setGrowthScore(0.0);
            score.setOverallScore(0.0);
            return;
        }

        // Try comprehensive AURA assessment first
        UserAssessmentProfile assessmentA = assessmentProfileRepo.findByUser(userA).orElse(null);
        UserAssessmentProfile assessmentB = assessmentProfileRepo.findByUser(userB).orElse(null);

        double personalityScore = 50.0;
        double valuesScore = 50.0;
        double lifestyleScore = 50.0;

        // Calculate Big Five personality compatibility from comprehensive assessment
        if (assessmentA != null && assessmentB != null &&
            Boolean.TRUE.equals(assessmentA.getBigFiveComplete()) &&
            Boolean.TRUE.equals(assessmentB.getBigFiveComplete())) {

            personalityScore = calculateBigFiveCompatibility(assessmentA, assessmentB);

            // Attachment style compatibility bonus
            if (assessmentA.getAttachmentStyle() != null && assessmentB.getAttachmentStyle() != null) {
                double attachmentBonus = calculateAttachmentCompatibility(assessmentA, assessmentB);
                personalityScore = (personalityScore * 0.8) + (attachmentBonus * 0.2);
            }
        } else {
            // Fallback to legacy personality profile
            UserPersonalityProfile profileA = personalityRepo.findByUser(userA).orElse(null);
            UserPersonalityProfile profileB = personalityRepo.findByUser(userB).orElse(null);

            if (profileA != null && profileB != null && profileA.isComplete() && profileB.isComplete()) {
                double diff = 0;
                diff += Math.abs(profileA.getOpenness() - profileB.getOpenness());
                diff += Math.abs(profileA.getConscientiousness() - profileB.getConscientiousness());
                diff += Math.abs(profileA.getExtraversion() - profileB.getExtraversion());
                diff += Math.abs(profileA.getAgreeableness() - profileB.getAgreeableness());
                diff += Math.abs(profileA.getNeuroticism() - profileB.getNeuroticism());
                personalityScore = 100 - (diff / 5);
            }
        }

        // Calculate values compatibility from comprehensive assessment
        if (assessmentA != null && assessmentB != null &&
            Boolean.TRUE.equals(assessmentA.getValuesComplete()) &&
            Boolean.TRUE.equals(assessmentB.getValuesComplete())) {
            valuesScore = calculateValuesCompatibility(assessmentA, assessmentB);
        } else {
            // Fallback to economic values compatibility
            valuesScore = calculateEconomicValuesCompatibility(userA, userB);
        }

        // Calculate lifestyle compatibility from comprehensive assessment
        if (assessmentA != null && assessmentB != null &&
            Boolean.TRUE.equals(assessmentA.getLifestyleComplete()) &&
            Boolean.TRUE.equals(assessmentB.getLifestyleComplete())) {
            lifestyleScore = calculateLifestyleCompatibility(assessmentA, assessmentB);
        }

        score.setPersonalityScore(personalityScore);
        score.setValuesScore(valuesScore);
        score.setLifestyleScore(lifestyleScore);
        score.setAttractionScore(50.0);
        score.setCircumstantialScore(50.0);
        score.setGrowthScore(50.0);

        // Weighted average with all comprehensive scores
        score.setOverallScore(
                personalityScore * 0.25 +
                valuesScore * 0.25 +
                lifestyleScore * 0.2 +
                50.0 * 0.15 + // attraction
                50.0 * 0.1 +  // circumstantial
                50.0 * 0.05   // growth
        );
    }

    private double calculateBigFiveCompatibility(UserAssessmentProfile a, UserAssessmentProfile b) {
        double totalDiff = 0;
        int count = 0;

        if (a.getOpennessScore() != null && b.getOpennessScore() != null) {
            totalDiff += Math.abs(a.getOpennessScore() - b.getOpennessScore());
            count++;
        }
        if (a.getConscientiousnessScore() != null && b.getConscientiousnessScore() != null) {
            totalDiff += Math.abs(a.getConscientiousnessScore() - b.getConscientiousnessScore());
            count++;
        }
        if (a.getExtraversionScore() != null && b.getExtraversionScore() != null) {
            totalDiff += Math.abs(a.getExtraversionScore() - b.getExtraversionScore());
            count++;
        }
        if (a.getAgreeablenessScore() != null && b.getAgreeablenessScore() != null) {
            totalDiff += Math.abs(a.getAgreeablenessScore() - b.getAgreeablenessScore());
            count++;
        }
        if (a.getEmotionalStabilityScore() != null && b.getEmotionalStabilityScore() != null) {
            totalDiff += Math.abs(a.getEmotionalStabilityScore() - b.getEmotionalStabilityScore());
            count++;
        }

        if (count == 0) return 50.0;
        return 100 - (totalDiff / count);
    }

    private double calculateAttachmentCompatibility(UserAssessmentProfile a, UserAssessmentProfile b) {
        UserAssessmentProfile.AttachmentStyle styleA = a.getAttachmentStyle();
        UserAssessmentProfile.AttachmentStyle styleB = b.getAttachmentStyle();

        // Secure-Secure is ideal
        if (styleA == UserAssessmentProfile.AttachmentStyle.SECURE &&
            styleB == UserAssessmentProfile.AttachmentStyle.SECURE) {
            return 100.0;
        }
        // One secure can stabilize another
        if (styleA == UserAssessmentProfile.AttachmentStyle.SECURE ||
            styleB == UserAssessmentProfile.AttachmentStyle.SECURE) {
            return 80.0;
        }
        // Anxious-Avoidant is challenging
        if ((styleA == UserAssessmentProfile.AttachmentStyle.ANXIOUS_PREOCCUPIED &&
             styleB == UserAssessmentProfile.AttachmentStyle.DISMISSIVE_AVOIDANT) ||
            (styleA == UserAssessmentProfile.AttachmentStyle.DISMISSIVE_AVOIDANT &&
             styleB == UserAssessmentProfile.AttachmentStyle.ANXIOUS_PREOCCUPIED)) {
            return 30.0;
        }
        // Same insecure style
        if (styleA == styleB) {
            return 50.0;
        }
        // Default mixed insecure
        return 40.0;
    }

    private double calculateValuesCompatibility(UserAssessmentProfile a, UserAssessmentProfile b) {
        double totalDiff = 0;
        int count = 0;

        if (a.getValuesProgressiveScore() != null && b.getValuesProgressiveScore() != null) {
            totalDiff += Math.abs(a.getValuesProgressiveScore() - b.getValuesProgressiveScore());
            count++;
        }
        if (a.getValuesEgalitarianScore() != null && b.getValuesEgalitarianScore() != null) {
            totalDiff += Math.abs(a.getValuesEgalitarianScore() - b.getValuesEgalitarianScore());
            count++;
        }

        if (count == 0) return 50.0;
        return 100 - (totalDiff / count);
    }

    private double calculateLifestyleCompatibility(UserAssessmentProfile a, UserAssessmentProfile b) {
        double totalDiff = 0;
        int count = 0;

        if (a.getLifestyleSocialScore() != null && b.getLifestyleSocialScore() != null) {
            totalDiff += Math.abs(a.getLifestyleSocialScore() - b.getLifestyleSocialScore());
            count++;
        }
        if (a.getLifestyleHealthScore() != null && b.getLifestyleHealthScore() != null) {
            totalDiff += Math.abs(a.getLifestyleHealthScore() - b.getLifestyleHealthScore());
            count++;
        }
        if (a.getLifestyleWorkLifeScore() != null && b.getLifestyleWorkLifeScore() != null) {
            totalDiff += Math.abs(a.getLifestyleWorkLifeScore() - b.getLifestyleWorkLifeScore());
            count++;
        }
        if (a.getLifestyleFinanceScore() != null && b.getLifestyleFinanceScore() != null) {
            totalDiff += Math.abs(a.getLifestyleFinanceScore() - b.getLifestyleFinanceScore());
            count++;
        }

        if (count == 0) return 50.0;
        return 100 - (totalDiff / count);
    }

    private double calculateEconomicValuesCompatibility(User userA, User userB) {
        // Use PoliticalAssessmentService to get economic compatibility
        return politicalAssessmentService.getEconomicCompatibility(userA, userB);
    }

    private Map<String, Object> buildUserProfileForMatching(User user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("uuid", user.getUuid().toString());

        // Try comprehensive AURA assessment first
        UserAssessmentProfile assessment = assessmentProfileRepo.findByUser(user).orElse(null);
        if (assessment != null && Boolean.TRUE.equals(assessment.getBigFiveComplete())) {
            Map<String, Object> personalityData = new HashMap<>();
            personalityData.put("openness", assessment.getOpennessScore());
            personalityData.put("conscientiousness", assessment.getConscientiousnessScore());
            personalityData.put("extraversion", assessment.getExtraversionScore());
            personalityData.put("agreeableness", assessment.getAgreeablenessScore());
            personalityData.put("emotionalStability", assessment.getEmotionalStabilityScore());
            personalityData.put("neuroticism", assessment.getNeuroticismScore());
            profile.put("personality", personalityData);

            // Add attachment style if available
            if (Boolean.TRUE.equals(assessment.getAttachmentComplete())) {
                profile.put("attachment", Map.of(
                        "style", assessment.getAttachmentStyle().name(),
                        "anxiety", assessment.getAttachmentAnxietyScore(),
                        "avoidance", assessment.getAttachmentAvoidanceScore()
                ));
            }

            // Add values if complete
            if (Boolean.TRUE.equals(assessment.getValuesComplete())) {
                Map<String, Object> valuesData = new HashMap<>();
                if (assessment.getValuesProgressiveScore() != null) {
                    valuesData.put("progressive", assessment.getValuesProgressiveScore());
                }
                if (assessment.getValuesEgalitarianScore() != null) {
                    valuesData.put("egalitarian", assessment.getValuesEgalitarianScore());
                }
                profile.put("assessmentValues", valuesData);
            }

            // Add lifestyle if complete
            if (Boolean.TRUE.equals(assessment.getLifestyleComplete())) {
                Map<String, Object> lifestyleData = new HashMap<>();
                if (assessment.getLifestyleSocialScore() != null) {
                    lifestyleData.put("social", assessment.getLifestyleSocialScore());
                }
                if (assessment.getLifestyleHealthScore() != null) {
                    lifestyleData.put("health", assessment.getLifestyleHealthScore());
                }
                if (assessment.getLifestyleWorkLifeScore() != null) {
                    lifestyleData.put("workLife", assessment.getLifestyleWorkLifeScore());
                }
                if (assessment.getLifestyleFinanceScore() != null) {
                    lifestyleData.put("finance", assessment.getLifestyleFinanceScore());
                }
                profile.put("lifestyle", lifestyleData);
            }

            // Add dealbreaker flags
            if (Boolean.TRUE.equals(assessment.getDealbreakerComplete()) && assessment.getDealbreakerFlags() != null) {
                profile.put("dealbreakerFlags", assessment.getDealbreakerFlags());
            }
        } else if (user.getPersonalityProfile() != null && user.getPersonalityProfile().isComplete()) {
            // Fallback to legacy personality profile
            UserPersonalityProfile pp = user.getPersonalityProfile();
            profile.put("personality", Map.of(
                    "openness", pp.getOpenness(),
                    "conscientiousness", pp.getConscientiousness(),
                    "extraversion", pp.getExtraversion(),
                    "agreeableness", pp.getAgreeableness(),
                    "neuroticism", pp.getNeuroticism()
            ));
        }

        // Add economic values for AI matching
        if (user.getPoliticalAssessment() != null) {
            var pa = user.getPoliticalAssessment();
            Map<String, Object> economicValues = new HashMap<>();
            if (pa.getEconomicClass() != null) {
                economicValues.put("economicClass", pa.getEconomicClass().name());
            }
            if (pa.getPoliticalOrientation() != null) {
                economicValues.put("politicalOrientation", pa.getPoliticalOrientation().name());
            }
            if (pa.getEconomicValuesScore() != null) {
                economicValues.put("economicValuesScore", pa.getEconomicValuesScore());
            }
            if (pa.getClassConsciousnessScore() != null) {
                economicValues.put("classConsciousnessScore", pa.getClassConsciousnessScore());
            }
            profile.put("economicValues", economicValues);
        }

        if (user.getInterests() != null) {
            profile.put("interests", user.getInterests().stream()
                    .map(i -> i.getText())
                    .collect(Collectors.toList()));
        }

        profile.put("age", getAge(user));
        profile.put("gender", user.getGender() != null ? user.getGender().getId() : null);

        return profile;
    }

    private int getAge(User user) {
        if (user.getDates() != null && user.getDates().getDateOfBirth() != null) {
            LocalDate dob = user.getDates().getDateOfBirth().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            return LocalDate.now().getYear() - dob.getYear();
        }
        return 0;
    }

    private UserDailyMatchLimit createNewDailyLimit(User user, Date today) {
        UserDailyMatchLimit limit = new UserDailyMatchLimit();
        limit.setUser(user);
        limit.setMatchDate(today);
        limit.setMatchesShown(0);
        limit.setMatchLimit(dailyMatchLimit);
        return matchLimitRepo.save(limit);
    }

    private Date truncateToDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Date getNextMidnight() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private List<MatchRecommendationDto> convertToMatchRecommendations(List<Map<String, Object>> rawList) {
        return rawList.stream().map(item -> {
            MatchRecommendationDto dto = new MatchRecommendationDto();
            dto.setUserId(((Number) item.get("user_id")).longValue());
            dto.setUserUuid((String) item.get("uuid"));
            dto.setCompatibilityScore(((Number) item.get("compatibility_score")).doubleValue());
            return dto;
        }).collect(Collectors.toList());
    }

    private void updateShownUserIds(UserDailyMatchLimit limit, List<MatchRecommendationDto> matches) {
        try {
            List<Long> existingIds = new ArrayList<>();
            if (limit.getShownUserIds() != null) {
                existingIds = objectMapper.readValue(limit.getShownUserIds(),
                        new TypeReference<List<Long>>() {});
            }
            for (MatchRecommendationDto match : matches) {
                existingIds.add(match.getUserId());
            }
            limit.setShownUserIds(objectMapper.writeValueAsString(existingIds));
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to update shown user IDs", e);
        }
    }
}
