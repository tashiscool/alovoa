package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport.*;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent.BehaviorType;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserAccountabilityReportRepository;
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
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountabilityServiceTest {

    @Autowired
    private AccountabilityService accountabilityService;

    @Autowired
    private UserAccountabilityReportRepository reportRepo;

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
    void testSubmitReport_Success() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter,
                subject,
                AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING,
                "Ghosted after matching",
                "We matched and chatted for a week, then they stopped responding.",
                false,
                null
        );

        assertNotNull(report);
        assertNotNull(report.getUuid());
        assertEquals(reporter, report.getReporter());
        assertEquals(subject, report.getSubject());
        assertEquals(AccountabilityCategory.GHOSTING, report.getCategory());
        assertEquals(ReportStatus.PENDING_VERIFICATION, report.getStatus());
        assertEquals(ReportVisibility.HIDDEN, report.getVisibility());
        assertNotNull(report.getReputationImpact());
        assertTrue(report.getReputationImpact() < 0); // Negative impact for ghosting
    }

    @Test
    void testSubmitReport_CannotReportSelf() {
        User user = testUsers.get(0);

        assertThrows(IllegalArgumentException.class, () ->
                accountabilityService.submitReport(
                        user,
                        user,
                        AccountabilityCategory.GHOSTING,
                        BehaviorType.GHOSTING,
                        "Test",
                        "Test",
                        false,
                        null
                ));
    }

    @Test
    void testSubmitReport_AnonymousReport() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter,
                subject,
                AccountabilityCategory.HARASSMENT,
                BehaviorType.INAPPROPRIATE_CONTENT,
                "Harassment report",
                "Received inappropriate messages.",
                true,  // anonymous
                null
        );

        assertTrue(report.isAnonymous());
    }

    @Test
    void testSubmitReport_PositiveExperience() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter,
                subject,
                AccountabilityCategory.POSITIVE_EXPERIENCE,
                BehaviorType.POSITIVE_FEEDBACK,
                "Great date!",
                "We had an amazing first date. Very respectful and genuine.",
                false,
                null
        );

        assertEquals(AccountabilityCategory.POSITIVE_EXPERIENCE, report.getCategory());
        assertTrue(report.getReputationImpact() > 0); // Positive impact
    }

    @Test
    void testSubmitReport_DailyLimitExceeded() throws Exception {
        User reporter = testUsers.get(0);
        User subject1 = testUsers.get(1);
        User subject2 = testUsers.get(2);

        // Submit max reports
        for (int i = 0; i < 3; i++) {
            User subject = i == 0 ? subject1 : subject2;
            accountabilityService.submitReport(
                    reporter,
                    subject,
                    AccountabilityCategory.values()[i % 3],
                    BehaviorType.GHOSTING,
                    "Report " + i,
                    "Description " + i,
                    false,
                    null
            );
        }

        // Fourth report should fail
        assertThrows(IllegalStateException.class, () ->
                accountabilityService.submitReport(
                        reporter,
                        subject1,
                        AccountabilityCategory.DISHONESTY,
                        BehaviorType.MISREPRESENTATION,
                        "Too many reports",
                        "Description",
                        false,
                        null
                ));
    }

    @Test
    void testVerifyReport() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        accountabilityService.verifyReport(report.getUuid(), true, "Verified by admin");

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertEquals(ReportStatus.VERIFIED, report.getStatus());
        assertNotNull(report.getVerifiedAt());
        assertEquals("Verified by admin", report.getVerificationNotes());
    }

    @Test
    void testVerifyReport_InsufficientEvidence() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        accountabilityService.verifyReport(report.getUuid(), false, "No evidence provided");

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertEquals(ReportStatus.EVIDENCE_INSUFFICIENT, report.getStatus());
    }

    @Test
    void testPublishReport() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        // Must verify first
        accountabilityService.verifyReport(report.getUuid(), true, "Verified");

        // Then publish
        accountabilityService.publishReport(report.getUuid(), ReportVisibility.PUBLIC);

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertEquals(ReportStatus.PUBLISHED, report.getStatus());
        assertEquals(ReportVisibility.PUBLIC, report.getVisibility());
        assertNotNull(report.getPublishedAt());
    }

    @Test
    void testPublishReport_NotVerified() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        // Cannot publish unverified report
        assertThrows(IllegalStateException.class, () ->
                accountabilityService.publishReport(report.getUuid(), ReportVisibility.PUBLIC));
    }

    @Test
    void testSubmitSubjectResponse() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        accountabilityService.submitSubjectResponse(report.getUuid(), subject,
                "I had a family emergency and couldn't respond.");

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertNotNull(report.getSubjectResponse());
        assertNotNull(report.getSubjectResponseDate());
    }

    @Test
    void testSubmitSubjectResponse_WrongUser() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);
        User other = testUsers.get(2);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        assertThrows(IllegalArgumentException.class, () ->
                accountabilityService.submitSubjectResponse(report.getUuid(), other,
                        "Some response"));
    }

    @Test
    void testSubmitSubjectResponse_AlreadyResponded() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        accountabilityService.submitSubjectResponse(report.getUuid(), subject, "First response");

        assertThrows(IllegalStateException.class, () ->
                accountabilityService.submitSubjectResponse(report.getUuid(), subject, "Second response"));
    }

    @Test
    void testGetFeedbackSummary() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        // Create and publish some reports
        UserAccountabilityReport positive = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.POSITIVE_EXPERIENCE,
                BehaviorType.POSITIVE_FEEDBACK, "Great!", "Great experience", false, null);
        accountabilityService.verifyReport(positive.getUuid(), true, "Verified");
        accountabilityService.publishReport(positive.getUuid(), ReportVisibility.PUBLIC);

        UserAccountabilityReport negative = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Ghosted", "They ghosted me", false, null);
        accountabilityService.verifyReport(negative.getUuid(), true, "Verified");
        accountabilityService.publishReport(negative.getUuid(), ReportVisibility.PUBLIC);

        Map<String, Object> summary = accountabilityService.getFeedbackSummary(subject);

        assertNotNull(summary);
        assertEquals(2, summary.get("totalReports"));
        assertEquals(1, summary.get("positiveCount"));
        assertEquals(1, summary.get("negativeCount"));
        assertTrue(summary.containsKey("feedbackScore"));
        assertTrue(summary.containsKey("byCategory"));
    }

    @Test
    void testGetPublicFeedback() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);
        User viewer = testUsers.get(2);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.POSITIVE_EXPERIENCE,
                BehaviorType.POSITIVE_FEEDBACK, "Great!", "Great experience", false, null);
        accountabilityService.verifyReport(report.getUuid(), true, "Verified");
        accountabilityService.publishReport(report.getUuid(), ReportVisibility.PUBLIC);

        List<UserAccountabilityReport> feedback = accountabilityService.getPublicFeedback(subject, viewer);

        assertFalse(feedback.isEmpty());
        assertEquals(1, feedback.size());
    }

    @Test
    void testMarkHelpful() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);
        User viewer = testUsers.get(2);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);
        accountabilityService.verifyReport(report.getUuid(), true, "Verified");
        accountabilityService.publishReport(report.getUuid(), ReportVisibility.PUBLIC);

        accountabilityService.markHelpful(report.getUuid(), viewer);

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertEquals(1, report.getHelpfulCount());
    }

    @Test
    void testFlagReport() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);
        User viewer = testUsers.get(2);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);
        accountabilityService.verifyReport(report.getUuid(), true, "Verified");
        accountabilityService.publishReport(report.getUuid(), ReportVisibility.PUBLIC);

        accountabilityService.flagReport(report.getUuid(), viewer);

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertEquals(1, report.getFlaggedCount());
    }

    @Test
    void testFlagReport_BecomesDisputed() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);
        User viewer = testUsers.get(2);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);
        accountabilityService.verifyReport(report.getUuid(), true, "Verified");
        accountabilityService.publishReport(report.getUuid(), ReportVisibility.PUBLIC);

        // Flag 5 times to trigger dispute
        for (int i = 0; i < 5; i++) {
            accountabilityService.flagReport(report.getUuid(), viewer);
        }

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertEquals(ReportStatus.DISPUTED, report.getStatus());
    }

    @Test
    void testRetractReport() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        accountabilityService.retractReport(report.getUuid(), reporter);

        report = reportRepo.findByUuid(report.getUuid()).orElse(null);
        assertNotNull(report);
        assertEquals(ReportStatus.RETRACTED, report.getStatus());
    }

    @Test
    void testRetractReport_WrongUser() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);
        User other = testUsers.get(2);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);

        assertThrows(IllegalArgumentException.class, () ->
                accountabilityService.retractReport(report.getUuid(), other));
    }

    @Test
    void testRetractReport_CannotRetractPublished() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        UserAccountabilityReport report = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test", "Description", false, null);
        accountabilityService.verifyReport(report.getUuid(), true, "Verified");
        accountabilityService.publishReport(report.getUuid(), ReportVisibility.PUBLIC);

        assertThrows(IllegalStateException.class, () ->
                accountabilityService.retractReport(report.getUuid(), reporter));
    }

    @Test
    void testGetPendingReports() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test 1", "Description", false, null);
        accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.DISHONESTY,
                BehaviorType.MISREPRESENTATION, "Test 2", "Description", false, null);

        Page<UserAccountabilityReport> pending = accountabilityService.getPendingReports(0, 10);

        assertEquals(2, pending.getTotalElements());
    }

    @Test
    void testReputationImpactByCategory() throws Exception {
        User reporter = testUsers.get(0);
        User subject = testUsers.get(1);

        // Test various categories have different impacts
        UserAccountabilityReport harassment = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.HARASSMENT,
                BehaviorType.INAPPROPRIATE_CONTENT, "Test", "Description", false, null);

        UserAccountabilityReport ghosting = accountabilityService.submitReport(
                reporter, subject, AccountabilityCategory.GHOSTING,
                BehaviorType.GHOSTING, "Test2", "Description", false, null);

        // Harassment should have higher impact than ghosting
        assertTrue(Math.abs(harassment.getReputationImpact()) > Math.abs(ghosting.getReputationImpact()));
    }
}
