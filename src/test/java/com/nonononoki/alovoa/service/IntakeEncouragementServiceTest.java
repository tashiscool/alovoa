package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.Tools;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserDates;
import com.nonononoki.alovoa.repo.ConversationRepository;
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

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntakeEncouragementServiceTest {

    @Autowired
    private IntakeEncouragementService encouragementService;

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

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    void testGetLifeStats_WithBirthday() throws Exception {
        User user = testUsers.get(0);

        // Set birthday to 25 years ago
        UserDates dates = new UserDates();
        dates.setDateOfBirth(Tools.ageToDate(25));
        user.setDates(dates);

        Map<String, Object> stats = encouragementService.getLifeStats(user);

        assertNotNull(stats);
        assertFalse(stats.isEmpty());
        assertTrue(stats.containsKey("daysAlive"));
        assertTrue(stats.containsKey("weeksAlive"));
        assertTrue(stats.containsKey("monthsAlive"));
        assertTrue(stats.containsKey("yearsAlive"));
        assertTrue(stats.containsKey("approximateHeartbeats"));
        assertTrue(stats.containsKey("approximateMealsEaten"));
        assertTrue(stats.containsKey("approximateDreamsHad"));
        assertTrue(stats.containsKey("approximateSunrises"));
        assertTrue(stats.containsKey("personalMessage"));

        // Verify calculations are reasonable for 25 year old
        long daysAlive = (long) stats.get("daysAlive");
        int yearsAlive = (int) stats.get("yearsAlive");

        assertTrue(daysAlive > 9000); // 25 years = ~9125 days
        assertTrue(daysAlive < 10000);
        assertEquals(25, yearsAlive);

        // Check derived stats
        long heartbeats = (long) stats.get("approximateHeartbeats");
        assertEquals(daysAlive * 100_000L, heartbeats);

        String message = (String) stats.get("personalMessage");
        assertNotNull(message);
        assertTrue(message.contains(String.valueOf(daysAlive)));
    }

    @Test
    void testGetLifeStats_NoBirthday() throws Exception {
        User user = testUsers.get(0);

        Map<String, Object> stats = encouragementService.getLifeStats(user);

        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    @Test
    void testGetLifeStats_DifferentAges() throws Exception {
        User user = testUsers.get(0);

        // Test with 18 year old
        UserDates dates = new UserDates();
        dates.setDateOfBirth(Tools.ageToDate(18));
        user.setDates(dates);

        Map<String, Object> stats18 = encouragementService.getLifeStats(user);
        long days18 = (long) stats18.get("daysAlive");

        // Test with 30 year old
        dates.setDateOfBirth(Tools.ageToDate(30));
        Map<String, Object> stats30 = encouragementService.getLifeStats(user);
        long days30 = (long) stats30.get("daysAlive");

        // 30 year old should have more days alive
        assertTrue(days30 > days18);
    }

    @Test
    void testGetStepEncouragement_Questions() throws Exception {
        String encouragement = encouragementService.getStepEncouragement("questions");

        assertNotNull(encouragement);
        assertFalse(encouragement.isEmpty());
        // Should be one of the predefined messages
        assertTrue(encouragement.length() > 10);
    }

    @Test
    void testGetStepEncouragement_Video() throws Exception {
        String encouragement = encouragementService.getStepEncouragement("video");

        assertNotNull(encouragement);
        assertFalse(encouragement.isEmpty());
    }

    @Test
    void testGetStepEncouragement_Profile() throws Exception {
        String encouragement = encouragementService.getStepEncouragement("profile");

        assertNotNull(encouragement);
        assertFalse(encouragement.isEmpty());
    }

    @Test
    void testGetStepEncouragement_Photos() throws Exception {
        String encouragement = encouragementService.getStepEncouragement("photos");

        assertNotNull(encouragement);
        assertFalse(encouragement.isEmpty());
    }

    @Test
    void testGetStepEncouragement_UnknownStep() throws Exception {
        String encouragement = encouragementService.getStepEncouragement("unknown_step");

        // Should return default encouragement from questions
        assertNotNull(encouragement);
        assertFalse(encouragement.isEmpty());
    }

    @Test
    void testGetStepEncouragement_Randomness() throws Exception {
        // Call multiple times to verify randomness
        String enc1 = encouragementService.getStepEncouragement("questions");
        String enc2 = encouragementService.getStepEncouragement("questions");
        String enc3 = encouragementService.getStepEncouragement("questions");

        // All should be non-null
        assertNotNull(enc1);
        assertNotNull(enc2);
        assertNotNull(enc3);

        // Call enough times, should eventually get different messages
        boolean foundDifferent = false;
        for (int i = 0; i < 20; i++) {
            String enc = encouragementService.getStepEncouragement("questions");
            if (!enc.equals(enc1)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent, "Should get different encouragement messages with multiple calls");
    }

    @Test
    void testGetRelationshipFact() throws Exception {
        String fact = encouragementService.getRelationshipFact();

        assertNotNull(fact);
        assertFalse(fact.isEmpty());
        assertTrue(fact.length() > 20); // Should be a substantial fact
    }

    @Test
    void testGetRelationshipFact_Randomness() throws Exception {
        String fact1 = encouragementService.getRelationshipFact();

        boolean foundDifferent = false;
        for (int i = 0; i < 20; i++) {
            String fact = encouragementService.getRelationshipFact();
            if (!fact.equals(fact1)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent, "Should get different facts with multiple calls");
    }

    @Test
    void testGetPopCultureFact() throws Exception {
        String fact = encouragementService.getPopCultureFact();

        assertNotNull(fact);
        assertFalse(fact.isEmpty());
    }

    @Test
    void testGetPopCultureFact_Randomness() throws Exception {
        String fact1 = encouragementService.getPopCultureFact();

        boolean foundDifferent = false;
        for (int i = 0; i < 20; i++) {
            String fact = encouragementService.getPopCultureFact();
            if (!fact.equals(fact1)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent);
    }

    @Test
    void testGetHobbyInsight() throws Exception {
        String insight = encouragementService.getHobbyInsight();

        assertNotNull(insight);
        assertFalse(insight.isEmpty());
    }

    @Test
    void testGetHobbyInsight_Randomness() throws Exception {
        String insight1 = encouragementService.getHobbyInsight();

        boolean foundDifferent = false;
        for (int i = 0; i < 20; i++) {
            String insight = encouragementService.getHobbyInsight();
            if (!insight.equals(insight1)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent);
    }

    @Test
    void testGetCompletionMessage() throws Exception {
        String message = encouragementService.getCompletionMessage();

        assertNotNull(message);
        assertFalse(message.isEmpty());
        assertTrue(message.length() > 10);
    }

    @Test
    void testGetCompletionMessage_Randomness() throws Exception {
        String msg1 = encouragementService.getCompletionMessage();

        boolean foundDifferent = false;
        for (int i = 0; i < 20; i++) {
            String msg = encouragementService.getCompletionMessage();
            if (!msg.equals(msg1)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent);
    }

    @Test
    void testGetIntakeEncouragement_WithoutBirthday() throws Exception {
        User user = testUsers.get(0);

        Map<String, Object> encouragement = encouragementService.getIntakeEncouragement(user, "questions");

        assertNotNull(encouragement);
        assertTrue(encouragement.containsKey("stepEncouragement"));
        assertTrue(encouragement.containsKey("funFacts"));
        assertTrue(encouragement.containsKey("hobbyInsight"));
        assertTrue(encouragement.containsKey("progressMessage"));

        // Should not have life stats without birthday
        assertFalse(encouragement.containsKey("lifeStats"));

        @SuppressWarnings("unchecked")
        List<String> funFacts = (List<String>) encouragement.get("funFacts");
        assertTrue(funFacts.size() >= 2);
        assertTrue(funFacts.size() <= 3);
    }

    @Test
    void testGetIntakeEncouragement_WithBirthday() throws Exception {
        User user = testUsers.get(0);
        UserDates dates = new UserDates();
        dates.setDateOfBirth(Tools.ageToDate(25));
        user.setDates(dates);

        Map<String, Object> encouragement = encouragementService.getIntakeEncouragement(user, "questions");

        assertNotNull(encouragement);
        assertTrue(encouragement.containsKey("lifeStats"));

        @SuppressWarnings("unchecked")
        Map<String, Object> lifeStats = (Map<String, Object>) encouragement.get("lifeStats");
        assertFalse(lifeStats.isEmpty());
    }

    @Test
    void testGetIntakeEncouragement_DifferentSteps() throws Exception {
        User user = testUsers.get(0);

        Map<String, Object> questionsEnc = encouragementService.getIntakeEncouragement(user, "questions");
        Map<String, Object> videoEnc = encouragementService.getIntakeEncouragement(user, "video");
        Map<String, Object> photosEnc = encouragementService.getIntakeEncouragement(user, "photos");

        // All should have required fields
        assertNotNull(questionsEnc.get("stepEncouragement"));
        assertNotNull(videoEnc.get("stepEncouragement"));
        assertNotNull(photosEnc.get("stepEncouragement"));

        // Progress messages should be different
        assertNotEquals(questionsEnc.get("progressMessage"), videoEnc.get("progressMessage"));
        assertNotEquals(videoEnc.get("progressMessage"), photosEnc.get("progressMessage"));
    }

    @Test
    void testGetQuestionCategoryHint_Dealbreakers() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("dealbreakers_safety");

        assertNotNull(hint);
        assertTrue(hint.toLowerCase().contains("safe") || hint.toLowerCase().contains("honest"));
    }

    @Test
    void testGetQuestionCategoryHint_Values() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("values_politics");

        assertNotNull(hint);
        assertTrue(hint.toLowerCase().contains("value") || hint.toLowerCase().contains("compatibility"));
    }

    @Test
    void testGetQuestionCategoryHint_Relationship() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("relationship_dynamics");

        assertNotNull(hint);
        assertTrue(hint.toLowerCase().contains("communicate") || hint.toLowerCase().contains("relationship"));
    }

    @Test
    void testGetQuestionCategoryHint_Attachment() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("attachment_emotional");

        assertNotNull(hint);
        assertTrue(hint.toLowerCase().contains("attachment") || hint.toLowerCase().contains("style"));
    }

    @Test
    void testGetQuestionCategoryHint_Lifestyle() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("lifestyle_compatibility");

        assertNotNull(hint);
        assertFalse(hint.isEmpty());
    }

    @Test
    void testGetQuestionCategoryHint_Family() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("family_future");

        assertNotNull(hint);
        assertTrue(hint.toLowerCase().contains("life") || hint.toLowerCase().contains("goals"));
    }

    @Test
    void testGetQuestionCategoryHint_Intimacy() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("sex_intimacy");

        assertNotNull(hint);
        assertTrue(hint.toLowerCase().contains("compatibility") || hint.toLowerCase().contains("physical"));
    }

    @Test
    void testGetQuestionCategoryHint_Personality() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("personality_temperament");

        assertNotNull(hint);
        assertFalse(hint.isEmpty());
    }

    @Test
    void testGetQuestionCategoryHint_Hypotheticals() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("hypotheticals_scenarios");

        assertNotNull(hint);
        assertTrue(hint.toLowerCase().contains("situation") || hint.toLowerCase().contains("compatibility"));
    }

    @Test
    void testGetQuestionCategoryHint_Location() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("location_specific");

        assertNotNull(hint);
        assertFalse(hint.isEmpty());
    }

    @Test
    void testGetQuestionCategoryHint_Unknown() throws Exception {
        String hint = encouragementService.getQuestionCategoryHint("unknown_category");

        assertNotNull(hint);
        assertEquals("Your honest answer is the right answer.", hint);
    }

    @Test
    void testGetQuestionCategoryHint_CaseInsensitive() throws Exception {
        String hint1 = encouragementService.getQuestionCategoryHint("dealbreakers_safety");
        String hint2 = encouragementService.getQuestionCategoryHint("DEALBREAKERS_SAFETY");
        String hint3 = encouragementService.getQuestionCategoryHint("DeAlBrEaKeRs_SaFeTy");

        // Should all return the same hint (case insensitive)
        assertEquals(hint1, hint2);
        assertEquals(hint1, hint3);
    }

    @Test
    void testGetVideoTips() throws Exception {
        List<String> tips = encouragementService.getVideoTips();

        assertNotNull(tips);
        assertFalse(tips.isEmpty());
        assertTrue(tips.size() >= 5);

        // All tips should be non-empty
        for (String tip : tips) {
            assertNotNull(tip);
            assertFalse(tip.isEmpty());
        }
    }

    @Test
    void testGetPlatformStats() throws Exception {
        Map<String, Object> stats = encouragementService.getPlatformStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("averageQuestionsAnswered"));
        assertTrue(stats.containsKey("percentageFoundConnection"));
        assertTrue(stats.containsKey("averageMessagesBeforeMeeting"));
        assertTrue(stats.containsKey("mostPopularSharedHobby"));
        assertTrue(stats.containsKey("factoid"));

        // Verify data types
        assertTrue(stats.get("averageQuestionsAnswered") instanceof Integer);
        assertTrue(stats.get("percentageFoundConnection") instanceof Integer);
        assertTrue(stats.get("averageMessagesBeforeMeeting") instanceof Integer);
        assertTrue(stats.get("mostPopularSharedHobby") instanceof String);
        assertTrue(stats.get("factoid") instanceof String);

        // Verify reasonable values
        int avgQuestions = (int) stats.get("averageQuestionsAnswered");
        assertTrue(avgQuestions > 0);

        int percentage = (int) stats.get("percentageFoundConnection");
        assertTrue(percentage >= 0 && percentage <= 100);

        String factoid = (String) stats.get("factoid");
        assertFalse(factoid.isEmpty());
    }

    @Test
    void testProgressMessage_Questions() throws Exception {
        User user = testUsers.get(0);
        Map<String, Object> encouragement = encouragementService.getIntakeEncouragement(user, "questions");

        String progressMsg = (String) encouragement.get("progressMessage");
        assertNotNull(progressMsg);
        assertTrue(progressMsg.toLowerCase().contains("question"));
    }

    @Test
    void testProgressMessage_Video() throws Exception {
        User user = testUsers.get(0);
        Map<String, Object> encouragement = encouragementService.getIntakeEncouragement(user, "video");

        String progressMsg = (String) encouragement.get("progressMessage");
        assertNotNull(progressMsg);
        assertTrue(progressMsg.toLowerCase().contains("video"));
    }

    @Test
    void testProgressMessage_Photos() throws Exception {
        User user = testUsers.get(0);
        Map<String, Object> encouragement = encouragementService.getIntakeEncouragement(user, "photos");

        String progressMsg = (String) encouragement.get("progressMessage");
        assertNotNull(progressMsg);
        assertTrue(progressMsg.toLowerCase().contains("photo") || progressMsg.toLowerCase().contains("last"));
    }

    @Test
    void testProgressMessage_Complete() throws Exception {
        User user = testUsers.get(0);
        Map<String, Object> encouragement = encouragementService.getIntakeEncouragement(user, "complete");

        String progressMsg = (String) encouragement.get("progressMessage");
        assertNotNull(progressMsg);
        // Should be a completion message
        assertFalse(progressMsg.isEmpty());
    }

    @Test
    void testAllEncouragementVariety() throws Exception {
        // Test that we have variety in all encouragement types
        int iterations = 50;

        // Questions encouragement
        long uniqueQuestions = java.util.stream.IntStream.range(0, iterations)
                .mapToObj(i -> encouragementService.getStepEncouragement("questions"))
                .distinct()
                .count();
        assertTrue(uniqueQuestions > 1, "Should have variety in questions encouragement");

        // Relationship facts
        long uniqueFacts = java.util.stream.IntStream.range(0, iterations)
                .mapToObj(i -> encouragementService.getRelationshipFact())
                .distinct()
                .count();
        assertTrue(uniqueFacts > 1, "Should have variety in relationship facts");

        // Pop culture facts
        long uniquePopCulture = java.util.stream.IntStream.range(0, iterations)
                .mapToObj(i -> encouragementService.getPopCultureFact())
                .distinct()
                .count();
        assertTrue(uniquePopCulture > 1, "Should have variety in pop culture facts");

        // Hobby insights
        long uniqueHobbies = java.util.stream.IntStream.range(0, iterations)
                .mapToObj(i -> encouragementService.getHobbyInsight())
                .distinct()
                .count();
        assertTrue(uniqueHobbies > 1, "Should have variety in hobby insights");
    }
}
