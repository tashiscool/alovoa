package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import com.nonononoki.alovoa.model.PersonalityAssessmentDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserPersonalityProfileRepository;
import com.nonononoki.alovoa.repo.UserRepository;
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
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonalityServiceTest {

    @Autowired
    private PersonalityService personalityService;

    @Autowired
    private UserPersonalityProfileRepository personalityRepo;

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
    private RestTemplate restTemplate;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(Mockito.any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    void testGetAssessmentQuestions() {
        Map<String, Object> result = personalityService.getAssessmentQuestions();

        assertNotNull(result);
        assertTrue(result.containsKey("questions"));
        assertTrue(result.containsKey("scaleMin"));
        assertTrue(result.containsKey("scaleMax"));
        assertTrue(result.containsKey("scaleLabels"));
        assertTrue(result.containsKey("version"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) result.get("questions");

        // Should have 25+ questions (5 traits x 5 questions + 4 attachment)
        assertTrue(questions.size() >= 25);

        // Check question structure
        Map<String, Object> firstQuestion = questions.get(0);
        assertTrue(firstQuestion.containsKey("id"));
        assertTrue(firstQuestion.containsKey("text"));
        assertTrue(firstQuestion.containsKey("trait"));

        // Check scale
        assertEquals(1, result.get("scaleMin"));
        assertEquals(5, result.get("scaleMax"));
    }

    @Test
    void testSubmitAssessment() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        PersonalityAssessmentDto dto = new PersonalityAssessmentDto();
        Map<String, Integer> answers = new HashMap<>();

        // Openness answers (high openness)
        answers.put("O1", 5);
        answers.put("O2", 5);
        answers.put("O3", 4);
        answers.put("O4", 5);
        answers.put("O5", 4);

        // Conscientiousness answers (medium)
        answers.put("C1", 3);
        answers.put("C2", 4);
        answers.put("C3", 3);
        answers.put("C4", 3);
        answers.put("C5", 4);

        // Extraversion answers (low)
        answers.put("E1", 2);
        answers.put("E2", 2);
        answers.put("E3", 2);
        answers.put("E4", 1);
        answers.put("E5", 3);

        // Agreeableness answers (high)
        answers.put("A1", 5);
        answers.put("A2", 5);
        answers.put("A3", 4);
        answers.put("A4", 5);
        answers.put("A5", 4);

        // Neuroticism answers (low)
        answers.put("N1", 2);
        answers.put("N2", 2);
        answers.put("N3", 1);
        answers.put("N4", 2);
        answers.put("N5", 1);

        // Attachment answers (secure)
        answers.put("AT1", 4);
        answers.put("AT2", 2);
        answers.put("AT3", 2);
        answers.put("AT4", 5);

        dto.setAnswers(answers);

        Map<String, Object> result = personalityService.submitAssessment(dto);

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertTrue(result.containsKey("profile"));

        // Verify profile was saved
        UserPersonalityProfile savedProfile = personalityRepo.findByUser(user).orElse(null);
        assertNotNull(savedProfile);
        assertTrue(savedProfile.isComplete());
        assertNotNull(savedProfile.getOpenness());
        assertNotNull(savedProfile.getConscientiousness());
        assertNotNull(savedProfile.getExtraversion());
        assertNotNull(savedProfile.getAgreeableness());
        assertNotNull(savedProfile.getNeuroticism());
        assertNotNull(savedProfile.getAttachmentStyle());

        // High openness should be 75-100
        assertTrue(savedProfile.getOpenness() >= 75);

        // Low extraversion should be 0-40
        assertTrue(savedProfile.getExtraversion() <= 40);
    }

    @Test
    void testGetPersonalityResults_NoProfile() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        Map<String, Object> result = personalityService.getPersonalityResults();

        assertNotNull(result);
        assertFalse((Boolean) result.get("hasResults"));
        assertTrue(result.containsKey("message"));
    }

    @Test
    void testGetPersonalityResults_WithProfile() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // First submit an assessment
        PersonalityAssessmentDto dto = new PersonalityAssessmentDto();
        Map<String, Integer> answers = createCompleteAnswers();
        dto.setAnswers(answers);
        personalityService.submitAssessment(dto);

        // Now get results
        Map<String, Object> result = personalityService.getPersonalityResults();

        assertNotNull(result);
        assertTrue((Boolean) result.get("hasResults"));
        assertTrue(result.containsKey("profile"));

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) result.get("profile");
        assertTrue(profile.containsKey("bigFive"));
        assertTrue(profile.containsKey("completedAt"));
    }

    @Test
    void testRetakeAssessment() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // First submit an assessment
        PersonalityAssessmentDto dto = new PersonalityAssessmentDto();
        dto.setAnswers(createCompleteAnswers());
        personalityService.submitAssessment(dto);

        // Verify profile exists and is complete
        UserPersonalityProfile profile = personalityRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertTrue(profile.isComplete());

        // Retake assessment
        Map<String, Object> result = personalityService.retakeAssessment();
        assertTrue((Boolean) result.get("success"));

        // Verify profile is reset
        profile = personalityRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertFalse(profile.isComplete());
        assertNull(profile.getOpenness());
    }

    @Test
    void testAttachmentStyleCalculation_Secure() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        PersonalityAssessmentDto dto = new PersonalityAssessmentDto();
        Map<String, Integer> answers = createCompleteAnswers();
        // Secure attachment: high intimacy comfort, low abandonment worry, low emotional hiding
        answers.put("AT1", 4); // Easy to depend
        answers.put("AT2", 2); // Low abandonment worry
        answers.put("AT3", 2); // Don't hide feelings
        answers.put("AT4", 5); // Comfortable with intimacy
        dto.setAnswers(answers);

        personalityService.submitAssessment(dto);

        UserPersonalityProfile profile = personalityRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertEquals(UserPersonalityProfile.AttachmentStyle.SECURE, profile.getAttachmentStyle());
    }

    @Test
    void testAttachmentStyleCalculation_Anxious() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        PersonalityAssessmentDto dto = new PersonalityAssessmentDto();
        Map<String, Integer> answers = createCompleteAnswers();
        // Anxious attachment: high abandonment worry
        answers.put("AT1", 4);
        answers.put("AT2", 5); // High abandonment worry
        answers.put("AT3", 2);
        answers.put("AT4", 3);
        dto.setAnswers(answers);

        personalityService.submitAssessment(dto);

        UserPersonalityProfile profile = personalityRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertEquals(UserPersonalityProfile.AttachmentStyle.ANXIOUS, profile.getAttachmentStyle());
    }

    @Test
    void testAttachmentStyleCalculation_Avoidant() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        PersonalityAssessmentDto dto = new PersonalityAssessmentDto();
        Map<String, Integer> answers = createCompleteAnswers();
        // Avoidant attachment: high emotional hiding, low dependence
        answers.put("AT1", 1); // Hard to depend
        answers.put("AT2", 2);
        answers.put("AT3", 5); // Hide feelings
        answers.put("AT4", 2);
        dto.setAnswers(answers);

        personalityService.submitAssessment(dto);

        UserPersonalityProfile profile = personalityRepo.findByUser(user).orElse(null);
        assertNotNull(profile);
        assertEquals(UserPersonalityProfile.AttachmentStyle.AVOIDANT, profile.getAttachmentStyle());
    }

    private Map<String, Integer> createCompleteAnswers() {
        Map<String, Integer> answers = new HashMap<>();
        // Fill all answers with neutral values
        for (int i = 1; i <= 5; i++) {
            answers.put("O" + i, 3);
            answers.put("C" + i, 3);
            answers.put("E" + i, 3);
            answers.put("A" + i, 3);
            answers.put("N" + i, 3);
        }
        answers.put("AT1", 3);
        answers.put("AT2", 3);
        answers.put("AT3", 3);
        answers.put("AT4", 3);
        return answers;
    }
}
