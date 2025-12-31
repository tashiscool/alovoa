package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport.*;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent.BehaviorType;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserAccountabilityReportRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountabilityResourceTest {

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
    private UserAccountabilityReportRepository reportRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @Autowired
    private AccountabilityResource accountabilityResource;

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
    void testMyAccountability_NoReports() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        ModelAndView mav = accountabilityResource.myAccountability();

        assertNotNull(mav);
        assertEquals("accountability", mav.getViewName());
        assertEquals(0, mav.getModel().get("totalCount"));
        assertEquals(0, mav.getModel().get("positiveCount"));
        assertEquals(0, mav.getModel().get("negativeCount"));
        assertFalse((Boolean) mav.getModel().get("canSubmitFeedback"));
    }

    @Test
    void testMyAccountability_WithReports() throws Exception {
        User user = testUsers.get(0);
        User reporter = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create published reports
        UserAccountabilityReport positiveReport = createReport(reporter, user,
                AccountabilityCategory.POSITIVE_EXPERIENCE, ReportStatus.PUBLISHED);
        reportRepo.save(positiveReport);

        UserAccountabilityReport negativeReport = createReport(reporter, user,
                AccountabilityCategory.GHOSTING, ReportStatus.PUBLISHED);
        reportRepo.save(negativeReport);

        ModelAndView mav = accountabilityResource.myAccountability();

        assertNotNull(mav);
        assertEquals(2, mav.getModel().get("totalCount"));
        assertEquals(1, mav.getModel().get("positiveCount"));
        assertEquals(1, mav.getModel().get("negativeCount"));
    }

    @Test
    void testUserAccountability() throws Exception {
        User viewer = testUsers.get(0);
        User subject = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(viewer);

        // Create published report
        UserAccountabilityReport report = createReport(viewer, subject,
                AccountabilityCategory.POSITIVE_EXPERIENCE, ReportStatus.PUBLISHED);
        reportRepo.save(report);

        ModelAndView mav = accountabilityResource.userAccountability(subject.getUuid().toString());

        assertNotNull(mav);
        assertEquals("accountability", mav.getViewName());
        assertTrue((Boolean) mav.getModel().get("canSubmitFeedback"));
        assertEquals(subject.getUuid().toString(), mav.getModel().get("subjectUuid"));
    }

    @Test
    void testUserAccountability_UserNotFound() {
        User viewer = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(viewer);

        assertThrows(Exception.class, () ->
                accountabilityResource.userAccountability("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void testAccountability_FeedbackScore() throws Exception {
        User user = testUsers.get(0);
        User reporter = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create 3 positive and 1 negative reports
        for (int i = 0; i < 3; i++) {
            UserAccountabilityReport positive = createReport(reporter, user,
                    AccountabilityCategory.POSITIVE_EXPERIENCE, ReportStatus.PUBLISHED);
            reportRepo.save(positive);
        }

        UserAccountabilityReport negative = createReport(reporter, user,
                AccountabilityCategory.GHOSTING, ReportStatus.PUBLISHED);
        reportRepo.save(negative);

        ModelAndView mav = accountabilityResource.myAccountability();

        // Feedback score should be 75% (3 positive out of 4)
        assertEquals(75L, mav.getModel().get("feedbackScore"));
    }

    @Test
    void testAccountability_CategoryBreakdown() throws Exception {
        User user = testUsers.get(0);
        User reporter = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create various reports
        reportRepo.save(createReport(reporter, user, AccountabilityCategory.POSITIVE_EXPERIENCE, ReportStatus.PUBLISHED));
        reportRepo.save(createReport(reporter, user, AccountabilityCategory.GHOSTING, ReportStatus.PUBLISHED));
        reportRepo.save(createReport(reporter, user, AccountabilityCategory.DISHONESTY, ReportStatus.PUBLISHED));

        ModelAndView mav = accountabilityResource.myAccountability();

        assertTrue(mav.getModel().containsKey("categoryBreakdown"));
    }

    @Test
    void testAccountability_PublicFeedbackVisible() throws Exception {
        User viewer = testUsers.get(0);
        User subject = testUsers.get(1);
        User otherReporter = testUsers.get(2);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(viewer);

        // Create public report
        UserAccountabilityReport publicReport = createReport(otherReporter, subject,
                AccountabilityCategory.POSITIVE_EXPERIENCE, ReportStatus.PUBLISHED);
        publicReport.setVisibility(ReportVisibility.PUBLIC);
        reportRepo.save(publicReport);

        ModelAndView mav = accountabilityResource.userAccountability(subject.getUuid().toString());

        assertTrue(mav.getModel().containsKey("feedback"));

        @SuppressWarnings("unchecked")
        List<UserAccountabilityReport> feedback = (List<UserAccountabilityReport>) mav.getModel().get("feedback");
        assertFalse(feedback.isEmpty());
    }

    private UserAccountabilityReport createReport(User reporter, User subject,
                                                   AccountabilityCategory category,
                                                   ReportStatus status) {
        UserAccountabilityReport report = new UserAccountabilityReport();
        report.setUuid(UUID.randomUUID());
        report.setReporter(reporter);
        report.setSubject(subject);
        report.setCategory(category);
        report.setBehaviorType(BehaviorType.POSITIVE_FEEDBACK);
        report.setTitle("Test Report");
        report.setDescription("Test description");
        report.setStatus(status);
        report.setVisibility(ReportVisibility.PUBLIC);
        report.setCreatedAt(new Date());
        report.setReputationImpact(category == AccountabilityCategory.POSITIVE_EXPERIENCE ? 3.0 : -2.0);
        return report;
    }
}
