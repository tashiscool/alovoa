package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent.BehaviorType;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.entity.user.UserReputationScore.TrustLevel;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserBehaviorEventRepository;
import com.nonononoki.alovoa.repo.UserReputationScoreRepository;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReputationServiceTest {

    @Autowired
    private ReputationService reputationService;

    @Autowired
    private UserReputationScoreRepository reputationRepo;

    @Autowired
    private UserBehaviorEventRepository behaviorRepo;

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
    void testGetOrCreateReputation_NewUser() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore reputation = reputationService.getOrCreateReputation(user);

        assertNotNull(reputation);
        assertEquals(user, reputation.getUser());
        assertEquals(50.0, reputation.getResponseQuality());
        assertEquals(50.0, reputation.getRespectScore());
        assertEquals(50.0, reputation.getAuthenticityScore());
        assertEquals(50.0, reputation.getInvestmentScore());
        assertEquals(TrustLevel.NEW_MEMBER, reputation.getTrustLevel());
    }

    @Test
    void testGetOrCreateReputation_ExistingUser() throws Exception {
        User user = testUsers.get(0);

        // Create initial reputation
        UserReputationScore initial = reputationService.getOrCreateReputation(user);
        Long initialId = initial.getId();

        // Get again - should return same record
        UserReputationScore second = reputationService.getOrCreateReputation(user);

        assertEquals(initialId, second.getId());
    }

    @Test
    void testRecordBehavior_PositiveBehavior() throws Exception {
        User user = testUsers.get(0);

        // Record a positive behavior
        reputationService.recordBehavior(user, BehaviorType.THOUGHTFUL_MESSAGE, null,
                Map.of("conversationId", 123));

        // Check event was recorded
        List<UserBehaviorEvent> events = behaviorRepo.findByUser(user);
        assertFalse(events.isEmpty());

        UserBehaviorEvent event = events.get(0);
        assertEquals(BehaviorType.THOUGHTFUL_MESSAGE, event.getBehaviorType());
        assertTrue(event.getReputationImpact() > 0);

        // Check reputation was updated
        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);
        assertTrue(reputation.getResponseQuality() > 50.0);
    }

    @Test
    void testRecordBehavior_NegativeBehavior() throws Exception {
        User user = testUsers.get(0);
        User targetUser = testUsers.get(1);

        // Record a negative behavior
        reputationService.recordBehavior(user, BehaviorType.GHOSTING, targetUser, null);

        // Check event was recorded
        List<UserBehaviorEvent> events = behaviorRepo.findByUser(user);
        assertFalse(events.isEmpty());

        UserBehaviorEvent event = events.get(0);
        assertEquals(BehaviorType.GHOSTING, event.getBehaviorType());
        assertEquals(targetUser, event.getTargetUser());
        assertTrue(event.getReputationImpact() < 0);

        // Check reputation was updated
        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);
        assertTrue(reputation.getRespectScore() < 50.0);
        assertEquals(1, reputation.getGhostingCount());
    }

    @Test
    void testRecordBehavior_VideoVerified() throws Exception {
        User user = testUsers.get(0);

        reputationService.recordBehavior(user, BehaviorType.VIDEO_VERIFIED, null,
                Map.of("verificationId", "abc123"));

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);
        // Video verification should boost authenticity score significantly
        assertTrue(reputation.getAuthenticityScore() > 50.0);
    }

    @Test
    void testRecordBehavior_CompletedDate() throws Exception {
        User user = testUsers.get(0);

        reputationService.recordBehavior(user, BehaviorType.COMPLETED_DATE, null, null);

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);
        assertTrue(reputation.getInvestmentScore() > 50.0);
        assertEquals(1, reputation.getDatesCompleted());
    }

    @Test
    void testRecordBehavior_ReportedBehavior() throws Exception {
        User user = testUsers.get(0);

        reputationService.recordBehavior(user, BehaviorType.REPORTED, null,
                Map.of("reason", "inappropriate"));

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);
        assertEquals(1, reputation.getReportsReceived());
    }

    @Test
    void testRecordBehavior_ReportUpheld() throws Exception {
        User user = testUsers.get(0);

        reputationService.recordBehavior(user, BehaviorType.REPORT_UPHELD, null, null);

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);
        assertEquals(1, reputation.getReportsUpheld());
        // Should have significant negative impact
        assertTrue(reputation.getRespectScore() < 45.0);
    }

    @Test
    void testRecordBehavior_DecayForRepeatedBehavior() throws Exception {
        User user = testUsers.get(0);

        // Record same positive behavior multiple times
        for (int i = 0; i < 5; i++) {
            reputationService.recordBehavior(user, BehaviorType.THOUGHTFUL_MESSAGE, null, null);
        }

        List<UserBehaviorEvent> events = behaviorRepo.findByUser(user);
        assertEquals(5, events.size());

        // Later events should have decayed impact
        double firstImpact = events.get(0).getReputationImpact();
        double lastImpact = events.get(events.size() - 1).getReputationImpact();

        // Due to decay, later impacts should be smaller
        assertTrue(lastImpact < firstImpact);
    }

    @Test
    void testGetRecentBehavior() throws Exception {
        User user = testUsers.get(0);

        // Record some behaviors
        reputationService.recordBehavior(user, BehaviorType.THOUGHTFUL_MESSAGE, null, null);
        reputationService.recordBehavior(user, BehaviorType.PROMPT_RESPONSE, null, null);
        reputationService.recordBehavior(user, BehaviorType.SCHEDULED_DATE, null, null);

        List<UserBehaviorEvent> recent = reputationService.getRecentBehavior(user, 7);

        assertEquals(3, recent.size());
    }

    @Test
    void testTrustLevelCalculation_NewMember() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore reputation = reputationService.getOrCreateReputation(user);

        // New user should be NEW_MEMBER
        assertEquals(TrustLevel.NEW_MEMBER, reputation.getTrustLevel());
    }

    @Test
    void testOverallScoreCalculation() throws Exception {
        User user = testUsers.get(0);

        // Record various behaviors to change scores
        reputationService.recordBehavior(user, BehaviorType.THOUGHTFUL_MESSAGE, null, null);
        reputationService.recordBehavior(user, BehaviorType.VIDEO_VERIFIED, null, null);
        reputationService.recordBehavior(user, BehaviorType.COMPLETED_DATE, null, null);

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);

        double overall = reputation.getOverallScore();
        // Overall should be weighted average of component scores
        assertTrue(overall > 50.0); // Should be positive due to positive behaviors
        assertTrue(overall <= 100.0);
    }

    @Test
    void testMisrepresentationImpact() throws Exception {
        User user = testUsers.get(0);

        reputationService.recordBehavior(user, BehaviorType.MISREPRESENTATION, null, null);

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);

        // Misrepresentation should have severe authenticity impact
        assertTrue(reputation.getAuthenticityScore() < 40.0);
    }

    @Test
    void testPositiveFeedbackImpact() throws Exception {
        User user = testUsers.get(0);

        reputationService.recordBehavior(user, BehaviorType.POSITIVE_FEEDBACK, null, null);

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);

        assertTrue(reputation.getInvestmentScore() > 50.0);
        assertEquals(1, reputation.getPositiveFeedbackCount());
    }

    @Test
    void testNoShowImpact() throws Exception {
        User user = testUsers.get(0);

        reputationService.recordBehavior(user, BehaviorType.NO_SHOW, null, null);

        UserReputationScore reputation = reputationRepo.findByUser(user).orElse(null);
        assertNotNull(reputation);

        // No-show should have significant negative impact on investment score
        assertTrue(reputation.getInvestmentScore() < 46.0);
    }
}
