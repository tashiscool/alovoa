package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Gender;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.*;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.GenderRepository;
import com.nonononoki.alovoa.repo.UserPoliticalAssessmentRepository;
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
class PoliticalAssessmentServiceTest {

    @Autowired
    private PoliticalAssessmentService assessmentService;

    @Autowired
    private UserPoliticalAssessmentRepository assessmentRepo;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private GenderRepository genderRepo;

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
    void testGetOrCreateAssessment_NewAssessment() throws Exception {
        User user = testUsers.get(0);

        UserPoliticalAssessment assessment = assessmentService.getOrCreateAssessment(user);

        assertNotNull(assessment);
        assertNotNull(assessment.getUuid());
        assertEquals(user, assessment.getUser());
        assertEquals(GateStatus.PENDING_ASSESSMENT, assessment.getGateStatus());
        assertNotNull(assessment.getCreatedAt());
    }

    @Test
    void testGetOrCreateAssessment_ExistingAssessment() throws Exception {
        User user = testUsers.get(0);

        UserPoliticalAssessment first = assessmentService.getOrCreateAssessment(user);
        Long firstId = first.getId();

        UserPoliticalAssessment second = assessmentService.getOrCreateAssessment(user);

        assertEquals(firstId, second.getId());
    }

    @Test
    void testSubmitEconomicClass_WorkingClass() throws Exception {
        User user = testUsers.get(0);

        UserPoliticalAssessment assessment = assessmentService.submitEconomicClass(
                user,
                IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY,
                WealthBracket.BRACKET_10K_50K,
                false,  // doesn't own rental properties
                false,  // doesn't employ others
                false   // doesn't live off capital
        );

        assertNotNull(assessment);
        assertEquals(EconomicClass.WORKING_CLASS, assessment.getEconomicClass());
    }

    @Test
    void testSubmitEconomicClass_CapitalClass() throws Exception {
        User user = testUsers.get(0);

        UserPoliticalAssessment assessment = assessmentService.submitEconomicClass(
                user,
                IncomeBracket.BRACKET_500K_1M,
                IncomeSource.INVESTMENTS_DIVIDENDS,
                WealthBracket.OVER_10M,
                true,   // owns rental properties
                true,   // employs others
                true    // lives off capital
        );

        assertNotNull(assessment);
        assertEquals(EconomicClass.CAPITAL_CLASS, assessment.getEconomicClass());
    }

    @Test
    void testSubmitPoliticalValues() throws Exception {
        User user = testUsers.get(0);

        // First submit economic class
        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);

        // Then political values
        UserPoliticalAssessment assessment = assessmentService.submitPoliticalValues(
                user,
                PoliticalOrientation.PROGRESSIVE,
                5,  // wealth redistribution (pro)
                5,  // worker ownership (pro)
                5,  // universal services (pro)
                5,  // housing rights (pro)
                1,  // billionaire existence (anti)
                2,  // meritocracy belief (skeptical)
                null
        );

        assertNotNull(assessment);
        assertEquals(PoliticalOrientation.PROGRESSIVE, assessment.getPoliticalOrientation());
        assertNotNull(assessment.getEconomicValuesScore());
        assertTrue(assessment.getEconomicValuesScore() > 70); // High progressive score
    }

    @Test
    void testCompleteAssessment_WorkingClassProgressive_Approved() throws Exception {
        User user = testUsers.get(0);

        // Complete all steps
        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitPoliticalValues(user, PoliticalOrientation.PROGRESSIVE,
                5, 5, 5, 5, 1, 2, null);
        assessmentService.submitReproductiveView(user, ReproductiveRightsView.FULL_BODILY_AUTONOMY);

        UserPoliticalAssessment assessment = assessmentService.completeAssessment(user);

        assertEquals(GateStatus.APPROVED, assessment.getGateStatus());
        assertTrue(assessmentService.canAccessMatching(user));
    }

    @Test
    void testCompleteAssessment_CapitalClassConservative_Rejected() throws Exception {
        User user = testUsers.get(0);

        // Capital class conservative
        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_500K_1M,
                IncomeSource.INVESTMENTS_DIVIDENDS, WealthBracket.OVER_10M, true, true, true);
        assessmentService.submitPoliticalValues(user, PoliticalOrientation.CONSERVATIVE,
                1, 1, 1, 1, 5, 5, null);

        UserPoliticalAssessment assessment = assessmentService.completeAssessment(user);

        assertEquals(GateStatus.REJECTED, assessment.getGateStatus());
        assertEquals(GateRejectionReason.CAPITAL_CLASS_CONSERVATIVE, assessment.getRejectionReason());
        assertFalse(assessmentService.canAccessMatching(user));
    }

    @Test
    void testCompleteAssessment_WorkingClassConservative_NeedsExplanation() throws Exception {
        User user = testUsers.get(0);

        // Working class conservative
        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitPoliticalValues(user, PoliticalOrientation.CONSERVATIVE,
                2, 2, 2, 2, 4, 5, null);

        UserPoliticalAssessment assessment = assessmentService.completeAssessment(user);

        assertEquals(GateStatus.PENDING_EXPLANATION, assessment.getGateStatus());
        assertFalse(assessmentService.canAccessMatching(user));
    }

    @Test
    void testSubmitConservativeExplanation() throws Exception {
        User user = testUsers.get(0);

        // Setup: working class conservative needs explanation
        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitPoliticalValues(user, PoliticalOrientation.CONSERVATIVE,
                2, 2, 2, 2, 4, 5, null);
        assessmentService.completeAssessment(user);

        // Submit explanation
        UserPoliticalAssessment assessment = assessmentService.submitConservativeExplanation(user,
                "I was raised with these values and am still learning about economic policy.");

        assertEquals(GateStatus.UNDER_REVIEW, assessment.getGateStatus());
        assertNotNull(assessment.getConservativeExplanation());
        assertFalse(assessment.getExplanationReviewed());
    }

    @Test
    void testSubmitConservativeExplanation_NotRequired() throws Exception {
        User user = testUsers.get(0);

        // Progressive user doesn't need explanation
        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitPoliticalValues(user, PoliticalOrientation.PROGRESSIVE,
                5, 5, 5, 5, 1, 2, null);
        assessmentService.completeAssessment(user);

        assertThrows(IllegalStateException.class, () ->
                assessmentService.submitConservativeExplanation(user, "Some explanation"));
    }

    @Test
    void testReviewConservativeExplanation_Approved() throws Exception {
        User user = testUsers.get(0);

        // Setup and submit explanation
        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitPoliticalValues(user, PoliticalOrientation.CONSERVATIVE,
                2, 2, 2, 2, 4, 5, null);
        assessmentService.completeAssessment(user);
        UserPoliticalAssessment assessment = assessmentService.submitConservativeExplanation(user,
                "I am open to learning.");

        // Admin approves
        assessmentService.reviewConservativeExplanation(assessment.getUuid(), true, "Seems genuine");

        assessment = assessmentRepo.findByUser(user).orElse(null);
        assertNotNull(assessment);
        assertEquals(GateStatus.APPROVED, assessment.getGateStatus());
        assertTrue(assessment.getExplanationReviewed());
        assertTrue(assessmentService.canAccessMatching(user));
    }

    @Test
    void testReviewConservativeExplanation_Rejected() throws Exception {
        User user = testUsers.get(0);

        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitPoliticalValues(user, PoliticalOrientation.CONSERVATIVE,
                2, 2, 2, 2, 4, 5, null);
        assessmentService.completeAssessment(user);
        UserPoliticalAssessment assessment = assessmentService.submitConservativeExplanation(user,
                "I believe billionaires earned their money.");

        assessmentService.reviewConservativeExplanation(assessment.getUuid(), false, "Shows no class awareness");

        assessment = assessmentRepo.findByUser(user).orElse(null);
        assertNotNull(assessment);
        assertEquals(GateStatus.REJECTED, assessment.getGateStatus());
        assertEquals(GateRejectionReason.UNEXPLAINED_CONSERVATIVE, assessment.getRejectionReason());
    }

    @Test
    void testCanAccessMatching_NoAssessment() throws Exception {
        User user = testUsers.get(0);

        assertFalse(assessmentService.canAccessMatching(user));
    }

    @Test
    void testGetGateStatusMessage() throws Exception {
        User user = testUsers.get(0);

        // No assessment
        String message = assessmentService.getGateStatusMessage(user);
        assertTrue(message.contains("complete the values assessment"));

        // Pending assessment
        assessmentService.getOrCreateAssessment(user);
        message = assessmentService.getGateStatusMessage(user);
        assertTrue(message.contains("complete the values assessment"));
    }

    @Test
    void testGetEconomicCompatibility() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Both progressive
        assessmentService.submitEconomicClass(user1, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitPoliticalValues(user1, PoliticalOrientation.PROGRESSIVE,
                5, 5, 5, 5, 1, 2, null);

        assessmentService.submitEconomicClass(user2, IncomeBracket.BRACKET_75K_100K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_50K_100K, false, false, false);
        assessmentService.submitPoliticalValues(user2, PoliticalOrientation.PROGRESSIVE,
                5, 4, 5, 5, 1, 2, null);

        double compatibility = assessmentService.getEconomicCompatibility(user1, user2);

        // Similar economic values should have high compatibility
        assertTrue(compatibility > 80);
    }

    @Test
    void testGetEconomicCompatibility_NoAssessment() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // No assessments - should return neutral
        double compatibility = assessmentService.getEconomicCompatibility(user1, user2);

        assertEquals(50.0, compatibility);
    }

    @Test
    void testGetClassConsciousnessQuestions() throws Exception {
        List<Map<String, Object>> questions = assessmentService.getClassConsciousnessQuestions();

        assertNotNull(questions);
        assertFalse(questions.isEmpty());

        // Check structure
        Map<String, Object> firstQuestion = questions.get(0);
        assertTrue(firstQuestion.containsKey("id"));
        assertTrue(firstQuestion.containsKey("question"));
        assertTrue(firstQuestion.containsKey("options"));
        assertTrue(firstQuestion.containsKey("correctAwareness"));
    }

    @Test
    void testSubmitClassConsciousnessTest() throws Exception {
        User user = testUsers.get(0);

        Map<String, Integer> answers = Map.of(
                "taxCuts", 5,
                "rentRelations", 5,
                "employerRelations", 5,
                "laborHistory", 5,
                "classInterests", 5,
                "policyAnalysis", 5
        );

        UserPoliticalAssessment assessment = assessmentService.submitClassConsciousnessTest(user, answers);

        assertNotNull(assessment);
        assertNotNull(assessment.getClassConsciousnessScore());
        // All answers at 5 (highest awareness) should give high score
        assertTrue(assessment.getClassConsciousnessScore() > 90);
    }

    @Test
    void testSubmitReproductiveView_MaleForcedBirth() throws Exception {
        User user = testUsers.get(0);
        // Set male gender
        Gender maleGender = genderRepo.findById(1L).orElse(null);
        if (maleGender == null) {
            maleGender = new Gender();
            maleGender.setId(1L);
            genderRepo.save(maleGender);
        }
        user.setGender(maleGender);
        userRepo.save(user);

        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);

        UserPoliticalAssessment assessment = assessmentService.submitReproductiveView(user,
                ReproductiveRightsView.FORCED_BIRTH);

        assertNotNull(assessment);
        assertEquals(VasectomyStatus.NOT_VERIFIED, assessment.getVasectomyStatus());
        assertFalse(assessment.getAcknowledgedVasectomyRequirement());
    }

    @Test
    void testSubmitReproductiveView_NonForcedBirth() throws Exception {
        User user = testUsers.get(0);

        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);

        UserPoliticalAssessment assessment = assessmentService.submitReproductiveView(user,
                ReproductiveRightsView.FULL_BODILY_AUTONOMY);

        assertNotNull(assessment);
        assertEquals(VasectomyStatus.NOT_APPLICABLE, assessment.getVasectomyStatus());
    }

    @Test
    void testAcknowledgeVasectomyRequirement() throws Exception {
        User user = testUsers.get(0);

        // Set male gender
        Gender maleGender = genderRepo.findById(1L).orElse(null);
        if (maleGender == null) {
            maleGender = new Gender();
            maleGender.setId(1L);
            genderRepo.save(maleGender);
        }
        user.setGender(maleGender);
        userRepo.save(user);

        assessmentService.submitEconomicClass(user, IncomeBracket.BRACKET_50K_75K,
                IncomeSource.WAGES_SALARY, WealthBracket.BRACKET_10K_50K, false, false, false);
        assessmentService.submitReproductiveView(user, ReproductiveRightsView.FORCED_BIRTH);

        // Decline to verify
        UserPoliticalAssessment assessment = assessmentService.acknowledgeVasectomyRequirement(user, false);

        assertTrue(assessment.getAcknowledgedVasectomyRequirement());
        assertEquals(VasectomyStatus.DECLINED, assessment.getVasectomyStatus());
    }
}
