package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.VideoDate;
import com.nonononoki.alovoa.entity.user.UserDailyMatchLimit;
import com.nonononoki.alovoa.entity.user.UserDates;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.entity.user.UserVideoVerification;
import com.nonononoki.alovoa.repo.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuraScheduleServiceTest {

    @Autowired
    private AuraScheduleService auraScheduleService;

    @Autowired
    private VideoDateRepository videoDateRepo;

    @Autowired
    private UserDailyMatchLimitRepository matchLimitRepo;

    @Autowired
    private UserReputationScoreRepository reputationRepo;

    @Autowired
    private UserVideoVerificationRepository verificationRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private ReputationService reputationService;

    private List<User> testUsers;
    private static final int firstNameLengthMax = 50;
    private static final int firstNameLengthMin = 2;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);
    }

    @AfterEach
    void after() throws Exception {
        videoDateRepo.deleteAll();
        matchLimitRepo.deleteAll();
        reputationRepo.deleteAll();
        verificationRepo.deleteAll();
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    // ====================
    // Video Date Proposal Expiration Tests
    // ====================

    @Test
    void testExpireOldProposals_ExpiredProposal() throws Exception {
        // Set proposal expiry hours to 48 (default)
        ReflectionTestUtils.setField(auraScheduleService, "proposalExpiryHours", 48);

        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create old proposed video date (50 hours ago)
        VideoDate oldProposal = createVideoDate(user1, user2, VideoDate.DateStatus.PROPOSED,
                Date.from(Instant.now().minus(50, ChronoUnit.HOURS)));
        videoDateRepo.save(oldProposal);

        // Create recent proposed video date (24 hours ago)
        VideoDate recentProposal = createVideoDate(user1, user2, VideoDate.DateStatus.PROPOSED,
                Date.from(Instant.now().minus(24, ChronoUnit.HOURS)));
        videoDateRepo.save(recentProposal);

        assertEquals(2, videoDateRepo.count());

        auraScheduleService.expireOldProposals();

        List<VideoDate> allDates = videoDateRepo.findAll();
        assertEquals(2, allDates.size());

        // Old proposal should be expired
        VideoDate expiredDate = videoDateRepo.findById(oldProposal.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.EXPIRED, expiredDate.getStatus());

        // Recent proposal should still be proposed
        VideoDate stillProposed = videoDateRepo.findById(recentProposal.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.PROPOSED, stillProposed.getStatus());
    }

    @Test
    void testExpireOldProposals_NoExpiredProposals() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create recent proposed video date
        VideoDate recentProposal = createVideoDate(user1, user2, VideoDate.DateStatus.PROPOSED,
                Date.from(Instant.now().minus(12, ChronoUnit.HOURS)));
        videoDateRepo.save(recentProposal);

        auraScheduleService.expireOldProposals();

        VideoDate date = videoDateRepo.findById(recentProposal.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.PROPOSED, date.getStatus());
    }

    @Test
    void testExpireOldProposals_OnlyExpiresProposedStatus() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create old dates with various statuses
        VideoDate acceptedDate = createVideoDate(user1, user2, VideoDate.DateStatus.ACCEPTED,
                Date.from(Instant.now().minus(50, ChronoUnit.HOURS)));
        videoDateRepo.save(acceptedDate);

        VideoDate scheduledDate = createVideoDate(user1, user2, VideoDate.DateStatus.SCHEDULED,
                Date.from(Instant.now().minus(50, ChronoUnit.HOURS)));
        videoDateRepo.save(scheduledDate);

        auraScheduleService.expireOldProposals();

        // These should not be expired (only PROPOSED status gets expired)
        VideoDate acceptedCheck = videoDateRepo.findById(acceptedDate.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.ACCEPTED, acceptedCheck.getStatus());

        VideoDate scheduledCheck = videoDateRepo.findById(scheduledDate.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.SCHEDULED, scheduledCheck.getStatus());
    }

    // ====================
    // Missed Video Dates Tests
    // ====================

    @Test
    void testHandleMissedVideoDates_ScheduledDateMissed() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create a scheduled date from 40 minutes ago
        VideoDate missedDate = createVideoDate(user1, user2, VideoDate.DateStatus.SCHEDULED,
                Date.from(Instant.now().minus(40, ChronoUnit.MINUTES)));
        videoDateRepo.save(missedDate);

        auraScheduleService.handleMissedVideoDates();

        VideoDate updated = videoDateRepo.findById(missedDate.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.EXPIRED, updated.getStatus());
    }

    @Test
    void testHandleMissedVideoDates_AcceptedDateMissed() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create an accepted date from 40 minutes ago
        VideoDate missedDate = createVideoDate(user1, user2, VideoDate.DateStatus.ACCEPTED,
                Date.from(Instant.now().minus(40, ChronoUnit.MINUTES)));
        videoDateRepo.save(missedDate);

        auraScheduleService.handleMissedVideoDates();

        VideoDate updated = videoDateRepo.findById(missedDate.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.EXPIRED, updated.getStatus());
    }

    @Test
    void testHandleMissedVideoDates_RecentDateNotMissed() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create a scheduled date from 15 minutes ago (should not be marked as missed)
        VideoDate recentDate = createVideoDate(user1, user2, VideoDate.DateStatus.SCHEDULED,
                Date.from(Instant.now().minus(15, ChronoUnit.MINUTES)));
        videoDateRepo.save(recentDate);

        auraScheduleService.handleMissedVideoDates();

        VideoDate updated = videoDateRepo.findById(recentDate.getId()).orElseThrow();
        assertEquals(VideoDate.DateStatus.SCHEDULED, updated.getStatus());
    }

    // ====================
    // Trust Level Recalculation Tests
    // ====================

    @Test
    void testRecalculateTrustLevels_UpdatesChangedLevels() throws Exception {
        User user = testUsers.get(0);

        // Create reputation score that should be VERIFIED
        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(60.0);
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(60.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.NEW_MEMBER);
        reputationRepo.save(score);

        // Set account age to > 30 days
        UserDates dates = user.getDates();
        dates.setCreationDate(Date.from(Instant.now().minus(60, ChronoUnit.DAYS)));
        user.setDates(dates);
        userRepo.save(user);

        auraScheduleService.recalculateTrustLevels();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        assertEquals(UserReputationScore.TrustLevel.VERIFIED, updated.getTrustLevel());
    }

    @Test
    void testRecalculateTrustLevels_RestrictedDueToReports() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(70.0);
        score.setRespectScore(70.0);
        score.setAuthenticityScore(70.0);
        score.setInvestmentScore(70.0);
        score.setReportsUpheld(2); // Should trigger RESTRICTED
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        auraScheduleService.recalculateTrustLevels();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        assertEquals(UserReputationScore.TrustLevel.RESTRICTED, updated.getTrustLevel());
    }

    @Test
    void testRecalculateTrustLevels_NewMemberDueToAccountAge() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(70.0);
        score.setRespectScore(70.0);
        score.setAuthenticityScore(70.0);
        score.setInvestmentScore(70.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set account age to < 30 days
        UserDates dates = user.getDates();
        dates.setCreationDate(Date.from(Instant.now().minus(15, ChronoUnit.DAYS)));
        user.setDates(dates);
        userRepo.save(user);

        auraScheduleService.recalculateTrustLevels();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        assertEquals(UserReputationScore.TrustLevel.NEW_MEMBER, updated.getTrustLevel());
    }

    // ====================
    // Match Limit Cleanup Tests
    // ====================

    @Test
    void testCleanOldMatchLimits_DeletesOldEntries() throws Exception {
        User user = testUsers.get(0);

        // Create old match limit (40 days ago)
        UserDailyMatchLimit oldLimit = new UserDailyMatchLimit();
        oldLimit.setUser(user);
        oldLimit.setMatchDate(Date.from(Instant.now().minus(40, ChronoUnit.DAYS)));
        oldLimit.setMatchesShown(3);
        matchLimitRepo.save(oldLimit);

        // Create recent match limit (10 days ago)
        UserDailyMatchLimit recentLimit = new UserDailyMatchLimit();
        recentLimit.setUser(user);
        recentLimit.setMatchDate(Date.from(Instant.now().minus(10, ChronoUnit.DAYS)));
        recentLimit.setMatchesShown(2);
        matchLimitRepo.save(recentLimit);

        assertEquals(2, matchLimitRepo.count());

        auraScheduleService.cleanOldMatchLimits();

        List<UserDailyMatchLimit> remaining = matchLimitRepo.findAll();
        assertEquals(1, remaining.size());
        assertEquals(recentLimit.getId(), remaining.get(0).getId());
    }

    @Test
    void testCleanOldMatchLimits_NoOldEntries() throws Exception {
        User user = testUsers.get(0);

        // Create recent match limit
        UserDailyMatchLimit recentLimit = new UserDailyMatchLimit();
        recentLimit.setUser(user);
        recentLimit.setMatchDate(Date.from(Instant.now().minus(5, ChronoUnit.DAYS)));
        recentLimit.setMatchesShown(2);
        matchLimitRepo.save(recentLimit);

        assertEquals(1, matchLimitRepo.count());

        auraScheduleService.cleanOldMatchLimits();

        assertEquals(1, matchLimitRepo.count());
    }

    // ====================
    // Verification Session Expiration Tests
    // ====================

    @Test
    void testExpireVerificationSessions_ExpiresPendingSessions() throws Exception {
        User user = testUsers.get(0);

        // Create old pending verification (2 hours ago)
        UserVideoVerification oldVerification = new UserVideoVerification();
        oldVerification.setUser(user);
        oldVerification.setStatus(UserVideoVerification.VerificationStatus.PENDING);
        oldVerification.setCreatedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        verificationRepo.save(oldVerification);

        auraScheduleService.expireVerificationSessions();

        UserVideoVerification updated = verificationRepo.findById(oldVerification.getId()).orElseThrow();
        assertEquals(UserVideoVerification.VerificationStatus.EXPIRED, updated.getStatus());
    }

    @Test
    void testExpireVerificationSessions_DoesNotExpireRecentSessions() throws Exception {
        User user = testUsers.get(0);

        // Create recent pending verification (30 minutes ago)
        UserVideoVerification recentVerification = new UserVideoVerification();
        recentVerification.setUser(user);
        recentVerification.setStatus(UserVideoVerification.VerificationStatus.PENDING);
        recentVerification.setCreatedAt(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        verificationRepo.save(recentVerification);

        auraScheduleService.expireVerificationSessions();

        UserVideoVerification updated = verificationRepo.findById(recentVerification.getId()).orElseThrow();
        assertEquals(UserVideoVerification.VerificationStatus.PENDING, updated.getStatus());
    }

    @Test
    void testExpireVerificationSessions_OnlyExpiresPendingStatus() throws Exception {
        User user = testUsers.get(0);

        // Create old verified session (should not be expired)
        UserVideoVerification verifiedSession = new UserVideoVerification();
        verifiedSession.setUser(user);
        verifiedSession.setStatus(UserVideoVerification.VerificationStatus.VERIFIED);
        verifiedSession.setCreatedAt(Date.from(Instant.now().minus(5, ChronoUnit.HOURS)));
        verificationRepo.save(verifiedSession);

        auraScheduleService.expireVerificationSessions();

        UserVideoVerification updated = verificationRepo.findById(verifiedSession.getId()).orElseThrow();
        assertEquals(UserVideoVerification.VerificationStatus.VERIFIED, updated.getStatus());
    }

    // ====================
    // Inactivity Decay Tests
    // ====================

    @Test
    void testApplyInactivityDecay_DecaysInactiveUser30to60Days() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(60.0);
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(60.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set last active date to 45 days ago
        UserDates dates = user.getDates();
        dates.setActiveDate(Date.from(Instant.now().minus(45, ChronoUnit.DAYS)));
        dates.setCreationDate(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        user.setDates(dates);
        userRepo.save(user);

        auraScheduleService.applyInactivityDecay();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        // Should decay by 0.5
        assertEquals(59.5, updated.getResponseQuality(), 0.01);
        assertEquals(59.75, updated.getInvestmentScore(), 0.01); // 0.25 decay (half of 0.5)
    }

    @Test
    void testApplyInactivityDecay_DecaysInactiveUser60to90Days() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(60.0);
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(60.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set last active date to 75 days ago
        UserDates dates = user.getDates();
        dates.setActiveDate(Date.from(Instant.now().minus(75, ChronoUnit.DAYS)));
        dates.setCreationDate(Date.from(Instant.now().minus(150, ChronoUnit.DAYS)));
        user.setDates(dates);
        userRepo.save(user);

        auraScheduleService.applyInactivityDecay();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        // Should decay by 1.0
        assertEquals(59.0, updated.getResponseQuality(), 0.01);
        assertEquals(59.5, updated.getInvestmentScore(), 0.01); // 0.5 decay (half of 1.0)
    }

    @Test
    void testApplyInactivityDecay_DecaysInactiveUserOver90Days() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(60.0);
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(60.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set last active date to 100 days ago
        UserDates dates = user.getDates();
        dates.setActiveDate(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        dates.setCreationDate(Date.from(Instant.now().minus(200, ChronoUnit.DAYS)));
        user.setDates(dates);
        userRepo.save(user);

        auraScheduleService.applyInactivityDecay();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        // Should decay by 1.5
        assertEquals(58.5, updated.getResponseQuality(), 0.01);
        assertEquals(59.25, updated.getInvestmentScore(), 0.01); // 0.75 decay (half of 1.5)
    }

    @Test
    void testApplyInactivityDecay_DoesNotDecayActiveUsers() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(60.0);
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(60.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set last active date to 10 days ago (active user)
        UserDates dates = user.getDates();
        dates.setActiveDate(Date.from(Instant.now().minus(10, ChronoUnit.DAYS)));
        dates.setCreationDate(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        user.setDates(dates);
        userRepo.save(user);

        auraScheduleService.applyInactivityDecay();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        // No decay should occur
        assertEquals(60.0, updated.getResponseQuality(), 0.01);
        assertEquals(60.0, updated.getInvestmentScore(), 0.01);
    }

    @Test
    void testApplyInactivityDecay_EnforcesFloor() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(25.5); // Close to floor
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(25.5);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set last active date to 100 days ago
        UserDates dates = user.getDates();
        dates.setActiveDate(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        dates.setCreationDate(Date.from(Instant.now().minus(200, ChronoUnit.DAYS)));
        user.setDates(dates);
        userRepo.save(user);

        auraScheduleService.applyInactivityDecay();

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        // Should be floored at 25.0
        assertEquals(25.0, updated.getResponseQuality(), 0.01);
        assertEquals(25.0, updated.getInvestmentScore(), 0.01);
    }

    @Test
    void testApplyInactivityDecay_HandlesNullDatesGracefully() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(60.0);
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(60.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set dates to null
        user.setDates(null);
        userRepo.save(user);

        // Should not throw exception
        assertDoesNotThrow(() -> auraScheduleService.applyInactivityDecay());
    }

    @Test
    void testApplyInactivityDecay_HandlesNullActiveDateGracefully() throws Exception {
        User user = testUsers.get(0);

        UserReputationScore score = new UserReputationScore();
        score.setUser(user);
        score.setResponseQuality(60.0);
        score.setRespectScore(60.0);
        score.setAuthenticityScore(60.0);
        score.setInvestmentScore(60.0);
        score.setTrustLevel(UserReputationScore.TrustLevel.VERIFIED);
        reputationRepo.save(score);

        // Set active date to null
        UserDates dates = user.getDates();
        dates.setActiveDate(null);
        user.setDates(dates);
        userRepo.save(user);

        // Should not throw exception and should not decay (treated as active)
        assertDoesNotThrow(() -> auraScheduleService.applyInactivityDecay());

        UserReputationScore updated = reputationRepo.findByUser(user).orElseThrow();
        assertEquals(60.0, updated.getResponseQuality(), 0.01);
    }

    // ====================
    // Ghosting Detection Tests
    // ====================

    @Test
    void testDetectGhostingBehavior_RunsWithoutError() throws Exception {
        // Ghosting detection is currently simplified, just ensure it doesn't throw
        assertDoesNotThrow(() -> auraScheduleService.detectGhostingBehavior());
    }

    // ====================
    // Helper Methods
    // ====================

    private VideoDate createVideoDate(User userA, User userB, VideoDate.DateStatus status, Date scheduledAt) {
        VideoDate videoDate = new VideoDate();
        videoDate.setUserA(userA);
        videoDate.setUserB(userB);
        videoDate.setStatus(status);
        videoDate.setScheduledAt(scheduledAt);
        videoDate.setCreatedAt(new Date());
        return videoDate;
    }
}
