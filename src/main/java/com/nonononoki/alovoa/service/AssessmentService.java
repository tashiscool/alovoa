package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.AssessmentQuestion;
import com.nonononoki.alovoa.entity.AssessmentQuestion.QuestionCategory;
import com.nonononoki.alovoa.entity.AssessmentQuestion.ResponseScale;
import com.nonononoki.alovoa.entity.AssessmentQuestion.Severity;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentProfile;
import com.nonononoki.alovoa.entity.UserAssessmentProfile.AttachmentStyle;
import com.nonononoki.alovoa.entity.UserAssessmentResponse;
import com.nonononoki.alovoa.model.AssessmentResponseDto;
import com.nonononoki.alovoa.repo.AssessmentQuestionRepository;
import com.nonononoki.alovoa.repo.UserAssessmentProfileRepository;
import com.nonononoki.alovoa.repo.UserAssessmentResponseRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssessmentService.class);

    private static final String QUESTION_BANK_PATH = "data/aura-question-bank.json";

    // Big Five domain mappings
    private static final Map<String, String> DOMAIN_MAP = Map.of(
            "OPENNESS", "O",
            "CONSCIENTIOUSNESS", "C",
            "EXTRAVERSION", "E",
            "AGREEABLENESS", "A",
            "NEUROTICISM", "N"
    );

    @Value("${aura.assessment.auto-load:true}")
    private boolean autoLoadQuestions;

    @Autowired
    private AuthService authService;

    @Autowired
    private AssessmentQuestionRepository questionRepo;

    @Autowired
    private UserAssessmentResponseRepository responseRepo;

    @Autowired
    private UserAssessmentProfileRepository profileRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReputationService reputationService;

    @PostConstruct
    public void init() {
        if (autoLoadQuestions) {
            try {
                loadQuestionsFromJson();
            } catch (Exception e) {
                LOGGER.error("Failed to load assessment questions from JSON", e);
            }
        }
    }

    @Transactional
    public void loadQuestionsFromJson() throws Exception {
        ClassPathResource resource = new ClassPathResource(QUESTION_BANK_PATH);
        if (!resource.exists()) {
            LOGGER.warn("Question bank file not found: {}", QUESTION_BANK_PATH);
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode questions = root.get("questions");

            int displayOrder = 0;

            // Load Big Five questions
            displayOrder = loadBigFiveQuestions(questions.get("BIG_FIVE"), displayOrder);

            // Load Attachment questions
            displayOrder = loadAttachmentQuestions(questions.get("ATTACHMENT"), displayOrder);

            // Load Dealbreaker questions
            displayOrder = loadDealbreakerQuestions(questions.get("DEALBREAKER"), displayOrder);

            // Load Values questions
            displayOrder = loadValuesQuestions(questions.get("VALUES"), displayOrder);

            // Load Lifestyle questions
            displayOrder = loadLifestyleQuestions(questions.get("LIFESTYLE"), displayOrder);

            // Load Red Flag questions
            loadRedFlagQuestions(questions.get("RED_FLAG"), displayOrder);

            LOGGER.info("Successfully loaded assessment questions from JSON");
        }
    }

    private int loadBigFiveQuestions(JsonNode bigFive, int displayOrder) {
        if (bigFive == null) return displayOrder;

        String[] domains = {"OPENNESS", "CONSCIENTIOUSNESS", "EXTRAVERSION", "AGREEABLENESS", "NEUROTICISM"};

        for (String domainName : domains) {
            JsonNode domain = bigFive.get(domainName);
            if (domain == null) continue;

            JsonNode questionList = domain.get("questions");
            if (questionList == null || !questionList.isArray()) continue;

            for (JsonNode q : questionList) {
                String externalId = q.get("id").asText();
                if (questionRepo.existsByExternalId(externalId)) continue;

                AssessmentQuestion question = new AssessmentQuestion();
                question.setExternalId(externalId);
                question.setText(q.get("text").asText());
                question.setCategory(QuestionCategory.BIG_FIVE);
                question.setResponseScale(ResponseScale.LIKERT_5);
                question.setDomain(DOMAIN_MAP.get(domainName));
                question.setFacet(q.get("facet").asInt());
                question.setKeyed(q.get("keyed").asText());
                question.setDisplayOrder(displayOrder++);
                question.setActive(true);

                questionRepo.save(question);
            }
        }

        return displayOrder;
    }

    private int loadAttachmentQuestions(JsonNode attachment, int displayOrder) {
        if (attachment == null) return displayOrder;

        JsonNode questionList = attachment.get("questions");
        if (questionList == null || !questionList.isArray()) return displayOrder;

        for (JsonNode q : questionList) {
            String externalId = q.get("id").asText();
            if (questionRepo.existsByExternalId(externalId)) continue;

            AssessmentQuestion question = new AssessmentQuestion();
            question.setExternalId(externalId);
            question.setText(q.get("text").asText());
            question.setCategory(QuestionCategory.ATTACHMENT);
            question.setResponseScale(ResponseScale.AGREEMENT_5);
            question.setDimension(q.has("dimension") ? q.get("dimension").asText() : null);
            question.setKeyed(q.has("keyed") ? q.get("keyed").asText() : "plus");
            question.setDisplayOrder(displayOrder++);
            question.setActive(true);

            questionRepo.save(question);
        }

        return displayOrder;
    }

    private int loadDealbreakerQuestions(JsonNode dealbreaker, int displayOrder) {
        if (dealbreaker == null) return displayOrder;

        JsonNode questionList = dealbreaker.get("questions");
        if (questionList == null || !questionList.isArray()) return displayOrder;

        for (JsonNode q : questionList) {
            String externalId = q.get("id").asText();
            if (questionRepo.existsByExternalId(externalId)) continue;

            AssessmentQuestion question = new AssessmentQuestion();
            question.setExternalId(externalId);
            question.setText(q.get("text").asText());
            question.setCategory(QuestionCategory.DEALBREAKER);
            question.setResponseScale(ResponseScale.BINARY);
            question.setSubcategory(q.has("subcategory") ? q.get("subcategory").asText() : null);
            question.setRedFlagValue(q.has("redFlagIf") ? q.get("redFlagIf").asInt() : null);
            question.setSeverity(q.has("severity") ? Severity.valueOf(q.get("severity").asText()) : Severity.HIGH);
            question.setDisplayOrder(displayOrder++);
            question.setActive(true);

            questionRepo.save(question);
        }

        return displayOrder;
    }

    private int loadValuesQuestions(JsonNode values, int displayOrder) {
        if (values == null) return displayOrder;

        JsonNode subcats = values.get("subcategories");
        if (subcats == null) return displayOrder;

        Iterator<String> subcatNames = subcats.fieldNames();
        while (subcatNames.hasNext()) {
            String subcatName = subcatNames.next();
            JsonNode subcat = subcats.get(subcatName);
            JsonNode questionList = subcat.get("questions");

            if (questionList == null || !questionList.isArray()) continue;

            for (JsonNode q : questionList) {
                String externalId = q.get("id").asText();
                if (questionRepo.existsByExternalId(externalId)) continue;

                AssessmentQuestion question = new AssessmentQuestion();
                question.setExternalId(externalId);
                question.setText(q.get("text").asText());
                question.setCategory(QuestionCategory.VALUES);
                question.setResponseScale(ResponseScale.AGREEMENT_5);
                question.setSubcategory(subcatName);
                question.setDimension(q.has("dimension") ? q.get("dimension").asText() : null);
                question.setDisplayOrder(displayOrder++);
                question.setActive(true);

                questionRepo.save(question);
            }
        }

        return displayOrder;
    }

    private int loadLifestyleQuestions(JsonNode lifestyle, int displayOrder) {
        if (lifestyle == null) return displayOrder;

        JsonNode subcats = lifestyle.get("subcategories");
        if (subcats == null) return displayOrder;

        Iterator<String> subcatNames = subcats.fieldNames();
        while (subcatNames.hasNext()) {
            String subcatName = subcatNames.next();
            JsonNode subcat = subcats.get(subcatName);
            JsonNode questionList = subcat.get("questions");

            if (questionList == null || !questionList.isArray()) continue;

            for (JsonNode q : questionList) {
                String externalId = q.get("id").asText();
                if (questionRepo.existsByExternalId(externalId)) continue;

                AssessmentQuestion question = new AssessmentQuestion();
                question.setExternalId(externalId);
                question.setText(q.get("text").asText());
                question.setCategory(QuestionCategory.LIFESTYLE);
                question.setResponseScale(ResponseScale.FREQUENCY_5);
                question.setSubcategory(subcatName);
                question.setDimension(q.has("dimension") ? q.get("dimension").asText() : null);
                question.setDisplayOrder(displayOrder++);
                question.setActive(true);

                questionRepo.save(question);
            }
        }

        return displayOrder;
    }

    private void loadRedFlagQuestions(JsonNode redFlag, int displayOrder) {
        if (redFlag == null) return;

        JsonNode questionList = redFlag.get("questions");
        if (questionList == null || !questionList.isArray()) return;

        for (JsonNode q : questionList) {
            String externalId = q.get("id").asText();
            if (questionRepo.existsByExternalId(externalId)) continue;

            AssessmentQuestion question = new AssessmentQuestion();
            question.setExternalId(externalId);
            question.setText(q.get("text").asText());
            question.setCategory(QuestionCategory.RED_FLAG);
            question.setResponseScale(ResponseScale.FREE_TEXT);
            question.setSubcategory(q.has("analyzes") ? q.get("analyzes").asText() : null);
            question.setDisplayOrder(displayOrder++);
            question.setActive(true);

            questionRepo.save(question);
        }
    }

    public Map<String, Object> getQuestionsByCategory(String categoryName) throws Exception {
        QuestionCategory category = QuestionCategory.valueOf(categoryName.toUpperCase());
        List<AssessmentQuestion> questions = questionRepo.findActiveQuestionsByCategory(category);

        User user = authService.getCurrentUser(true);
        List<UserAssessmentResponse> responses = responseRepo.findByUserAndCategory(user, category);
        Set<Long> answeredQuestionIds = responses.stream()
                .map(r -> r.getQuestion().getId())
                .collect(Collectors.toSet());

        List<Map<String, Object>> questionList = new ArrayList<>();
        for (AssessmentQuestion q : questions) {
            Map<String, Object> questionData = new HashMap<>();
            questionData.put("id", q.getId());
            questionData.put("externalId", q.getExternalId());
            questionData.put("text", q.getText());
            questionData.put("responseScale", q.getResponseScale().name());
            questionData.put("answered", answeredQuestionIds.contains(q.getId()));

            if (q.getDomain() != null) questionData.put("domain", q.getDomain());
            if (q.getFacet() != null) questionData.put("facet", q.getFacet());
            if (q.getSubcategory() != null) questionData.put("subcategory", q.getSubcategory());

            // Include existing response if answered
            if (answeredQuestionIds.contains(q.getId())) {
                Optional<UserAssessmentResponse> existingResponse = responses.stream()
                        .filter(r -> r.getQuestion().getId().equals(q.getId()))
                        .findFirst();
                existingResponse.ifPresent(r -> {
                    if (r.getNumericResponse() != null) {
                        questionData.put("response", r.getNumericResponse());
                    } else if (r.getTextResponse() != null) {
                        questionData.put("response", r.getTextResponse());
                    }
                });
            }

            questionList.add(questionData);
        }

        // Get subcategories if applicable
        List<String> subcategories = questionRepo.findDistinctSubcategoriesByCategory(category);

        return Map.of(
                "category", categoryName,
                "questions", questionList,
                "subcategories", subcategories,
                "totalQuestions", questions.size(),
                "answeredQuestions", answeredQuestionIds.size()
        );
    }

    public Map<String, Object> getAssessmentProgress() throws Exception {
        User user = authService.getCurrentUser(true);
        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);

        Map<String, Object> progress = new HashMap<>();

        for (QuestionCategory category : QuestionCategory.values()) {
            long totalQuestions = questionRepo.countByCategoryAndActiveTrue(category);
            long answeredQuestions = responseRepo.countByUserAndCategory(user, category);

            Map<String, Object> categoryProgress = new HashMap<>();
            categoryProgress.put("total", totalQuestions);
            categoryProgress.put("answered", answeredQuestions);
            categoryProgress.put("percentage", totalQuestions > 0 ? (answeredQuestions * 100.0 / totalQuestions) : 0);
            categoryProgress.put("complete", profile != null && isCategoryComplete(profile, category));

            progress.put(category.name(), categoryProgress);
        }

        progress.put("profileComplete", profile != null && Boolean.TRUE.equals(profile.getProfileComplete()));

        return progress;
    }

    private boolean isCategoryComplete(UserAssessmentProfile profile, QuestionCategory category) {
        return switch (category) {
            case BIG_FIVE -> Boolean.TRUE.equals(profile.getBigFiveComplete());
            case ATTACHMENT -> Boolean.TRUE.equals(profile.getAttachmentComplete());
            case VALUES -> Boolean.TRUE.equals(profile.getValuesComplete());
            case DEALBREAKER -> Boolean.TRUE.equals(profile.getDealbreakerComplete());
            case LIFESTYLE -> Boolean.TRUE.equals(profile.getLifestyleComplete());
            case RED_FLAG -> true; // Optional category
        };
    }

    @Transactional
    public Map<String, Object> submitResponses(List<AssessmentResponseDto> responses) throws Exception {
        User user = authService.getCurrentUser(true);

        // Get or create user's assessment profile
        UserAssessmentProfile profile = profileRepo.findByUser(user)
                .orElseGet(() -> {
                    UserAssessmentProfile newProfile = new UserAssessmentProfile();
                    newProfile.setUser(user);
                    return newProfile;
                });

        Map<QuestionCategory, Integer> answeredCount = new HashMap<>();
        List<String> savedQuestionIds = new ArrayList<>();

        for (AssessmentResponseDto dto : responses) {
            Optional<AssessmentQuestion> questionOpt = questionRepo.findByExternalId(dto.getQuestionId());
            if (questionOpt.isEmpty()) {
                LOGGER.warn("Question not found: {}", dto.getQuestionId());
                continue;
            }

            AssessmentQuestion question = questionOpt.get();

            // Find or create response
            UserAssessmentResponse response = responseRepo.findByUserAndQuestion(user, question)
                    .orElseGet(() -> {
                        UserAssessmentResponse newResponse = new UserAssessmentResponse();
                        newResponse.setUser(user);
                        newResponse.setQuestion(question);
                        newResponse.setCategory(question.getCategory());
                        return newResponse;
                    });

            // Set the response value
            if (question.getResponseScale() == ResponseScale.FREE_TEXT) {
                response.setTextResponse(dto.getTextResponse());
            } else {
                response.setNumericResponse(dto.getNumericResponse());
            }

            responseRepo.save(response);
            savedQuestionIds.add(dto.getQuestionId());

            // Track answered counts
            answeredCount.merge(question.getCategory(), 1, Integer::sum);
        }

        // Update profile question counts
        updateProfileQuestionCounts(user, profile);

        // Calculate scores if categories are complete
        if (Boolean.TRUE.equals(profile.getBigFiveComplete())) {
            calculateBigFiveScores(user, profile);
        }
        if (Boolean.TRUE.equals(profile.getAttachmentComplete())) {
            calculateAttachmentScores(user, profile);
        }
        if (Boolean.TRUE.equals(profile.getValuesComplete())) {
            calculateValuesScores(user, profile);
        }
        if (Boolean.TRUE.equals(profile.getLifestyleComplete())) {
            calculateLifestyleScores(user, profile);
        }

        profileRepo.save(profile);

        // Record profile completion behavior if complete
        if (Boolean.TRUE.equals(profile.getProfileComplete())) {
            reputationService.recordBehavior(user,
                    com.nonononoki.alovoa.entity.user.UserBehaviorEvent.BehaviorType.PROFILE_COMPLETE,
                    null, Map.of("type", "comprehensive_assessment"));
        }

        return Map.of(
                "success", true,
                "savedQuestions", savedQuestionIds.size(),
                "profileComplete", Boolean.TRUE.equals(profile.getProfileComplete())
        );
    }

    private void updateProfileQuestionCounts(User user, UserAssessmentProfile profile) {
        for (QuestionCategory category : QuestionCategory.values()) {
            long count = responseRepo.countByUserAndCategory(user, category);
            switch (category) {
                case BIG_FIVE -> profile.setBigFiveQuestionsAnswered((int) count);
                case ATTACHMENT -> profile.setAttachmentQuestionsAnswered((int) count);
                case VALUES -> profile.setValuesQuestionsAnswered((int) count);
                case DEALBREAKER -> profile.setDealbreakerQuestionsAnswered((int) count);
                case LIFESTYLE -> profile.setLifestyleQuestionsAnswered((int) count);
            }
        }
    }

    private void calculateBigFiveScores(User user, UserAssessmentProfile profile) {
        // Calculate each domain score using plus/minus keying
        String[] domains = {"O", "C", "E", "A", "N"};

        for (String domain : domains) {
            Double plusAvg = responseRepo.averageScoreByUserAndDomainPlus(user, domain);
            Double minusAvg = responseRepo.averageScoreByUserAndDomainMinus(user, domain);

            // For minus-keyed items, reverse the score (6 - score)
            double plusScore = plusAvg != null ? plusAvg : 3.0;
            double minusScore = minusAvg != null ? (6.0 - minusAvg) : 3.0;

            // Average the plus and reversed-minus scores, then convert to 0-100
            double rawScore = (plusScore + minusScore) / 2.0;
            double normalizedScore = (rawScore - 1) * 25; // Convert 1-5 to 0-100

            switch (domain) {
                case "O" -> profile.setOpennessScore(normalizedScore);
                case "C" -> profile.setConscientiousnessScore(normalizedScore);
                case "E" -> profile.setExtraversionScore(normalizedScore);
                case "A" -> profile.setAgreeablenessScore(normalizedScore);
                case "N" -> {
                    profile.setNeuroticismScore(normalizedScore);
                    // Emotional stability is inverse of neuroticism
                    profile.setEmotionalStabilityScore(100 - normalizedScore);
                }
            }
        }
    }

    private void calculateAttachmentScores(User user, UserAssessmentProfile profile) {
        Double anxietyScore = responseRepo.averageScoreByUserAndDimension(user, "anxiety");
        Double avoidanceScore = responseRepo.averageScoreByUserAndDimension(user, "avoidance");

        if (anxietyScore != null) {
            profile.setAttachmentAnxietyScore((anxietyScore - 1) * 25);
        }
        if (avoidanceScore != null) {
            profile.setAttachmentAvoidanceScore((avoidanceScore - 1) * 25);
        }

        // Determine attachment style based on anxiety and avoidance
        if (anxietyScore != null && avoidanceScore != null) {
            boolean lowAnxiety = anxietyScore < 3.0;
            boolean lowAvoidance = avoidanceScore < 3.0;

            if (lowAnxiety && lowAvoidance) {
                profile.setAttachmentStyle(AttachmentStyle.SECURE);
            } else if (!lowAnxiety && lowAvoidance) {
                profile.setAttachmentStyle(AttachmentStyle.ANXIOUS_PREOCCUPIED);
            } else if (lowAnxiety && !lowAvoidance) {
                profile.setAttachmentStyle(AttachmentStyle.DISMISSIVE_AVOIDANT);
            } else {
                profile.setAttachmentStyle(AttachmentStyle.FEARFUL_AVOIDANT);
            }
        }
    }

    private void calculateValuesScores(User user, UserAssessmentProfile profile) {
        Double progressiveScore = responseRepo.averageScoreByUserAndDimension(user, "progressive");
        Double egalitarianScore = responseRepo.averageScoreByUserAndDimension(user, "egalitarian");

        if (progressiveScore != null) {
            profile.setValuesProgressiveScore((progressiveScore - 1) * 25);
        }
        if (egalitarianScore != null) {
            profile.setValuesEgalitarianScore((egalitarianScore - 1) * 25);
        }
    }

    private void calculateLifestyleScores(User user, UserAssessmentProfile profile) {
        Double socialScore = responseRepo.averageScoreByUserAndDimension(user, "social");
        Double healthScore = responseRepo.averageScoreByUserAndDimension(user, "health");
        Double workLifeScore = responseRepo.averageScoreByUserAndDimension(user, "worklife");
        Double financeScore = responseRepo.averageScoreByUserAndDimension(user, "finance");

        if (socialScore != null) {
            profile.setLifestyleSocialScore((socialScore - 1) * 25);
        }
        if (healthScore != null) {
            profile.setLifestyleHealthScore((healthScore - 1) * 25);
        }
        if (workLifeScore != null) {
            profile.setLifestyleWorkLifeScore((workLifeScore - 1) * 25);
        }
        if (financeScore != null) {
            profile.setLifestyleFinanceScore((financeScore - 1) * 25);
        }
    }

    public Map<String, Object> getAssessmentResults() throws Exception {
        User user = authService.getCurrentUser(true);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        if (profile == null) {
            return Map.of(
                    "hasResults", false,
                    "message", "Please complete the assessment first"
            );
        }

        Map<String, Object> results = new HashMap<>();
        results.put("hasResults", true);
        results.put("profileComplete", Boolean.TRUE.equals(profile.getProfileComplete()));
        results.put("lastUpdated", profile.getLastUpdated());

        // Big Five results
        if (Boolean.TRUE.equals(profile.getBigFiveComplete())) {
            results.put("bigFive", Map.of(
                    "openness", profile.getOpennessScore(),
                    "conscientiousness", profile.getConscientiousnessScore(),
                    "extraversion", profile.getExtraversionScore(),
                    "agreeableness", profile.getAgreeablenessScore(),
                    "neuroticism", profile.getNeuroticismScore(),
                    "emotionalStability", profile.getEmotionalStabilityScore()
            ));
        }

        // Attachment results
        if (Boolean.TRUE.equals(profile.getAttachmentComplete())) {
            results.put("attachment", Map.of(
                    "style", profile.getAttachmentStyle().name(),
                    "anxietyScore", profile.getAttachmentAnxietyScore(),
                    "avoidanceScore", profile.getAttachmentAvoidanceScore()
            ));
        }

        // Values results
        if (Boolean.TRUE.equals(profile.getValuesComplete())) {
            Map<String, Object> valuesMap = new HashMap<>();
            if (profile.getValuesProgressiveScore() != null) {
                valuesMap.put("progressive", profile.getValuesProgressiveScore());
            }
            if (profile.getValuesEgalitarianScore() != null) {
                valuesMap.put("egalitarian", profile.getValuesEgalitarianScore());
            }
            results.put("values", valuesMap);
        }

        // Lifestyle results
        if (Boolean.TRUE.equals(profile.getLifestyleComplete())) {
            Map<String, Object> lifestyleMap = new HashMap<>();
            if (profile.getLifestyleSocialScore() != null) {
                lifestyleMap.put("social", profile.getLifestyleSocialScore());
            }
            if (profile.getLifestyleHealthScore() != null) {
                lifestyleMap.put("health", profile.getLifestyleHealthScore());
            }
            if (profile.getLifestyleWorkLifeScore() != null) {
                lifestyleMap.put("workLife", profile.getLifestyleWorkLifeScore());
            }
            if (profile.getLifestyleFinanceScore() != null) {
                lifestyleMap.put("finance", profile.getLifestyleFinanceScore());
            }
            results.put("lifestyle", lifestyleMap);
        }

        // Dealbreaker flags
        if (Boolean.TRUE.equals(profile.getDealbreakerComplete()) && profile.getDealbreakerFlags() != null) {
            results.put("dealbreakerFlags", profile.getDealbreakerFlags());
        }

        return results;
    }

    @Transactional
    public Map<String, Object> resetAssessment(String categoryName) throws Exception {
        User user = authService.getCurrentUser(true);

        if (categoryName != null && !categoryName.isEmpty()) {
            // Reset specific category
            QuestionCategory category = QuestionCategory.valueOf(categoryName.toUpperCase());
            responseRepo.deleteByUserAndCategory(user, category);
        } else {
            // Reset all categories
            responseRepo.deleteByUser(user);
            profileRepo.deleteByUser(user);
        }

        return Map.of("success", true);
    }

    public List<UserAssessmentProfile> findCompatibleProfiles(User user, double tolerance) {
        UserAssessmentProfile myProfile = profileRepo.findByUser(user).orElse(null);
        if (myProfile == null || !Boolean.TRUE.equals(myProfile.getBigFiveComplete())) {
            return Collections.emptyList();
        }

        return profileRepo.findSimilarPersonalities(
                myProfile.getOpennessScore(),
                myProfile.getConscientiousnessScore(),
                myProfile.getExtraversionScore(),
                myProfile.getAgreeablenessScore(),
                myProfile.getEmotionalStabilityScore(),
                tolerance
        );
    }

    public boolean checkDealbreakers(User user1, User user2) {
        UserAssessmentProfile profile1 = profileRepo.findByUser(user1).orElse(null);
        UserAssessmentProfile profile2 = profileRepo.findByUser(user2).orElse(null);

        if (profile1 == null || profile2 == null) {
            return true; // No dealbreaker data, allow match
        }

        Integer flags1 = profile1.getDealbreakerFlags();
        Integer flags2 = profile2.getDealbreakerFlags();

        if (flags1 == null || flags2 == null) {
            return true; // No dealbreaker flags, allow match
        }

        // Dealbreaker logic: if there are critical incompatibilities
        // This is simplified - in production, you'd check specific flag combinations
        return (flags1 & flags2) == 0; // No overlapping critical flags
    }
}
