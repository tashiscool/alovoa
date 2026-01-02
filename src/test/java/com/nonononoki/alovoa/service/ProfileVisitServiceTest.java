package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserProfileVisit;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserProfileVisitRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileVisitServiceTest {

    @Autowired
    private ProfileVisitService profileVisitService;

    @Autowired
    private UserProfileVisitRepository visitRepository;

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

    // ============================================
    // Recording Profile Visits
    // ============================================

    @Test
    void testRecordVisit_NewVisit_ShouldCreateNewRecord() throws Exception {
        User visitor = testUsers.get(0);
        User visitedUser = testUsers.get(1);

        long countBefore = visitRepository.count();
        profileVisitService.recordVisit(visitor, visitedUser);
        long countAfter = visitRepository.count();

        assertEquals(countBefore + 1, countAfter);

        Optional<UserProfileVisit> visit = visitRepository.findByVisitorAndVisitedUser(visitor, visitedUser);
        assertTrue(visit.isPresent());
        assertEquals(1, visit.get().getVisitCount());
        assertNotNull(visit.get().getVisitedAt());
        assertNotNull(visit.get().getLastVisitAt());
    }

    @Test
    void testRecordVisit_ExistingVisit_ShouldUpdateCount() throws InterruptedException {
        User visitor = testUsers.get(0);
        User visitedUser = testUsers.get(1);

        // First visit
        profileVisitService.recordVisit(visitor, visitedUser);
        Optional<UserProfileVisit> firstVisit = visitRepository.findByVisitorAndVisitedUser(visitor, visitedUser);
        assertTrue(firstVisit.isPresent());
        assertEquals(1, firstVisit.get().getVisitCount());
        Date firstVisitTime = firstVisit.get().getLastVisitAt();

        // Wait a bit to ensure timestamp changes
        Thread.sleep(100);

        // Second visit
        long countBefore = visitRepository.count();
        profileVisitService.recordVisit(visitor, visitedUser);
        long countAfter = visitRepository.count();

        // Should not create a new record
        assertEquals(countBefore, countAfter);

        Optional<UserProfileVisit> secondVisit = visitRepository.findByVisitorAndVisitedUser(visitor, visitedUser);
        assertTrue(secondVisit.isPresent());
        assertEquals(2, secondVisit.get().getVisitCount());
        assertTrue(secondVisit.get().getLastVisitAt().after(firstVisitTime));
    }

    @Test
    void testRecordVisit_SelfVisit_ShouldNotRecord() throws Exception {
        User user = testUsers.get(0);

        long countBefore = visitRepository.count();
        profileVisitService.recordVisit(user, user);
        long countAfter = visitRepository.count();

        // Should not create a record for self-visits
        assertEquals(countBefore, countAfter);
    }

    @Test
    void testRecordVisit_MultipleVisitors_ShouldTrackSeparately() throws Exception {
        User visitedUser = testUsers.get(0);
        User visitor1 = testUsers.get(1);
        User visitor2 = testUsers.get(2);

        profileVisitService.recordVisit(visitor1, visitedUser);
        profileVisitService.recordVisit(visitor2, visitedUser);

        long visitorCount = visitRepository.countByVisitedUser(visitedUser);
        assertEquals(2, visitorCount);
    }

    // ============================================
    // Getting Visitors List
    // ============================================

    @Test
    void testGetMyVisitors_ShouldReturnPagedResults() throws Exception {
        User currentUser = testUsers.get(0);
        User visitor1 = testUsers.get(1);
        User visitor2 = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);

        // Record visits
        profileVisitService.recordVisit(visitor1, currentUser);
        profileVisitService.recordVisit(visitor2, currentUser);

        Page<UserProfileVisit> visitors = profileVisitService.getMyVisitors(0, 10);

        assertNotNull(visitors);
        assertEquals(2, visitors.getTotalElements());
        assertTrue(visitors.getContent().stream()
                .anyMatch(v -> v.getVisitor().getId().equals(visitor1.getId())));
        assertTrue(visitors.getContent().stream()
                .anyMatch(v -> v.getVisitor().getId().equals(visitor2.getId())));
    }

    @Test
    void testGetMyVisitors_ShouldOrderByMostRecent() throws Exception {
        User currentUser = testUsers.get(0);
        User visitor1 = testUsers.get(1);
        User visitor2 = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);

        // Record first visit
        profileVisitService.recordVisit(visitor1, currentUser);
        Thread.sleep(100);

        // Record second visit (more recent)
        profileVisitService.recordVisit(visitor2, currentUser);

        Page<UserProfileVisit> visitors = profileVisitService.getMyVisitors(0, 10);

        assertEquals(2, visitors.getTotalElements());
        // Most recent should be first
        assertEquals(visitor2.getId(), visitors.getContent().get(0).getVisitor().getId());
        assertEquals(visitor1.getId(), visitors.getContent().get(1).getVisitor().getId());
    }

    @Test
    void testGetMyVisitors_Pagination_ShouldWork() throws Exception {
        User currentUser = testUsers.get(0);
        User visitor1 = testUsers.get(1);
        User visitor2 = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);

        profileVisitService.recordVisit(visitor1, currentUser);
        profileVisitService.recordVisit(visitor2, currentUser);

        // Get page with size 1
        Page<UserProfileVisit> page1 = profileVisitService.getMyVisitors(0, 1);
        assertEquals(1, page1.getContent().size());
        assertEquals(2, page1.getTotalElements());

        Page<UserProfileVisit> page2 = profileVisitService.getMyVisitors(1, 1);
        assertEquals(1, page2.getContent().size());
    }

    @Test
    void testGetMyVisitedProfiles_ShouldReturnProfilesIVisited() throws Exception {
        User currentUser = testUsers.get(0);
        User visitedUser1 = testUsers.get(1);
        User visitedUser2 = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);

        // Current user visits other profiles
        profileVisitService.recordVisit(currentUser, visitedUser1);
        profileVisitService.recordVisit(currentUser, visitedUser2);

        Page<UserProfileVisit> visitedProfiles = profileVisitService.getMyVisitedProfiles(0, 10);

        assertEquals(2, visitedProfiles.getTotalElements());
        assertTrue(visitedProfiles.getContent().stream()
                .anyMatch(v -> v.getVisitedUser().getId().equals(visitedUser1.getId())));
        assertTrue(visitedProfiles.getContent().stream()
                .anyMatch(v -> v.getVisitedUser().getId().equals(visitedUser2.getId())));
    }

    // ============================================
    // Recent Visitors
    // ============================================

    @Test
    void testGetRecentVisitors_ShouldReturnVisitorsFromLastNDays() throws Exception {
        User currentUser = testUsers.get(0);
        User visitor1 = testUsers.get(1);
        User visitor2 = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);

        // Create a visit
        profileVisitService.recordVisit(visitor1, currentUser);

        // Create an old visit (manually set date)
        UserProfileVisit oldVisit = new UserProfileVisit(visitor2, currentUser);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -10);
        oldVisit.setLastVisitAt(cal.getTime());
        oldVisit.setVisitedAt(cal.getTime());
        visitRepository.saveAndFlush(oldVisit);

        // Get visitors from last 7 days
        List<UserProfileVisit> recentVisitors = profileVisitService.getRecentVisitors(7);

        // Should only include visitor1, not visitor2
        assertEquals(1, recentVisitors.size());
        assertEquals(visitor1.getId(), recentVisitors.get(0).getVisitor().getId());
    }

    @Test
    void testGetRecentVisitorCount_ShouldCountCorrectly() throws Exception {
        User currentUser = testUsers.get(0);
        User visitor1 = testUsers.get(1);
        User visitor2 = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);

        // Recent visit
        profileVisitService.recordVisit(visitor1, currentUser);

        // Old visit
        UserProfileVisit oldVisit = new UserProfileVisit(visitor2, currentUser);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -10);
        oldVisit.setLastVisitAt(cal.getTime());
        oldVisit.setVisitedAt(cal.getTime());
        visitRepository.saveAndFlush(oldVisit);

        long recentCount = profileVisitService.getRecentVisitorCount(7);
        assertEquals(1, recentCount);

        long allTimeCount = profileVisitService.getTotalVisitorCount();
        assertEquals(2, allTimeCount);
    }

    // ============================================
    // Visitor Counts
    // ============================================

    @Test
    void testGetTotalVisitorCount_ShouldCountAllUniqueVisitors() throws Exception {
        User currentUser = testUsers.get(0);
        User visitor1 = testUsers.get(1);
        User visitor2 = testUsers.get(2);

        Mockito.doReturn(currentUser).when(authService).getCurrentUser(true);

        profileVisitService.recordVisit(visitor1, currentUser);
        profileVisitService.recordVisit(visitor2, currentUser);

        // Visit multiple times
        profileVisitService.recordVisit(visitor1, currentUser);
        profileVisitService.recordVisit(visitor1, currentUser);

        long totalVisitors = profileVisitService.getTotalVisitorCount();
        assertEquals(2, totalVisitors); // 2 unique visitors
    }

    // ============================================
    // Has Visited Check
    // ============================================

    @Test
    void testHasVisited_WhenVisited_ShouldReturnTrue() throws Exception {
        User visitor = testUsers.get(0);
        User visitedUser = testUsers.get(1);

        profileVisitService.recordVisit(visitor, visitedUser);

        boolean hasVisited = profileVisitService.hasVisited(visitor, visitedUser);
        assertTrue(hasVisited);
    }

    @Test
    void testHasVisited_WhenNotVisited_ShouldReturnFalse() throws Exception {
        User visitor = testUsers.get(0);
        User visitedUser = testUsers.get(1);

        boolean hasVisited = profileVisitService.hasVisited(visitor, visitedUser);
        assertFalse(hasVisited);
    }

    // ============================================
    // Cleanup Old Visits
    // ============================================

    @Test
    void testCleanupOldVisits_ShouldDeleteOldRecords() throws Exception {
        User visitor = testUsers.get(0);
        User visitedUser = testUsers.get(1);

        // Create old visit
        UserProfileVisit oldVisit = new UserProfileVisit(visitor, visitedUser);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -100);
        oldVisit.setLastVisitAt(cal.getTime());
        oldVisit.setVisitedAt(cal.getTime());
        visitRepository.saveAndFlush(oldVisit);

        // Create recent visit
        User visitor2 = testUsers.get(2);
        profileVisitService.recordVisit(visitor2, visitedUser);

        long countBefore = visitRepository.count();
        assertEquals(2, countBefore);

        // Clean up visits older than 90 days
        profileVisitService.cleanupOldVisits(90);

        long countAfter = visitRepository.count();
        assertEquals(1, countAfter);

        // Recent visit should remain
        boolean recentExists = visitRepository.existsByVisitorAndVisitedUser(visitor2, visitedUser);
        assertTrue(recentExists);

        // Old visit should be deleted
        boolean oldExists = visitRepository.existsByVisitorAndVisitedUser(visitor, visitedUser);
        assertFalse(oldExists);
    }

    @Test
    void testCleanupOldVisits_ShouldNotDeleteRecentVisits() throws Exception {
        User visitor = testUsers.get(0);
        User visitedUser = testUsers.get(1);

        profileVisitService.recordVisit(visitor, visitedUser);

        long countBefore = visitRepository.count();

        // Clean up visits older than 30 days (should not affect recent visit)
        profileVisitService.cleanupOldVisits(30);

        long countAfter = visitRepository.count();
        assertEquals(countBefore, countAfter);
    }
}
