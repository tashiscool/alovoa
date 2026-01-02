package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent.BehaviorType;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.entity.user.UserReputationScore.TrustLevel;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserBehaviorEventRepository;
import com.nonononoki.alovoa.repo.UserReputationScoreRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.*;
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
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReputationResourceTest {

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

    @Autowired
    private UserReputationScoreRepository reputationRepo;

    @Autowired
    private UserBehaviorEventRepository behaviorRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @Autowired
    private ReputationResource reputationResource;

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
    void testReputation_NewUser() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        ModelAndView mav = reputationResource.reputation();

        assertNotNull(mav);
        assertEquals("reputation", mav.getViewName());

        // New user should have default scores
        assertTrue(mav.getModel().containsKey("overallScore"));
        assertTrue(mav.getModel().containsKey("trustLevel"));
        assertTrue(mav.getModel().containsKey("badges"));
    }

    @Test
    void testReputation_WithScores() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create reputation score
        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(85.0);
        score.setRespectScore(90.0);
        score.setAuthenticityScore(75.0);
        score.setInvestmentScore(80.0);
        score.setTrustLevel(TrustLevel.TRUSTED);
        score.setDatesCompleted(3);
        score.setPositiveFeedbackCount(5);
        reputationRepo.save(score);

        ModelAndView mav = reputationResource.reputation();

        assertNotNull(mav);
        assertEquals(85.0, mav.getModel().get("responseQuality"));
        assertEquals(90.0, mav.getModel().get("respectScore"));
        assertEquals(75.0, mav.getModel().get("authenticityScore"));
        assertEquals(80.0, mav.getModel().get("investmentScore"));
        assertEquals(TrustLevel.TRUSTED.name(), mav.getModel().get("trustLevel"));
    }

    @Test
    void testReputation_WithRecentActivity() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create reputation score
        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(50.0);
        score.setRespectScore(50.0);
        score.setAuthenticityScore(50.0);
        score.setInvestmentScore(50.0);
        score.setTrustLevel(TrustLevel.NEW_MEMBER);
        reputationRepo.save(score);

        // Create some behavior events
        UserBehaviorEvent event1 = new UserBehaviorEvent();
        event1.setUser(user);
        event1.setBehaviorType(BehaviorType.THOUGHTFUL_MESSAGE);
        event1.setReputationImpact(0.5);
        event1.setCreatedAt(new Date());
        behaviorRepo.save(event1);

        UserBehaviorEvent event2 = new UserBehaviorEvent();
        event2.setUser(user);
        event2.setBehaviorType(BehaviorType.COMPLETED_DATE);
        event2.setReputationImpact(2.0);
        event2.setCreatedAt(new Date());
        behaviorRepo.save(event2);

        ModelAndView mav = reputationResource.reputation();

        assertNotNull(mav);
        assertTrue(mav.getModel().containsKey("recentActivity"));

        @SuppressWarnings("unchecked")
        List<UserBehaviorEvent> activity = (List<UserBehaviorEvent>) mav.getModel().get("recentActivity");
        assertFalse(activity.isEmpty());
    }

    @Test
    void testReputation_BadgesEarned() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create reputation with achievements
        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(95.0);  // High enough for badge
        score.setRespectScore(90.0);
        score.setAuthenticityScore(85.0);
        score.setInvestmentScore(80.0);
        score.setTrustLevel(TrustLevel.HIGHLY_TRUSTED);
        score.setDatesCompleted(10);  // Multiple completed dates
        score.setPositiveFeedbackCount(20);  // Many positive feedbacks
        reputationRepo.save(score);

        ModelAndView mav = reputationResource.reputation();

        assertNotNull(mav);
        assertTrue(mav.getModel().containsKey("badges"));

        @SuppressWarnings("unchecked")
        List<String> badges = (List<String>) mav.getModel().get("badges");
        // User should have earned some badges
        assertNotNull(badges);
    }
}
