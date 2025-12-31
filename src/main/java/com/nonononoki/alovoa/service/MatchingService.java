package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.CompatibilityScore;
import com.nonononoki.alovoa.entity.User;
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
        // Simple personality-based compatibility calculation
        double personalityScore = 50.0;

        UserPersonalityProfile profileA = personalityRepo.findByUser(userA).orElse(null);
        UserPersonalityProfile profileB = personalityRepo.findByUser(userB).orElse(null);

        if (profileA != null && profileB != null && profileA.isComplete() && profileB.isComplete()) {
            // Calculate Big Five similarity
            double diff = 0;
            diff += Math.abs(profileA.getOpenness() - profileB.getOpenness());
            diff += Math.abs(profileA.getConscientiousness() - profileB.getConscientiousness());
            diff += Math.abs(profileA.getExtraversion() - profileB.getExtraversion());
            diff += Math.abs(profileA.getAgreeableness() - profileB.getAgreeableness());
            diff += Math.abs(profileA.getNeuroticism() - profileB.getNeuroticism());

            // Max diff is 500 (5 traits * 100), normalize to 0-100 compatibility
            personalityScore = 100 - (diff / 5);
        }

        // Calculate economic values compatibility
        double valuesScore = calculateEconomicValuesCompatibility(userA, userB);

        score.setPersonalityScore(personalityScore);
        score.setValuesScore(valuesScore);
        score.setLifestyleScore(50.0);
        score.setAttractionScore(50.0);
        score.setCircumstantialScore(50.0);
        score.setGrowthScore(50.0);

        // Weighted average with economic values now included
        score.setOverallScore(
                personalityScore * 0.25 +
                valuesScore * 0.25 + // economic values now weighted
                50.0 * 0.2 + // lifestyle
                50.0 * 0.15 + // attraction
                50.0 * 0.1 + // circumstantial
                50.0 * 0.05  // growth
        );
    }

    private double calculateEconomicValuesCompatibility(User userA, User userB) {
        // Use PoliticalAssessmentService to get economic compatibility
        return politicalAssessmentService.getEconomicCompatibility(userA, userB);
    }

    private Map<String, Object> buildUserProfileForMatching(User user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("uuid", user.getUuid().toString());

        if (user.getPersonalityProfile() != null && user.getPersonalityProfile().isComplete()) {
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
