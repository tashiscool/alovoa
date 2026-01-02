package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.*;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserPoliticalAssessmentRepository;
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
class PoliticalAssessmentResourceTest {

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
    private UserPoliticalAssessmentRepository assessmentRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @Autowired
    private PoliticalAssessmentResource politicalAssessmentResource;

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
    void testValuesAssessment_NewAssessment() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertNotNull(mav);
        assertEquals("political-assessment", mav.getViewName());
        assertEquals(GateStatus.PENDING_ASSESSMENT.name(), mav.getModel().get("gateStatus"));
        assertFalse((Boolean) mav.getModel().get("canAccessMatching"));
        assertFalse((Boolean) mav.getModel().get("assessmentComplete"));
    }

    @Test
    void testValuesAssessment_PartiallyComplete() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create partial assessment
        UserPoliticalAssessment assessment = new UserPoliticalAssessment();
        assessment.setUuid(UUID.randomUUID());
        assessment.setUser(user);
        assessment.setGateStatus(GateStatus.PENDING_ASSESSMENT);
        assessment.setCreatedAt(new Date());
        assessment.setEconomicClass(EconomicClass.WORKING_CLASS);
        assessment.setPoliticalOrientation(PoliticalOrientation.PROGRESSIVE);
        assessment.setEconomicValuesScore(75.0);
        assessmentRepo.save(assessment);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertNotNull(mav);
        assertEquals(EconomicClass.WORKING_CLASS.name(), mav.getModel().get("economicClass"));
        assertEquals(PoliticalOrientation.PROGRESSIVE.name(), mav.getModel().get("politicalOrientation"));
        assertEquals(75.0, mav.getModel().get("economicValuesScore"));
    }

    @Test
    void testValuesAssessment_Approved() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create approved assessment
        UserPoliticalAssessment assessment = new UserPoliticalAssessment();
        assessment.setUuid(UUID.randomUUID());
        assessment.setUser(user);
        assessment.setGateStatus(GateStatus.APPROVED);
        assessment.setCreatedAt(new Date());
        assessment.setAssessmentCompletedAt(new Date());
        assessment.setEconomicClass(EconomicClass.WORKING_CLASS);
        assessment.setPoliticalOrientation(PoliticalOrientation.PROGRESSIVE);
        assessmentRepo.save(assessment);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertNotNull(mav);
        assertEquals(GateStatus.APPROVED.name(), mav.getModel().get("gateStatus"));
        assertTrue((Boolean) mav.getModel().get("canAccessMatching"));
        assertTrue((Boolean) mav.getModel().get("assessmentComplete"));
    }

    @Test
    void testValuesAssessment_PendingExplanation() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create assessment requiring explanation
        UserPoliticalAssessment assessment = new UserPoliticalAssessment();
        assessment.setUuid(UUID.randomUUID());
        assessment.setUser(user);
        assessment.setGateStatus(GateStatus.PENDING_EXPLANATION);
        assessment.setCreatedAt(new Date());
        assessment.setEconomicClass(EconomicClass.WORKING_CLASS);
        assessment.setPoliticalOrientation(PoliticalOrientation.CONSERVATIVE);
        assessmentRepo.save(assessment);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertNotNull(mav);
        assertEquals(GateStatus.PENDING_EXPLANATION.name(), mav.getModel().get("gateStatus"));
        assertTrue((Boolean) mav.getModel().get("needsExplanation"));
        assertFalse((Boolean) mav.getModel().get("canAccessMatching"));
    }

    @Test
    void testValuesAssessment_PendingVasectomy() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create assessment requiring vasectomy verification
        UserPoliticalAssessment assessment = new UserPoliticalAssessment();
        assessment.setUuid(UUID.randomUUID());
        assessment.setUser(user);
        assessment.setGateStatus(GateStatus.PENDING_VASECTOMY);
        assessment.setCreatedAt(new Date());
        assessment.setReproductiveRightsView(ReproductiveRightsView.FORCED_BIRTH);
        assessment.setVasectomyStatus(VasectomyStatus.NOT_VERIFIED);
        assessment.setAcknowledgedVasectomyRequirement(false);
        assessmentRepo.save(assessment);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertNotNull(mav);
        assertEquals(GateStatus.PENDING_VASECTOMY.name(), mav.getModel().get("gateStatus"));
        assertTrue((Boolean) mav.getModel().get("needsVasectomy"));
        assertFalse((Boolean) mav.getModel().get("acknowledgedRequirement"));
    }

    @Test
    void testValuesAssessment_Rejected() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create rejected assessment
        UserPoliticalAssessment assessment = new UserPoliticalAssessment();
        assessment.setUuid(UUID.randomUUID());
        assessment.setUser(user);
        assessment.setGateStatus(GateStatus.REJECTED);
        assessment.setRejectionReason(GateRejectionReason.CAPITAL_CLASS_CONSERVATIVE);
        assessment.setCreatedAt(new Date());
        assessment.setEconomicClass(EconomicClass.CAPITAL_CLASS);
        assessment.setPoliticalOrientation(PoliticalOrientation.CONSERVATIVE);
        assessmentRepo.save(assessment);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertNotNull(mav);
        assertEquals(GateStatus.REJECTED.name(), mav.getModel().get("gateStatus"));
        assertFalse((Boolean) mav.getModel().get("canAccessMatching"));
    }

    @Test
    void testValuesAssessment_UnderReview() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create assessment under review
        UserPoliticalAssessment assessment = new UserPoliticalAssessment();
        assessment.setUuid(UUID.randomUUID());
        assessment.setUser(user);
        assessment.setGateStatus(GateStatus.UNDER_REVIEW);
        assessment.setCreatedAt(new Date());
        assessment.setEconomicClass(EconomicClass.WORKING_CLASS);
        assessment.setPoliticalOrientation(PoliticalOrientation.CONSERVATIVE);
        assessment.setConservativeExplanation("I am learning and open to change.");
        assessment.setExplanationReviewed(false);
        assessmentRepo.save(assessment);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertNotNull(mav);
        assertEquals(GateStatus.UNDER_REVIEW.name(), mav.getModel().get("gateStatus"));
        assertFalse((Boolean) mav.getModel().get("canAccessMatching"));
    }

    @Test
    void testValuesAssessment_StatusMessage() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        ModelAndView mav = politicalAssessmentResource.valuesAssessment();

        assertTrue(mav.getModel().containsKey("statusMessage"));
        assertNotNull(mav.getModel().get("statusMessage"));
    }
}
