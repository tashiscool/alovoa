package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.CompatibilityScore;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserDailyMatchLimit;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.*;
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
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MatchingServiceTest {

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private PoliticalAssessmentService politicalAssessmentService;

    @Autowired
    private UserDailyMatchLimitRepository matchLimitRepo;

    @Autowired
    private CompatibilityScoreRepository compatibilityRepo;

    @Autowired
    private UserPersonalityProfileRepository personalityRepo;

    @Autowired
    private UserPoliticalAssessmentRepository politicalAssessmentRepo;

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
    void testGetDailyMatches_NoAssessment() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        Map<String, Object> result = matchingService.getDailyMatches();

        assertNotNull(result);
        assertTrue((Boolean) result.get("gated"));
        assertTrue(result.containsKey("gateMessage"));
        assertTrue(result.containsKey("gateStatus"));
    }

    @Test
    void testGetDailyMatches_AssessmentNotApproved() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create assessment but don't complete it
        politicalAssessmentService.getOrCreateAssessment(user);

        Map<String, Object> result = matchingService.getDailyMatches();

        assertNotNull(result);
        assertTrue((Boolean) result.get("gated"));
    }

    @Test
    void testGetDailyMatches_Approved() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Complete assessment with approval
        setupApprovedAssessment(user);

        Map<String, Object> result = matchingService.getDailyMatches();

        assertNotNull(result);
        assertFalse(result.containsKey("gated"));
        assertTrue(result.containsKey("matches"));
        assertTrue(result.containsKey("remaining"));
        assertTrue(result.containsKey("dailyLimitReached"));
    }

    @Test
    void testGetDailyMatches_DailyLimitReached() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        setupApprovedAssessment(user);

        // Create a daily limit that's already maxed
        UserDailyMatchLimit limit = new UserDailyMatchLimit();
        limit.setUser(user);
        limit.setMatchDate(truncateToDay(new Date()));
        limit.setMatchesShown(10);  // Assuming default limit is 5
        limit.setMatchLimit(5);
        matchLimitRepo.save(limit);

        Map<String, Object> result = matchingService.getDailyMatches();

        assertNotNull(result);
        assertTrue((Boolean) result.get("dailyLimitReached"));
        assertEquals(0, result.get("remaining"));
        assertTrue(result.containsKey("resetsAt"));
    }

    @Test
    void testGetCompatibilityExplanation() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);

        // Create compatibility score
        CompatibilityScore score = new CompatibilityScore();
        score.setUserA(user1);
        score.setUserB(user2);
        score.setOverallScore(75.0);
        score.setValuesScore(80.0);
        score.setLifestyleScore(70.0);
        score.setPersonalityScore(75.0);
        score.setAttractionScore(72.0);
        score.setCircumstantialScore(68.0);
        score.setGrowthScore(85.0);
        compatibilityRepo.save(score);

        Map<String, Object> result = matchingService.getCompatibilityExplanation(user2.getUuid().toString());

        assertNotNull(result);
        assertEquals(75.0, result.get("overallScore"));
        assertTrue(result.containsKey("breakdown"));

        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) result.get("breakdown");
        assertEquals(80.0, breakdown.get("values"));
        assertEquals(70.0, breakdown.get("lifestyle"));
        assertEquals(75.0, breakdown.get("personality"));
    }

    @Test
    void testGetCompatibilityExplanation_CalculateNew() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);

        // Setup personality profiles
        setupPersonalityProfile(user1);
        setupPersonalityProfile(user2);

        // No existing compatibility - should calculate
        Map<String, Object> result = matchingService.getCompatibilityExplanation(user2.getUuid().toString());

        assertNotNull(result);
        assertTrue(result.containsKey("overallScore"));
        assertTrue(result.containsKey("breakdown"));

        // Should have created a compatibility record
        assertTrue(compatibilityRepo.findByUserAAndUserB(user1, user2).isPresent());
    }

    @Test
    void testGetCompatibilityExplanation_UserNotFound() {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        assertThrows(Exception.class, () ->
                matchingService.getCompatibilityExplanation("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void testDailyLimitCreation() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        setupApprovedAssessment(user);

        // First call should create daily limit
        matchingService.getDailyMatches();

        Date today = truncateToDay(new Date());
        UserDailyMatchLimit limit = matchLimitRepo.findByUserAndMatchDate(user, today).orElse(null);

        assertNotNull(limit);
        assertEquals(user, limit.getUser());
        assertNotNull(limit.getMatchLimit());
    }

    @Test
    void testPersonalityBasedCompatibility() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);

        // Setup similar personality profiles
        UserPersonalityProfile profile1 = new UserPersonalityProfile();
        profile1.setUser(user1);
        profile1.setOpenness(80.0);
        profile1.setConscientiousness(70.0);
        profile1.setExtraversion(60.0);
        profile1.setAgreeableness(75.0);
        profile1.setNeuroticism(30.0);
        profile1.setAssessmentCompletedAt(new Date());
        personalityRepo.save(profile1);

        UserPersonalityProfile profile2 = new UserPersonalityProfile();
        profile2.setUser(user2);
        profile2.setOpenness(85.0);  // Similar
        profile2.setConscientiousness(65.0);
        profile2.setExtraversion(55.0);
        profile2.setAgreeableness(80.0);
        profile2.setNeuroticism(25.0);
        profile2.setAssessmentCompletedAt(new Date());
        personalityRepo.save(profile2);

        Map<String, Object> result = matchingService.getCompatibilityExplanation(user2.getUuid().toString());

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) result.get("breakdown");

        // Similar personalities should have high compatibility
        Double personalityScore = (Double) breakdown.get("personality");
        assertTrue(personalityScore > 70);
    }

    @Test
    void testEconomicValuesIntegration() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);

        // Setup approved assessments with economic values
        setupApprovedAssessment(user1);
        setupApprovedAssessment(user2);

        // Update economic values scores
        UserPoliticalAssessment assessment1 = politicalAssessmentRepo.findByUser(user1).orElse(null);
        assessment1.setEconomicValuesScore(75.0);
        politicalAssessmentRepo.save(assessment1);

        UserPoliticalAssessment assessment2 = politicalAssessmentRepo.findByUser(user2).orElse(null);
        assessment2.setEconomicValuesScore(80.0);  // Similar values
        politicalAssessmentRepo.save(assessment2);

        Map<String, Object> result = matchingService.getCompatibilityExplanation(user2.getUuid().toString());

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) result.get("breakdown");

        // Similar economic values should have high compatibility
        Double valuesScore = (Double) breakdown.get("values");
        assertNotNull(valuesScore);
        assertTrue(valuesScore > 90);  // Very similar (only 5 point difference)
    }

    @Test
    void testRejectedUserCannotMatch() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Setup rejected assessment (capital class conservative)
        politicalAssessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_500K_PLUS,
                IncomeSource.CAPITAL_GAINS, WealthBracket.BRACKET_10M_PLUS, true, true, true);
        politicalAssessmentService.submitPoliticalValues(user, PoliticalOrientation.CONSERVATIVE,
                1, 1, 1, 1, 5, 5, null);
        politicalAssessmentService.completeAssessment(user);

        Map<String, Object> result = matchingService.getDailyMatches();

        assertTrue((Boolean) result.get("gated"));
        assertEquals("REJECTED", result.get("gateStatus"));
    }

    // Helper methods

    private void setupApprovedAssessment(User user) {
        politicalAssessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.UNDER_50K, false, false, false);
        politicalAssessmentService.submitPoliticalValues(user, PoliticalOrientation.PROGRESSIVE,
                5, 5, 5, 5, 1, 2, null);
        politicalAssessmentService.submitReproductiveView(user, ReproductiveRightsView.FULLY_PRO_CHOICE);
        politicalAssessmentService.completeAssessment(user);
    }

    private void setupPersonalityProfile(User user) {
        UserPersonalityProfile profile = new UserPersonalityProfile();
        profile.setUser(user);
        profile.setOpenness(70.0);
        profile.setConscientiousness(65.0);
        profile.setExtraversion(50.0);
        profile.setAgreeableness(70.0);
        profile.setNeuroticism(35.0);
        profile.setAssessmentCompletedAt(new Date());
        personalityRepo.save(profile);
    }

    private Date truncateToDay(Date date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
