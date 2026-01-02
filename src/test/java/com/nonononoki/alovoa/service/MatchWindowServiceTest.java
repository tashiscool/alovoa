package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.CompatibilityScore;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.MatchWindow;
import com.nonononoki.alovoa.entity.MatchWindow.WindowStatus;
import com.nonononoki.alovoa.entity.User;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MatchWindowServiceTest {

    @Autowired
    private MatchWindowService matchWindowService;

    @Autowired
    private MatchWindowRepository matchWindowRepo;

    @Autowired
    private CompatibilityScoreRepository compatibilityRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepo;

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

    // ============================================
    // Creating Match Windows
    // ============================================

    @Test
    void testCreateWindow_Success() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);
        Double compatScore = 0.85;

        MatchWindow window = matchWindowService.createWindow(userA, userB, compatScore);

        assertNotNull(window);
        assertNotNull(window.getUuid());
        assertEquals(userA.getId(), window.getUserA().getId());
        assertEquals(userB.getId(), window.getUserB().getId());
        assertEquals(compatScore, window.getCompatibilityScore());
        assertEquals(WindowStatus.PENDING_BOTH, window.getStatus());
        assertFalse(window.isUserAConfirmed());
        assertFalse(window.isUserBConfirmed());
        assertNotNull(window.getExpiresAt());

        // Verify it was saved
        Optional<MatchWindow> saved = matchWindowRepo.findByUuid(window.getUuid());
        assertTrue(saved.isPresent());
    }

    @Test
    void testCreateWindow_DuplicateThrowsException() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        // Create first window
        matchWindowService.createWindow(userA, userB, 0.85);

        // Try to create duplicate
        Exception exception = assertThrows(Exception.class, () -> {
            matchWindowService.createWindow(userA, userB, 0.90);
        });

        assertTrue(exception.getMessage().contains("Active match window already exists"));
    }

    @Test
    void testCreateWindow_InitialExpirationSet() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        assertNotNull(window.getExpiresAt());

        // Should expire in approximately 24 hours
        long hoursUntilExpiry = (window.getExpiresAt().getTime() - System.currentTimeMillis()) / (60 * 60 * 1000);
        assertTrue(hoursUntilExpiry >= 23 && hoursUntilExpiry <= 25);
    }

    @Test
    void testCreateWindowsForHighMatches_CreatesMultipleWindows() throws Exception {
        User user = testUsers.get(0);
        User match1 = testUsers.get(1);
        User match2 = testUsers.get(2);

        // Create compatibility scores
        CompatibilityScore score1 = new CompatibilityScore();
        score1.setUserA(user);
        score1.setUserB(match1);
        score1.setOverallScore(0.90);
        compatibilityRepo.save(score1);

        CompatibilityScore score2 = new CompatibilityScore();
        score2.setUserA(user);
        score2.setUserB(match2);
        score2.setOverallScore(0.85);
        compatibilityRepo.save(score2);

        List<MatchWindow> windows = matchWindowService.createWindowsForHighMatches(user, 0.80);

        assertEquals(2, windows.size());
    }

    @Test
    void testCreateWindowsForHighMatches_SkipsLowScores() throws Exception {
        User user = testUsers.get(0);
        User match1 = testUsers.get(1);

        // Create low compatibility score
        CompatibilityScore score1 = new CompatibilityScore();
        score1.setUserA(user);
        score1.setUserB(match1);
        score1.setOverallScore(0.60);
        compatibilityRepo.save(score1);

        List<MatchWindow> windows = matchWindowService.createWindowsForHighMatches(user, 0.80);

        assertEquals(0, windows.size());
    }

    @Test
    void testCreateWindowsForHighMatches_SkipsExistingWindows() throws Exception {
        User user = testUsers.get(0);
        User match1 = testUsers.get(1);

        // Create compatibility score
        CompatibilityScore score1 = new CompatibilityScore();
        score1.setUserA(user);
        score1.setUserB(match1);
        score1.setOverallScore(0.90);
        compatibilityRepo.save(score1);

        // Create window manually first
        matchWindowService.createWindow(user, match1, 0.90);

        // Try to create windows again
        List<MatchWindow> windows = matchWindowService.createWindowsForHighMatches(user, 0.80);

        assertEquals(0, windows.size());
    }

    // ============================================
    // User Actions - Confirm Interest
    // ============================================

    @Test
    void testConfirmInterest_FirstUserConfirms() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        MatchWindow confirmed = matchWindowService.confirmInterest(window.getUuid());

        assertTrue(confirmed.isUserAConfirmed());
        assertFalse(confirmed.isUserBConfirmed());
        assertNotNull(confirmed.getUserAConfirmedAt());
        assertEquals(WindowStatus.PENDING_USER_B, confirmed.getStatus());
    }

    @Test
    void testConfirmInterest_BothUsersConfirm() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // User A confirms
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        matchWindowService.confirmInterest(window.getUuid());

        // User B confirms
        Mockito.doReturn(userB).when(authService).getCurrentUser(true);
        MatchWindow confirmed = matchWindowService.confirmInterest(window.getUuid());

        assertTrue(confirmed.isUserAConfirmed());
        assertTrue(confirmed.isUserBConfirmed());
        assertTrue(confirmed.isBothConfirmed());
        assertEquals(WindowStatus.CONFIRMED, confirmed.getStatus());
        assertNotNull(confirmed.getConversation());
    }

    @Test
    void testConfirmInterest_CreatesConversation() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // Both users confirm
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        matchWindowService.confirmInterest(window.getUuid());

        Mockito.doReturn(userB).when(authService).getCurrentUser(true);
        MatchWindow confirmed = matchWindowService.confirmInterest(window.getUuid());

        assertNotNull(confirmed.getConversation());

        // Verify conversation exists
        Optional<Conversation> conversation = conversationRepo.findById(confirmed.getConversation().getId());
        assertTrue(conversation.isPresent());
    }

    @Test
    void testConfirmInterest_ExpiredWindowThrowsException() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // Manually expire the window
        window.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
        matchWindowRepo.save(window);

        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        Exception exception = assertThrows(Exception.class, () -> {
            matchWindowService.confirmInterest(window.getUuid());
        });

        assertTrue(exception.getMessage().contains("expired"));
    }

    @Test
    void testConfirmInterest_InvalidUserThrowsException() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);
        User userC = testUsers.get(2);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);
        Mockito.doReturn(userC).when(authService).getCurrentUser(true);

        Exception exception = assertThrows(Exception.class, () -> {
            matchWindowService.confirmInterest(window.getUuid());
        });

        assertTrue(exception.getMessage().contains("not part of this match"));
    }

    // ============================================
    // User Actions - Decline Match
    // ============================================

    @Test
    void testDeclineMatch_UserADeclines() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        MatchWindow declined = matchWindowService.declineMatch(window.getUuid());

        assertEquals(WindowStatus.DECLINED_BY_A, declined.getStatus());
    }

    @Test
    void testDeclineMatch_UserBDeclines() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);
        Mockito.doReturn(userB).when(authService).getCurrentUser(true);

        MatchWindow declined = matchWindowService.declineMatch(window.getUuid());

        assertEquals(WindowStatus.DECLINED_BY_B, declined.getStatus());
    }

    // ============================================
    // User Actions - Extension
    // ============================================

    @Test
    void testRequestExtension_Success() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);
        Date originalExpiry = window.getExpiresAt();

        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        MatchWindow extended = matchWindowService.requestExtension(window.getUuid());

        assertTrue(extended.isExtensionUsed());
        assertEquals(userA.getId(), extended.getExtensionRequestedBy().getId());
        assertTrue(extended.getExpiresAt().after(originalExpiry));

        // Should add 12 hours
        long extensionHours = (extended.getExpiresAt().getTime() - originalExpiry.getTime()) / (60 * 60 * 1000);
        assertEquals(MatchWindow.EXTENSION_HOURS, extensionHours);
    }

    @Test
    void testRequestExtension_CannotExtendTwice() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        // First extension works
        matchWindowService.requestExtension(window.getUuid());

        // Second extension should fail
        Exception exception = assertThrows(Exception.class, () -> {
            matchWindowService.requestExtension(window.getUuid());
        });

        assertTrue(exception.getMessage().contains("cannot be extended"));
    }

    @Test
    void testRequestExtension_ExpiredWindowThrowsException() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // Manually expire the window
        window.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
        matchWindowRepo.save(window);

        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        Exception exception = assertThrows(Exception.class, () -> {
            matchWindowService.requestExtension(window.getUuid());
        });

        assertTrue(exception.getMessage().contains("expired"));
    }

    // ============================================
    // Query Methods
    // ============================================

    @Test
    void testGetPendingDecisions() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);
        User userC = testUsers.get(2);

        // Create windows for user A
        matchWindowService.createWindow(userA, userB, 0.85);
        matchWindowService.createWindow(userA, userC, 0.90);

        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        List<MatchWindow> pending = matchWindowService.getPendingDecisions();

        assertEquals(2, pending.size());
    }

    @Test
    void testGetPendingDecisions_AfterConfirmation() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        // Should have 1 pending
        assertEquals(1, matchWindowService.getPendingDecisions().size());

        // Confirm
        matchWindowService.confirmInterest(window.getUuid());

        // Should have 0 pending for user A now
        assertEquals(0, matchWindowService.getPendingDecisions().size());
    }

    @Test
    void testGetWaitingMatches() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // User A confirms
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        matchWindowService.confirmInterest(window.getUuid());

        // User A should see it in waiting matches
        List<MatchWindow> waiting = matchWindowService.getWaitingMatches();
        assertEquals(1, waiting.size());

        // User B should not see it in waiting (they haven't confirmed yet)
        Mockito.doReturn(userB).when(authService).getCurrentUser(true);
        assertEquals(0, matchWindowService.getWaitingMatches().size());
    }

    @Test
    void testGetConfirmedMatches() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // Both confirm
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        matchWindowService.confirmInterest(window.getUuid());

        Mockito.doReturn(userB).when(authService).getCurrentUser(true);
        matchWindowService.confirmInterest(window.getUuid());

        // Both should see it in confirmed
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        assertEquals(1, matchWindowService.getConfirmedMatches().size());

        Mockito.doReturn(userB).when(authService).getCurrentUser(true);
        assertEquals(1, matchWindowService.getConfirmedMatches().size());
    }

    @Test
    void testGetPendingCount() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);
        User userC = testUsers.get(2);

        Mockito.doReturn(userA).when(authService).getCurrentUser(true);

        // Initially 0
        assertEquals(0, matchWindowService.getPendingCount());

        // Create windows
        matchWindowService.createWindow(userA, userB, 0.85);
        matchWindowService.createWindow(userA, userC, 0.90);

        // Should be 2
        assertEquals(2, matchWindowService.getPendingCount());
    }

    @Test
    void testGetWindow_Success() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        Optional<MatchWindow> retrieved = matchWindowService.getWindow(window.getUuid());

        assertTrue(retrieved.isPresent());
        assertEquals(window.getId(), retrieved.get().getId());
    }

    @Test
    void testGetWindow_NotFound() throws Exception {
        Optional<MatchWindow> retrieved = matchWindowService.getWindow(UUID.randomUUID());
        assertFalse(retrieved.isPresent());
    }

    // ============================================
    // Scheduled Tasks
    // ============================================

    @Test
    void testExpireWindows() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // Manually set expiry to past
        window.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
        matchWindowRepo.save(window);

        // Run expiration task
        matchWindowService.expireWindows();

        // Verify window is expired
        MatchWindow expired = matchWindowRepo.findByUuid(window.getUuid()).get();
        assertEquals(WindowStatus.EXPIRED, expired.getStatus());
    }

    @Test
    void testExpireWindows_DoesNotExpireActiveWindows() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // Run expiration task (should not affect this window)
        matchWindowService.expireWindows();

        // Verify window is still pending
        MatchWindow stillActive = matchWindowRepo.findByUuid(window.getUuid()).get();
        assertEquals(WindowStatus.PENDING_BOTH, stillActive.getStatus());
    }

    @Test
    void testExpireWindows_OneUserConfirmedIsGhosting() throws Exception {
        User userA = testUsers.get(0);
        User userB = testUsers.get(1);

        MatchWindow window = matchWindowService.createWindow(userA, userB, 0.85);

        // User A confirms
        Mockito.doReturn(userA).when(authService).getCurrentUser(true);
        matchWindowService.confirmInterest(window.getUuid());

        // Expire the window
        window = matchWindowRepo.findByUuid(window.getUuid()).get();
        window.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
        matchWindowRepo.save(window);

        // Run expiration task (should record ghosting behavior for user B)
        matchWindowService.expireWindows();

        // Verify window is expired
        MatchWindow expired = matchWindowRepo.findByUuid(window.getUuid()).get();
        assertEquals(WindowStatus.EXPIRED, expired.getStatus());
    }
}
