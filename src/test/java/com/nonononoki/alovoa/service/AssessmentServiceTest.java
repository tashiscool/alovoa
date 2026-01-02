package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.AssessmentQuestion;
import com.nonononoki.alovoa.entity.AssessmentQuestion.QuestionCategory;
import com.nonononoki.alovoa.entity.AssessmentQuestion.ResponseScale;
import com.nonononoki.alovoa.entity.AssessmentQuestion.Severity;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentProfile;
import com.nonononoki.alovoa.entity.UserAssessmentProfile.AttachmentStyle;
import com.nonononoki.alovoa.entity.UserAssessmentResponse;
import com.nonononoki.alovoa.model.AssessmentResponseDto;
import com.nonononoki.alovoa.repo.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssessmentServiceTest {

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private AssessmentQuestionRepository questionRepo;

    @Autowired
    private UserAssessmentResponseRepository responseRepo;

    @Autowired
    private UserAssessmentProfileRepository profileRepo;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private ReputationService reputationService;

    private List<User> testUsers;
    private List<AssessmentQuestion> testQuestions;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.when(mailService.sendMail(Mockito.any(String.class), Mockito.any(String.class),
                Mockito.any(String.class), Mockito.any(String.class))).thenReturn(true);

        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService,
                firstNameLengthMax, firstNameLengthMin);

        createTestQuestions();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up test data
        responseRepo.deleteAll();
        profileRepo.deleteAll();

        for (AssessmentQuestion q : testQuestions) {
            questionRepo.delete(q);
        }

        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    private void createTestQuestions() {
        testQuestions = new ArrayList<>();

        // Create Big Five questions (5 domains x 5 questions each = 25)
        String[] domains = {"O", "C", "E", "A", "N"};
        int displayOrder = 0;

        for (String domain : domains) {
            for (int facet = 1; facet <= 5; facet++) {
                AssessmentQuestion q = new AssessmentQuestion();
                q.setExternalId("TEST_BIG5_" + domain + facet);
                q.setText("Big Five question for " + domain + " facet " + facet);
                q.setCategory(QuestionCategory.BIG_FIVE);
                q.setResponseScale(ResponseScale.LIKERT_5);
                q.setDomain(domain);
                q.setFacet(facet);
                q.setKeyed(facet <= 3 ? "plus" : "minus");
                q.setDisplayOrder(displayOrder++);
                q.setActive(true);
                testQuestions.add(questionRepo.save(q));
            }
        }

        // Create Attachment questions (4 questions)
        for (int i = 1; i <= 4; i++) {
            AssessmentQuestion q = new AssessmentQuestion();
            q.setExternalId("TEST_ATTACH_" + i);
            q.setText("Attachment question " + i);
            q.setCategory(QuestionCategory.ATTACHMENT);
            q.setResponseScale(ResponseScale.AGREEMENT_5);
            q.setDimension(i <= 2 ? "anxiety" : "avoidance");
            q.setKeyed("plus");
            q.setDisplayOrder(displayOrder++);
            q.setActive(true);
            testQuestions.add(questionRepo.save(q));
        }

        // Create Dealbreaker questions (5 questions)
        for (int i = 1; i <= 5; i++) {
            AssessmentQuestion q = new AssessmentQuestion();
            q.setExternalId("TEST_DEALBREAKER_" + i);
            q.setText("Dealbreaker question " + i);
            q.setCategory(QuestionCategory.DEALBREAKER);
            q.setResponseScale(ResponseScale.BINARY);
            q.setSubcategory("safety");
            q.setRedFlagValue(1);
            q.setSeverity(i <= 2 ? Severity.CRITICAL : Severity.HIGH);
            q.setDisplayOrder(displayOrder++);
            q.setActive(true);
            testQuestions.add(questionRepo.save(q));
        }

        // Create Values questions (5 questions)
        for (int i = 1; i <= 5; i++) {
            AssessmentQuestion q = new AssessmentQuestion();
            q.setExternalId("TEST_VALUES_" + i);
            q.setText("Values question " + i);
            q.setCategory(QuestionCategory.VALUES);
            q.setResponseScale(ResponseScale.AGREEMENT_5);
            q.setSubcategory("politics");
            q.setDimension(i <= 3 ? "progressive" : "egalitarian");
            q.setDisplayOrder(displayOrder++);
            q.setActive(true);
            testQuestions.add(questionRepo.save(q));
        }

        // Create Lifestyle questions (5 questions)
        for (int i = 1; i <= 5; i++) {
            AssessmentQuestion q = new AssessmentQuestion();
            q.setExternalId("TEST_LIFESTYLE_" + i);
            q.setText("Lifestyle question " + i);
            q.setCategory(QuestionCategory.LIFESTYLE);
            q.setResponseScale(ResponseScale.FREQUENCY_5);
            q.setSubcategory("social");
            q.setDimension("social");
            q.setDisplayOrder(displayOrder++);
            q.setActive(true);
            testQuestions.add(questionRepo.save(q));
        }

        // Create a core question for each category
        AssessmentQuestion coreQ = new AssessmentQuestion();
        coreQ.setExternalId("TEST_CORE_BIG5");
        coreQ.setText("Core Big Five question");
        coreQ.setCategory(QuestionCategory.BIG_FIVE);
        coreQ.setResponseScale(ResponseScale.LIKERT_5);
        coreQ.setDomain("O");
        coreQ.setFacet(1);
        coreQ.setKeyed("plus");
        coreQ.setDisplayOrder(0);
        coreQ.setActive(true);
        coreQ.setCoreQuestion(true);
        testQuestions.add(questionRepo.save(coreQ));
    }

    @Test
    void testGetQuestionsByCategory_BigFive() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> result = assessmentService.getQuestionsByCategory("BIG_FIVE");

        assertNotNull(result);
        assertEquals("BIG_FIVE", result.get("category"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.get("questions");

        // Should have 25 Big Five questions + 1 core question = 26 total
        assertEquals(26, questions.size());
        assertEquals(0, result.get("answeredQuestions"));
    }

    @Test
    void testGetQuestionsByCategory_WithAnsweredQuestions() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Answer one Big Five question
        AssessmentQuestion question = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .findFirst()
                .orElseThrow();

        UserAssessmentResponse response = new UserAssessmentResponse();
        response.setUser(user);
        response.setQuestion(question);
        response.setCategory(QuestionCategory.BIG_FIVE);
        response.setNumericResponse(4);
        responseRepo.save(response);

        Map<String, Object> result = assessmentService.getQuestionsByCategory("BIG_FIVE");

        assertEquals(1, result.get("answeredQuestions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.get("questions");

        // Find the answered question
        Map<String, Object> answeredQ = questions.stream()
                .filter(q -> q.get("id").equals(question.getId()))
                .findFirst()
                .orElseThrow();

        assertTrue((Boolean) answeredQ.get("answered"));
        assertEquals(4, answeredQ.get("response"));
    }

    @Test
    void testGetQuestionsByCategory_InvalidCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        assertThrows(IllegalArgumentException.class, () -> {
            assessmentService.getQuestionsByCategory("INVALID_CATEGORY");
        });
    }

    @Test
    void testGetAssessmentProgress_NoResponses() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> progress = assessmentService.getAssessmentProgress();

        assertNotNull(progress);
        assertFalse((Boolean) progress.get("profileComplete"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bigFiveProgress = (Map<String, Object>) progress.get("BIG_FIVE");
        assertEquals(0L, bigFiveProgress.get("answered"));
        assertEquals(0.0, (Double) bigFiveProgress.get("percentage"), 0.01);
    }

    @Test
    void testSubmitResponses_SingleQuestion() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        AssessmentQuestion question = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .findFirst()
                .orElseThrow();

        List<AssessmentResponseDto> responses = new ArrayList<>();
        responses.add(new AssessmentResponseDto(question.getExternalId(), 4));

        Map<String, Object> result = assessmentService.submitResponses(responses);

        assertTrue((Boolean) result.get("success"));
        assertEquals(1, result.get("savedQuestions"));

        // Verify response was saved
        Optional<UserAssessmentResponse> savedResponse =
                responseRepo.findByUserAndQuestion(user, question);
        assertTrue(savedResponse.isPresent());
        assertEquals(4, savedResponse.get().getNumericResponse());
    }

    @Test
    void testSubmitResponses_MultipleQuestions() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentQuestion> questions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .limit(5)
                .toList();

        List<AssessmentResponseDto> responses = new ArrayList<>();
        for (AssessmentQuestion q : questions) {
            responses.add(new AssessmentResponseDto(q.getExternalId(), 3));
        }

        Map<String, Object> result = assessmentService.submitResponses(responses);

        assertTrue((Boolean) result.get("success"));
        assertEquals(5, result.get("savedQuestions"));

        // Verify all responses were saved
        List<UserAssessmentResponse> savedResponses = responseRepo.findByUser(user);
        assertEquals(5, savedResponses.size());
    }

    @Test
    void testSubmitResponses_UpdateExisting() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        AssessmentQuestion question = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .findFirst()
                .orElseThrow();

        // Submit initial response
        List<AssessmentResponseDto> responses1 = new ArrayList<>();
        responses1.add(new AssessmentResponseDto(question.getExternalId(), 3));
        assessmentService.submitResponses(responses1);

        // Update response
        List<AssessmentResponseDto> responses2 = new ArrayList<>();
        responses2.add(new AssessmentResponseDto(question.getExternalId(), 5));
        Map<String, Object> result = assessmentService.submitResponses(responses2);

        assertTrue((Boolean) result.get("success"));

        // Verify response was updated, not duplicated
        List<UserAssessmentResponse> savedResponses = responseRepo.findByUser(user);
        assertEquals(1, savedResponses.size());
        assertEquals(5, savedResponses.get(0).getNumericResponse());
    }

    @Test
    void testSubmitResponses_TextResponse() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create a free text question
        AssessmentQuestion textQuestion = new AssessmentQuestion();
        textQuestion.setExternalId("TEST_TEXT_1");
        textQuestion.setText("Describe yourself");
        textQuestion.setCategory(QuestionCategory.RED_FLAG);
        textQuestion.setResponseScale(ResponseScale.FREE_TEXT);
        textQuestion.setDisplayOrder(1000);
        textQuestion.setActive(true);
        questionRepo.save(textQuestion);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        responses.add(new AssessmentResponseDto(textQuestion.getExternalId(), "This is my answer"));

        Map<String, Object> result = assessmentService.submitResponses(responses);

        assertTrue((Boolean) result.get("success"));

        // Verify text response was saved
        Optional<UserAssessmentResponse> savedResponse =
                responseRepo.findByUserAndQuestion(user, textQuestion);
        assertTrue(savedResponse.isPresent());
        assertEquals("This is my answer", savedResponse.get().getTextResponse());
    }

    @Test
    void testCalculateBigFiveScores() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Submit all Big Five responses (25 questions minimum for complete profile)
        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> bigFiveQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .toList();

        for (AssessmentQuestion q : bigFiveQuestions) {
            // High scores for all questions
            responses.add(new AssessmentResponseDto(q.getExternalId(), 5));
        }

        assessmentService.submitResponses(responses);

        // Verify profile was created with scores
        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertNotNull(profile.getOpennessScore());
        assertNotNull(profile.getConscientiousnessScore());
        assertNotNull(profile.getExtraversionScore());
        assertNotNull(profile.getAgreeablenessScore());
        assertNotNull(profile.getNeuroticismScore());
        assertNotNull(profile.getEmotionalStabilityScore());

        // Emotional stability should be inverse of neuroticism
        assertEquals(100.0, profile.getEmotionalStabilityScore() + profile.getNeuroticismScore(), 0.01);
    }

    @Test
    void testCalculateBigFiveScores_WithKeyedQuestions() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create mix of plus and minus keyed questions
        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> opennessQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE && "O".equals(q.getDomain()))
                .toList();

        for (AssessmentQuestion q : opennessQuestions) {
            if ("plus".equals(q.getKeyed())) {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 5)); // Agree strongly
            } else {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 1)); // Disagree strongly (reversed)
            }
        }

        // Add responses for other domains to meet minimum
        testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE && !"O".equals(q.getDomain()))
                .forEach(q -> responses.add(new AssessmentResponseDto(q.getExternalId(), 3)));

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);

        // Openness should be high (both plus=5 and minus=1 indicate high openness)
        assertTrue(profile.getOpennessScore() >= 75.0);
    }

    @Test
    void testCalculateAttachmentScores_Secure() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> attachmentQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.ATTACHMENT)
                .toList();

        // Low anxiety and low avoidance = SECURE
        for (AssessmentQuestion q : attachmentQuestions) {
            responses.add(new AssessmentResponseDto(q.getExternalId(), 2)); // Low scores
        }

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertEquals(AttachmentStyle.SECURE, profile.getAttachmentStyle());
        assertTrue(profile.getAttachmentAnxietyScore() < 50.0);
        assertTrue(profile.getAttachmentAvoidanceScore() < 50.0);
    }

    @Test
    void testCalculateAttachmentScores_AnxiousPreoccupied() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> attachmentQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.ATTACHMENT)
                .toList();

        // High anxiety, low avoidance = ANXIOUS_PREOCCUPIED
        for (AssessmentQuestion q : attachmentQuestions) {
            if ("anxiety".equals(q.getDimension())) {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 5)); // High anxiety
            } else {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 2)); // Low avoidance
            }
        }

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertEquals(AttachmentStyle.ANXIOUS_PREOCCUPIED, profile.getAttachmentStyle());
        assertTrue(profile.getAttachmentAnxietyScore() >= 75.0);
    }

    @Test
    void testCalculateAttachmentScores_DismissiveAvoidant() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> attachmentQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.ATTACHMENT)
                .toList();

        // Low anxiety, high avoidance = DISMISSIVE_AVOIDANT
        for (AssessmentQuestion q : attachmentQuestions) {
            if ("anxiety".equals(q.getDimension())) {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 2)); // Low anxiety
            } else {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 5)); // High avoidance
            }
        }

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertEquals(AttachmentStyle.DISMISSIVE_AVOIDANT, profile.getAttachmentStyle());
        assertTrue(profile.getAttachmentAvoidanceScore() >= 75.0);
    }

    @Test
    void testCalculateAttachmentScores_FearfulAvoidant() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> attachmentQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.ATTACHMENT)
                .toList();

        // High anxiety, high avoidance = FEARFUL_AVOIDANT
        for (AssessmentQuestion q : attachmentQuestions) {
            responses.add(new AssessmentResponseDto(q.getExternalId(), 5)); // High on both
        }

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertEquals(AttachmentStyle.FEARFUL_AVOIDANT, profile.getAttachmentStyle());
        assertTrue(profile.getAttachmentAnxietyScore() >= 75.0);
        assertTrue(profile.getAttachmentAvoidanceScore() >= 75.0);
    }

    @Test
    void testCalculateValuesScores() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> valuesQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.VALUES)
                .toList();

        for (AssessmentQuestion q : valuesQuestions) {
            if ("progressive".equals(q.getDimension())) {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 5)); // Very progressive
            } else {
                responses.add(new AssessmentResponseDto(q.getExternalId(), 2)); // Less egalitarian
            }
        }

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertNotNull(profile.getValuesProgressiveScore());
        assertNotNull(profile.getValuesEgalitarianScore());
        assertTrue(profile.getValuesProgressiveScore() >= 75.0);
        assertTrue(profile.getValuesEgalitarianScore() < 50.0);
    }

    @Test
    void testCalculateLifestyleScores() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        List<AssessmentQuestion> lifestyleQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.LIFESTYLE)
                .toList();

        for (AssessmentQuestion q : lifestyleQuestions) {
            responses.add(new AssessmentResponseDto(q.getExternalId(), 4)); // Moderately high
        }

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertNotNull(profile.getLifestyleSocialScore());
        assertTrue(profile.getLifestyleSocialScore() >= 50.0);
    }

    @Test
    void testGetAssessmentResults_NoProfile() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> results = assessmentService.getAssessmentResults();

        assertNotNull(results);
        assertFalse((Boolean) results.get("hasResults"));
        assertTrue(results.containsKey("message"));
    }

    @Test
    void testGetAssessmentResults_WithProfile() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Submit Big Five responses
        List<AssessmentResponseDto> responses = new ArrayList<>();
        testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .forEach(q -> responses.add(new AssessmentResponseDto(q.getExternalId(), 3)));

        assessmentService.submitResponses(responses);

        Map<String, Object> results = assessmentService.getAssessmentResults();

        assertTrue((Boolean) results.get("hasResults"));
        assertTrue(results.containsKey("bigFive"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bigFive = (Map<String, Object>) results.get("bigFive");
        assertNotNull(bigFive.get("openness"));
        assertNotNull(bigFive.get("conscientiousness"));
        assertNotNull(bigFive.get("extraversion"));
        assertNotNull(bigFive.get("agreeableness"));
        assertNotNull(bigFive.get("neuroticism"));
    }

    @Test
    void testResetAssessment_SpecificCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Submit responses for multiple categories
        List<AssessmentResponseDto> responses = new ArrayList<>();
        testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE ||
                            q.getCategory() == QuestionCategory.ATTACHMENT)
                .forEach(q -> responses.add(new AssessmentResponseDto(q.getExternalId(), 3)));

        assessmentService.submitResponses(responses);

        long initialCount = responseRepo.findByUser(user).size();
        assertTrue(initialCount > 0);

        // Reset only Big Five
        Map<String, Object> result = assessmentService.resetAssessment("BIG_FIVE");
        assertTrue((Boolean) result.get("success"));

        // Verify only Big Five responses were deleted
        List<UserAssessmentResponse> remaining = responseRepo.findByUser(user);
        assertTrue(remaining.stream().noneMatch(r -> r.getCategory() == QuestionCategory.BIG_FIVE));
        assertTrue(remaining.stream().anyMatch(r -> r.getCategory() == QuestionCategory.ATTACHMENT));
    }

    @Test
    void testResetAssessment_AllCategories() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Submit responses
        List<AssessmentResponseDto> responses = new ArrayList<>();
        testQuestions.stream()
                .limit(10)
                .forEach(q -> responses.add(new AssessmentResponseDto(q.getExternalId(), 3)));

        assessmentService.submitResponses(responses);

        assertTrue(responseRepo.findByUser(user).size() > 0);

        // Reset all
        Map<String, Object> result = assessmentService.resetAssessment(null);
        assertTrue((Boolean) result.get("success"));

        // Verify all responses and profile deleted
        assertEquals(0, responseRepo.findByUser(user).size());
        assertFalse(profileRepo.findByUser(user).isPresent());
    }

    @Test
    void testCalculateOkCupidMatch_NoData() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Map<String, Object> match = assessmentService.calculateOkCupidMatch(user1, user2);

        assertEquals(50.0, match.get("matchPercentage"));
        assertFalse((Boolean) match.get("hasEnoughData"));
        assertEquals(0, match.get("commonQuestions"));
    }

    @Test
    void testCalculateOkCupidMatch_WithCommonAnswers() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        // Both users answer the same questions
        List<AssessmentQuestion> commonQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .limit(15)
                .toList();

        // User 1 answers
        List<AssessmentResponseDto> responses1 = new ArrayList<>();
        for (AssessmentQuestion q : commonQuestions) {
            responses1.add(new AssessmentResponseDto(q.getExternalId(), 4));
        }
        assessmentService.submitResponses(responses1);

        // User 2 answers similarly
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        List<AssessmentResponseDto> responses2 = new ArrayList<>();
        for (AssessmentQuestion q : commonQuestions) {
            responses2.add(new AssessmentResponseDto(q.getExternalId(), 4)); // Same answers
        }
        assessmentService.submitResponses(responses2);

        Map<String, Object> match = assessmentService.calculateOkCupidMatch(user1, user2);

        assertTrue((Boolean) match.get("hasEnoughData"));
        assertEquals(15, match.get("commonQuestions"));

        // Perfect match should be close to 100%
        double matchPercent = (Double) match.get("matchPercentage");
        assertTrue(matchPercent >= 90.0);
    }

    @Test
    void testCalculateOkCupidMatch_WithDifferences() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        List<AssessmentQuestion> commonQuestions = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .limit(15)
                .toList();

        // User 1 answers with high scores
        List<AssessmentResponseDto> responses1 = new ArrayList<>();
        for (AssessmentQuestion q : commonQuestions) {
            responses1.add(new AssessmentResponseDto(q.getExternalId(), 5));
        }
        assessmentService.submitResponses(responses1);

        // User 2 answers with low scores (opposite)
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        List<AssessmentResponseDto> responses2 = new ArrayList<>();
        for (AssessmentQuestion q : commonQuestions) {
            responses2.add(new AssessmentResponseDto(q.getExternalId(), 1));
        }
        assessmentService.submitResponses(responses2);

        Map<String, Object> match = assessmentService.calculateOkCupidMatch(user1, user2);

        // Low compatibility expected
        double matchPercent = (Double) match.get("matchPercentage");
        assertTrue(matchPercent < 50.0);
    }

    @Test
    void testCalculateOkCupidMatch_WithMandatoryConflict() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        List<AssessmentQuestion> dealbreakers = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.DEALBREAKER &&
                            q.getSeverity() == Severity.CRITICAL)
                .limit(2)
                .toList();

        // User 1 says no (0) to dealbreaker
        List<AssessmentResponseDto> responses1 = new ArrayList<>();
        for (AssessmentQuestion q : dealbreakers) {
            responses1.add(new AssessmentResponseDto(q.getExternalId(), 0));
        }
        assessmentService.submitResponses(responses1);

        // User 2 says yes (1) to same dealbreaker
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        List<AssessmentResponseDto> responses2 = new ArrayList<>();
        for (AssessmentQuestion q : dealbreakers) {
            responses2.add(new AssessmentResponseDto(q.getExternalId(), 1));
        }
        assessmentService.submitResponses(responses2);

        Map<String, Object> match = assessmentService.calculateOkCupidMatch(user1, user2);

        // Should indicate mandatory conflict
        assertTrue(match.containsKey("hasMandatoryConflict"));
    }

    @Test
    void testGetNextQuestion() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> result = assessmentService.getNextQuestion(null);

        assertTrue((Boolean) result.get("hasNext"));
        assertNotNull(result.get("externalId"));
        assertNotNull(result.get("text"));

        // Should prioritize core questions
        assertTrue((Boolean) result.get("isCore"));
    }

    @Test
    void testGetNextQuestion_WithCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> result = assessmentService.getNextQuestion("ATTACHMENT");

        assertTrue((Boolean) result.get("hasNext"));
        assertEquals("ATTACHMENT", result.get("category"));
    }

    @Test
    void testGetNextQuestion_AllAnswered() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Answer all DEALBREAKER questions
        List<AssessmentResponseDto> responses = new ArrayList<>();
        testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.DEALBREAKER)
                .forEach(q -> responses.add(new AssessmentResponseDto(q.getExternalId(), 0)));

        assessmentService.submitResponses(responses);

        Map<String, Object> result = assessmentService.getNextQuestion("DEALBREAKER");

        assertFalse((Boolean) result.get("hasNext"));
        assertTrue(result.containsKey("message"));
    }

    @Test
    void testValidateAnswer_ValidNumeric() throws Exception {
        AssessmentQuestion question = testQuestions.stream()
                .filter(q -> q.getResponseScale() == ResponseScale.LIKERT_5)
                .findFirst()
                .orElseThrow();

        Map<String, Object> result = assessmentService.validateAnswer(
                question.getExternalId(), 4, null);

        assertTrue((Boolean) result.get("valid"));
    }

    @Test
    void testValidateAnswer_InvalidRange() throws Exception {
        AssessmentQuestion question = testQuestions.stream()
                .filter(q -> q.getResponseScale() == ResponseScale.LIKERT_5)
                .findFirst()
                .orElseThrow();

        Map<String, Object> result = assessmentService.validateAnswer(
                question.getExternalId(), 10, null);

        assertFalse((Boolean) result.get("valid"));
        assertTrue(result.containsKey("error"));
    }

    @Test
    void testValidateAnswer_BinaryQuestion() throws Exception {
        AssessmentQuestion question = testQuestions.stream()
                .filter(q -> q.getResponseScale() == ResponseScale.BINARY)
                .findFirst()
                .orElseThrow();

        Map<String, Object> valid0 = assessmentService.validateAnswer(
                question.getExternalId(), 0, null);
        assertTrue((Boolean) valid0.get("valid"));

        Map<String, Object> valid1 = assessmentService.validateAnswer(
                question.getExternalId(), 1, null);
        assertTrue((Boolean) valid1.get("valid"));

        Map<String, Object> invalid = assessmentService.validateAnswer(
                question.getExternalId(), 2, null);
        assertFalse((Boolean) invalid.get("valid"));
    }

    @Test
    void testValidateAnswer_TextResponse() throws Exception {
        // Create a text question
        AssessmentQuestion textQuestion = new AssessmentQuestion();
        textQuestion.setExternalId("TEST_TEXT_VALIDATE");
        textQuestion.setText("Free text question");
        textQuestion.setCategory(QuestionCategory.RED_FLAG);
        textQuestion.setResponseScale(ResponseScale.FREE_TEXT);
        textQuestion.setDisplayOrder(2000);
        textQuestion.setActive(true);
        questionRepo.save(textQuestion);

        Map<String, Object> valid = assessmentService.validateAnswer(
                textQuestion.getExternalId(), null, "Valid text response");
        assertTrue((Boolean) valid.get("valid"));

        Map<String, Object> invalid = assessmentService.validateAnswer(
                textQuestion.getExternalId(), null, "");
        assertFalse((Boolean) invalid.get("valid"));
    }

    @Test
    void testGetNextUnansweredQuestions_Batch() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> result = assessmentService.getNextUnansweredQuestions(null, 10);

        assertNotNull(result);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.get("questions");

        assertTrue(questions.size() <= 10);
        assertTrue((Long) result.get("totalUnanswered") > 0);
    }

    @Test
    void testCategoryBasedFiltering() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> result = assessmentService.getQuestionsByCategory("ATTACHMENT");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.get("questions");

        // All questions should be ATTACHMENT category
        assertTrue(questions.stream()
                .allMatch(q -> "ATTACHMENT".equals(q.get("category"))));
    }

    @Test
    void testQuestionWeighting() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Submit responses with a suggested importance
        AssessmentQuestion question = testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.DEALBREAKER)
                .findFirst()
                .orElseThrow();

        question.setSuggestedImportance("mandatory");
        questionRepo.save(question);

        Map<String, Object> questionData = assessmentService.getQuestionsByCategory("DEALBREAKER");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionData.get("questions");

        Map<String, Object> foundQuestion = questions.stream()
                .filter(q -> q.get("externalId").equals(question.getExternalId()))
                .findFirst()
                .orElse(null);

        assertNotNull(foundQuestion);
        assertEquals("mandatory", foundQuestion.get("suggestedImportance"));
    }

    @Test
    void testIncompleteResponses() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Submit only partial Big Five responses (less than required for completion)
        List<AssessmentResponseDto> responses = new ArrayList<>();
        testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.BIG_FIVE)
                .limit(10) // Less than the 120 required
                .forEach(q -> responses.add(new AssessmentResponseDto(q.getExternalId(), 3)));

        assessmentService.submitResponses(responses);

        UserAssessmentProfile profile = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile);

        // Profile should not be complete
        assertFalse(Boolean.TRUE.equals(profile.getBigFiveComplete()));
        assertFalse(Boolean.TRUE.equals(profile.getProfileComplete()));
    }

    @Test
    void testRetakeScenario() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // First assessment
        List<AssessmentResponseDto> responses1 = new ArrayList<>();
        testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.ATTACHMENT)
                .forEach(q -> responses1.add(new AssessmentResponseDto(q.getExternalId(), 2)));

        assessmentService.submitResponses(responses1);
        UserAssessmentProfile profile1 = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile1);

        // Reset and retake
        assessmentService.resetAssessment("ATTACHMENT");

        // Second assessment with different answers
        List<AssessmentResponseDto> responses2 = new ArrayList<>();
        testQuestions.stream()
                .filter(q -> q.getCategory() == QuestionCategory.ATTACHMENT)
                .forEach(q -> responses2.add(new AssessmentResponseDto(q.getExternalId(), 5)));

        assessmentService.submitResponses(responses2);
        UserAssessmentProfile profile2 = profileRepo.findByUser(user).orElse(null);
        assertNotNull(profile2);

        // Scores should be different
        assertNotEquals(profile1.getAttachmentAnxietyScore(), profile2.getAttachmentAnxietyScore());
    }

    @Test
    void testGetMatchExplanation() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        // Both users answer some questions
        List<AssessmentQuestion> commonQuestions = testQuestions.stream()
                .limit(15)
                .toList();

        List<AssessmentResponseDto> responses1 = new ArrayList<>();
        for (AssessmentQuestion q : commonQuestions) {
            responses1.add(new AssessmentResponseDto(q.getExternalId(), 3));
        }
        assessmentService.submitResponses(responses1);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        List<AssessmentResponseDto> responses2 = new ArrayList<>();
        for (AssessmentQuestion q : commonQuestions) {
            responses2.add(new AssessmentResponseDto(q.getExternalId(), 3));
        }
        assessmentService.submitResponses(responses2);

        Map<String, Object> explanation = assessmentService.getMatchExplanation(user1, user2);

        assertNotNull(explanation);
        assertTrue(explanation.containsKey("matchPercentage"));
        assertTrue(explanation.containsKey("categoryBreakdown"));

        @SuppressWarnings("unchecked")
        Map<String, Double> breakdown = (Map<String, Double>) explanation.get("categoryBreakdown");
        assertNotNull(breakdown);
    }
}
