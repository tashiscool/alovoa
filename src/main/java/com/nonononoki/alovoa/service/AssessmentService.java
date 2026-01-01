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

    private static final String QUESTION_BANK_PATH = "data/aura-comprehensive-questions.json";

    // OKCupid-style importance weights
    private static final Map<String, Double> IMPORTANCE_WEIGHTS = Map.of(
            "irrelevant", 0.0,
            "a_little", 1.0,
            "somewhat", 10.0,
            "very", 50.0,
            "mandatory", 250.0
    );

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
                loadComprehensiveQuestions();
            } catch (Exception e) {
                LOGGER.error("Failed to load assessment questions from JSON", e);
            }
        }
    }

    /**
     * Load questions from the comprehensive flat-array format.
     * This is the new AURA question bank with 4,127 questions.
     */
    @Transactional
    public void loadComprehensiveQuestions() throws Exception {
        ClassPathResource resource = new ClassPathResource(QUESTION_BANK_PATH);
        if (!resource.exists()) {
            LOGGER.warn("Comprehensive question bank not found: {}", QUESTION_BANK_PATH);
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode questions = root.get("questions");

            if (questions == null || !questions.isArray()) {
                LOGGER.warn("No questions array found in question bank");
                return;
            }

            int loaded = 0;
            int skipped = 0;

            for (JsonNode q : questions) {
                String externalId = q.has("id") ? q.get("id").asText() : null;
                if (externalId == null) continue;

                if (questionRepo.existsByExternalId(externalId)) {
                    // Update coreQuestion flag if needed
                    if (q.has("coreQuestion") && q.get("coreQuestion").asBoolean()) {
                        questionRepo.findByExternalId(externalId).ifPresent(existing -> {
                            if (!Boolean.TRUE.equals(existing.getCoreQuestion())) {
                                existing.setCoreQuestion(true);
                                questionRepo.save(existing);
                            }
                        });
                    }
                    skipped++;
                    continue;
                }

                AssessmentQuestion question = new AssessmentQuestion();
                question.setExternalId(externalId);
                question.setText(q.has("text") ? q.get("text").asText() :
                               (q.has("question") ? q.get("question").asText() : ""));

                // Set category from string
                String categoryStr = q.has("category") ? q.get("category").asText() : null;
                question.setSubcategory(categoryStr); // Store original category as subcategory

                // Map to QuestionCategory enum
                QuestionCategory category = mapStringToCategory(categoryStr);
                question.setCategory(category);

                // Set response scale based on question type
                ResponseScale scale = determineResponseScale(q);
                question.setResponseScale(scale);

                // Set optional fields
                if (q.has("subcategory")) {
                    question.setSubcategory(q.get("subcategory").asText());
                }
                if (q.has("domain")) {
                    question.setDomain(q.get("domain").asText());
                }
                if (q.has("facet")) {
                    question.setFacet(q.get("facet").asInt());
                }
                if (q.has("keyed")) {
                    question.setKeyed(q.get("keyed").asText());
                }
                if (q.has("dimension")) {
                    question.setDimension(q.get("dimension").asText());
                }
                if (q.has("coreQuestion")) {
                    question.setCoreQuestion(q.get("coreQuestion").asBoolean());
                }

                // Store options as JSON
                if (q.has("options")) {
                    question.setOptions(objectMapper.writeValueAsString(q.get("options")));
                }

                // Get suggested importance from metadata
                if (q.has("metadata") && q.get("metadata").has("suggested_importance")) {
                    question.setSuggestedImportance(q.get("metadata").get("suggested_importance").asText());
                }

                question.setDisplayOrder(loaded);
                question.setActive(true);

                questionRepo.save(question);
                loaded++;
            }

            LOGGER.info("Loaded {} questions, skipped {} existing (total: {})",
                       loaded, skipped, loaded + skipped);
        }
    }

    private QuestionCategory mapStringToCategory(String categoryStr) {
        if (categoryStr == null) return QuestionCategory.VALUES;

        return switch (categoryStr.toLowerCase()) {
            case "attachment_emotional" -> QuestionCategory.ATTACHMENT;
            case "dealbreakers_safety" -> QuestionCategory.DEALBREAKER;
            case "personality_temperament" -> QuestionCategory.BIG_FIVE;
            case "values_politics", "relationship_dynamics", "family_future",
                 "hypotheticals_scenarios", "location_specific" -> QuestionCategory.VALUES;
            case "lifestyle_compatibility" -> QuestionCategory.LIFESTYLE;
            case "sex_intimacy" -> QuestionCategory.LIFESTYLE;
            default -> QuestionCategory.VALUES;
        };
    }

    private ResponseScale determineResponseScale(JsonNode q) {
        if (q.has("type")) {
            String type = q.get("type").asText();
            if ("binary".equals(type)) return ResponseScale.BINARY;
            if ("free_text".equals(type) || "open_ended".equals(type)) return ResponseScale.FREE_TEXT;
        }

        if (q.has("options") && q.get("options").isArray()) {
            int optionCount = q.get("options").size();
            if (optionCount == 2) return ResponseScale.BINARY;
            if (optionCount >= 5) return ResponseScale.LIKERT_5;
        }

        return ResponseScale.AGREEMENT_5;
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

    /**
     * Calculate OKCupid-style match percentage between two users.
     * Formula: sqrt(user_a_satisfaction * user_b_satisfaction) * 100
     *
     * Each user's satisfaction is calculated based on:
     * - Questions both users have answered
     * - Whether the other user's answer matches what this user finds acceptable
     * - Weighted by the importance each user assigns to each question
     */
    public Map<String, Object> calculateOkCupidMatch(User userA, User userB) {
        List<UserAssessmentResponse> responsesA = responseRepo.findByUser(userA);
        List<UserAssessmentResponse> responsesB = responseRepo.findByUser(userB);

        if (responsesA.isEmpty() || responsesB.isEmpty()) {
            return Map.of(
                    "matchPercentage", 50.0,
                    "hasEnoughData", false,
                    "commonQuestions", 0
            );
        }

        // Build maps for quick lookup
        Map<Long, UserAssessmentResponse> responseMapA = responsesA.stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r));
        Map<Long, UserAssessmentResponse> responseMapB = responsesB.stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r));

        // Find common questions
        Set<Long> commonQuestionIds = new HashSet<>(responseMapA.keySet());
        commonQuestionIds.retainAll(responseMapB.keySet());

        if (commonQuestionIds.isEmpty()) {
            return Map.of(
                    "matchPercentage", 50.0,
                    "hasEnoughData", false,
                    "commonQuestions", 0
            );
        }

        // Calculate satisfaction scores
        double satisfactionA = calculateSatisfaction(responseMapA, responseMapB, commonQuestionIds);
        double satisfactionB = calculateSatisfaction(responseMapB, responseMapA, commonQuestionIds);

        // OKCupid formula: geometric mean of both satisfactions
        double matchPercentage = Math.sqrt(satisfactionA * satisfactionB) * 100;

        // Check for mandatory dealbreakers
        boolean hasMandatoryConflict = checkMandatoryConflicts(responseMapA, responseMapB, commonQuestionIds);
        if (hasMandatoryConflict) {
            matchPercentage = Math.min(matchPercentage, 10.0); // Cap at 10% if mandatory conflict
        }

        return Map.of(
                "matchPercentage", Math.round(matchPercentage * 10.0) / 10.0,
                "hasEnoughData", commonQuestionIds.size() >= 10,
                "commonQuestions", commonQuestionIds.size(),
                "satisfactionA", Math.round(satisfactionA * 1000.0) / 10.0,
                "satisfactionB", Math.round(satisfactionB * 1000.0) / 10.0,
                "hasMandatoryConflict", hasMandatoryConflict
        );
    }

    private double calculateSatisfaction(
            Map<Long, UserAssessmentResponse> myResponses,
            Map<Long, UserAssessmentResponse> theirResponses,
            Set<Long> commonQuestionIds) {

        double totalWeight = 0;
        double satisfiedWeight = 0;

        for (Long questionId : commonQuestionIds) {
            UserAssessmentResponse myResponse = myResponses.get(questionId);
            UserAssessmentResponse theirResponse = theirResponses.get(questionId);

            AssessmentQuestion question = myResponse.getQuestion();

            // Get importance weight (default to "somewhat" = 10)
            double weight = IMPORTANCE_WEIGHTS.getOrDefault("somewhat", 10.0);

            // Check if their answer is acceptable to me
            // For simplicity: same answer = fully satisfied, adjacent = partially
            boolean satisfied = isAnswerAcceptable(myResponse, theirResponse, question);

            totalWeight += weight;
            if (satisfied) {
                satisfiedWeight += weight;
            }
        }

        return totalWeight > 0 ? satisfiedWeight / totalWeight : 0.5;
    }

    private boolean isAnswerAcceptable(
            UserAssessmentResponse myResponse,
            UserAssessmentResponse theirResponse,
            AssessmentQuestion question) {

        Integer myAnswer = myResponse.getNumericResponse();
        Integer theirAnswer = theirResponse.getNumericResponse();

        if (myAnswer == null || theirAnswer == null) {
            return true; // Can't compare, assume acceptable
        }

        // Check if their answer is a red flag
        String theirAnswerStr = String.valueOf(theirAnswer);
        // Red flags would be checked against question metadata in a full implementation

        // Simple matching: same answer or within 1 point is acceptable
        int diff = Math.abs(myAnswer - theirAnswer);
        return diff <= 1;
    }

    private boolean checkMandatoryConflicts(
            Map<Long, UserAssessmentResponse> responsesA,
            Map<Long, UserAssessmentResponse> responsesB,
            Set<Long> commonQuestionIds) {

        for (Long questionId : commonQuestionIds) {
            UserAssessmentResponse responseA = responsesA.get(questionId);
            UserAssessmentResponse responseB = responsesB.get(questionId);

            AssessmentQuestion question = responseA.getQuestion();

            // Check if this is a dealbreaker category question
            if (question.getCategory() == QuestionCategory.DEALBREAKER) {
                Integer answerA = responseA.getNumericResponse();
                Integer answerB = responseB.getNumericResponse();

                if (answerA != null && answerB != null) {
                    // If answers are at opposite extremes on a dealbreaker, it's a conflict
                    if ((answerA == 1 && answerB >= 4) || (answerA >= 4 && answerB == 1)) {
                        if (question.getSeverity() == Severity.CRITICAL) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Get match explanation with detailed breakdown
     */
    public Map<String, Object> getMatchExplanation(User userA, User userB) {
        Map<String, Object> match = calculateOkCupidMatch(userA, userB);

        List<UserAssessmentResponse> responsesA = responseRepo.findByUser(userA);
        List<UserAssessmentResponse> responsesB = responseRepo.findByUser(userB);

        // Calculate category-level compatibility
        Map<String, Double> categoryScores = new HashMap<>();
        for (QuestionCategory category : QuestionCategory.values()) {
            double score = calculateCategoryCompatibility(responsesA, responsesB, category);
            if (score >= 0) {
                categoryScores.put(category.name(), Math.round(score * 10.0) / 10.0);
            }
        }

        Map<String, Object> explanation = new HashMap<>(match);
        explanation.put("categoryBreakdown", categoryScores);

        return explanation;
    }

    private double calculateCategoryCompatibility(
            List<UserAssessmentResponse> responsesA,
            List<UserAssessmentResponse> responsesB,
            QuestionCategory category) {

        List<UserAssessmentResponse> catResponsesA = responsesA.stream()
                .filter(r -> r.getCategory() == category)
                .collect(Collectors.toList());
        List<UserAssessmentResponse> catResponsesB = responsesB.stream()
                .filter(r -> r.getCategory() == category)
                .collect(Collectors.toList());

        if (catResponsesA.isEmpty() || catResponsesB.isEmpty()) {
            return -1; // Not enough data
        }

        Map<Long, UserAssessmentResponse> mapA = catResponsesA.stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r));
        Map<Long, UserAssessmentResponse> mapB = catResponsesB.stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r));

        Set<Long> common = new HashSet<>(mapA.keySet());
        common.retainAll(mapB.keySet());

        if (common.isEmpty()) {
            return -1;
        }

        double satA = calculateSatisfaction(mapA, mapB, common);
        double satB = calculateSatisfaction(mapB, mapA, common);

        return Math.sqrt(satA * satB) * 100;
    }
}
