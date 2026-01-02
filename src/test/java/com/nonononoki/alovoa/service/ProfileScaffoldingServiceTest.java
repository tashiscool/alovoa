package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentProfile;
import com.nonononoki.alovoa.entity.VideoSegmentPrompt;
import com.nonononoki.alovoa.entity.user.InferredDealbreaker;
import com.nonononoki.alovoa.entity.user.UserScaffoldingProgress;
import com.nonononoki.alovoa.entity.user.UserScaffoldingProgress.ScaffoldingStatus;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.entity.user.UserVideoSegment;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.ScaffoldedProfileDto;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileScaffoldingServiceTest {

    @Autowired
    private ProfileScaffoldingService scaffoldingService;

    @Autowired
    private UserVideoIntroductionRepository videoIntroRepo;

    @Autowired
    private UserVideoSegmentRepository segmentRepo;

    @Autowired
    private VideoSegmentPromptRepository promptRepo;

    @Autowired
    private UserScaffoldingProgressRepository progressRepo;

    @Autowired
    private InferredDealbreakerRepository dealbreakerRepo;

    @Autowired
    private UserAssessmentProfileRepository assessmentProfileRepo;

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
    private ObjectMapper objectMapper;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private S3StorageService s3StorageService;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(Mockito.any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);

        // Create test prompts if they don't exist
        if (promptRepo.count() == 0) {
            createTestPrompts();
        }
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    void testGetAvailablePrompts() throws Exception {
        List<Map<String, Object>> prompts = scaffoldingService.getAvailablePrompts();

        assertNotNull(prompts);
        // May be empty if no prompts in DB, but should not throw
        assertTrue(prompts.isEmpty() || prompts.get(0).containsKey("key"));
    }

    @Test
    void testGetScaffoldedProfile_NoProfile() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        assertThrows(AlovoaException.class, () -> scaffoldingService.getScaffoldedProfile());
    }

    @Test
    void testGetScaffoldedProfile_WithInferenceData() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create video intro with inference data
        UserVideoIntroduction intro = createVideoIntroWithInference(user);

        ScaffoldedProfileDto profile = scaffoldingService.getScaffoldedProfile();

        assertNotNull(profile);
        assertNotNull(profile.getBigFive());
        assertEquals(5, profile.getBigFive().size());
        assertTrue(profile.getBigFive().containsKey("openness"));
        assertTrue(profile.getBigFive().containsKey("extraversion"));
    }

    @Test
    void testSaveAdjustments() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create video intro with inference data
        createVideoIntroWithInference(user);

        // Adjust scores
        Map<String, Object> adjustments = new HashMap<>();
        Map<String, Object> bigFive = new HashMap<>();
        bigFive.put("openness", 80.0);
        bigFive.put("extraversion", 60.0);
        adjustments.put("bigFive", bigFive);

        scaffoldingService.saveAdjustments(adjustments);

        // Verify adjustments were applied
        UserVideoIntroduction intro = videoIntroRepo.findByUser(user).orElseThrow();
        assertEquals(80.0, intro.getInferredOpenness());
        assertEquals(60.0, intro.getInferredExtraversion());
    }

    @Test
    void testConfirmScaffoldedProfile() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create video intro with inference data
        createVideoIntroWithInference(user);

        // Confirm profile
        Map<String, Object> result = scaffoldingService.confirmScaffoldedProfile();

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("matchable"));

        // Verify UserAssessmentProfile was created
        Optional<UserAssessmentProfile> profile = assessmentProfileRepo.findByUser(user);
        assertTrue(profile.isPresent());
        assertTrue(profile.get().getProfileComplete());

        // Verify video intro was marked confirmed
        UserVideoIntroduction intro = videoIntroRepo.findByUser(user).orElseThrow();
        assertTrue(intro.getInferenceConfirmed());
        assertNotNull(intro.getConfirmedAt());
    }

    @Test
    void testReRecordVideoIntro() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create video intro with inference data
        UserVideoIntroduction intro = createVideoIntroWithInference(user);
        Long introId = intro.getId();

        // Create a segment
        VideoSegmentPrompt prompt = promptRepo.findByPromptKey("worldview").orElse(null);
        if (prompt != null) {
            UserVideoSegment segment = new UserVideoSegment();
            segment.setUser(user);
            segment.setPrompt(prompt);
            segment.setS3Key("test-segment-key");
            segment.setStatus(UserVideoSegment.AnalysisStatus.COMPLETED);
            segmentRepo.save(segment);
        }

        // Re-record
        Map<String, Object> result = scaffoldingService.reRecordVideoIntro();

        assertTrue((Boolean) result.get("success"));

        // Verify inference data was cleared
        intro = videoIntroRepo.findByUser(user).orElse(null);
        if (intro != null) {
            assertNull(intro.getInferredOpenness());
            assertFalse(intro.getInferenceConfirmed());
        }

        // Verify segments were deleted
        List<UserVideoSegment> segments = segmentRepo.findByUser(user);
        assertTrue(segments.isEmpty());
    }

    @Test
    void testGetProgress_NotStarted() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> progress = scaffoldingService.getProgress();

        assertNotNull(progress);
        assertEquals("NOT_STARTED", progress.get("status"));
        assertEquals(0, progress.get("segmentsCompleted"));
    }

    @Test
    void testGetProgress_InProgress() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create progress
        UserScaffoldingProgress progress = new UserScaffoldingProgress();
        progress.setUser(user);
        progress.setStatus(ScaffoldingStatus.RECORDING);
        progress.setSegmentsCompleted(2);
        progress.setSegmentsRequired(4);
        progressRepo.save(progress);

        Map<String, Object> result = scaffoldingService.getProgress();

        assertNotNull(result);
        assertEquals("RECORDING", result.get("status"));
        assertEquals(2, result.get("segmentsCompleted"));
        assertEquals(4, result.get("segmentsRequired"));
    }

    @Test
    void testHasScaffoldedProfileReady() throws Exception {
        User user = testUsers.get(0);

        // No profile initially
        assertFalse(scaffoldingService.hasScaffoldedProfileReady(user));

        // Create video intro with inference data (not confirmed)
        createVideoIntroWithInference(user);

        assertTrue(scaffoldingService.hasScaffoldedProfileReady(user));

        // Confirm the profile
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        scaffoldingService.confirmScaffoldedProfile();

        // No longer "ready" (already confirmed)
        assertFalse(scaffoldingService.hasScaffoldedProfileReady(user));
    }

    @Test
    void testInferredDealbreakerConfirmation() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create video intro with inference data
        createVideoIntroWithInference(user);

        // Create high confidence dealbreaker
        InferredDealbreaker dealbreaker = new InferredDealbreaker();
        dealbreaker.setUser(user);
        dealbreaker.setDealbreakerKey("smoking");
        dealbreaker.setConfidence(0.85);
        dealbreaker.setSourceQuote("I can't stand cigarettes");
        dealbreakerRepo.save(dealbreaker);

        // Confirm profile
        scaffoldingService.confirmScaffoldedProfile();

        // Verify high confidence dealbreaker was auto-confirmed
        List<InferredDealbreaker> dealbreakers = dealbreakerRepo.findByUser(user);
        assertEquals(1, dealbreakers.size());
        assertTrue(dealbreakers.get(0).getConfirmed());
    }

    @Test
    void testVideoAnalysisResultInferenceFields() {
        // Test that VideoAnalysisResult properly handles inference data
        com.nonononoki.alovoa.model.VideoAnalysisResult result = com.nonononoki.alovoa.model.VideoAnalysisResult.builder()
                .transcript("Test transcript")
                .worldviewSummary("Test worldview")
                .inferredBigFive(Map.of(
                        "openness", 75.0,
                        "conscientiousness", 60.0,
                        "extraversion", 50.0,
                        "agreeableness", 70.0,
                        "neuroticism", 40.0
                ))
                .inferredAttachmentStyle("SECURE")
                .overallConfidence(0.75)
                .build();

        assertTrue(result.isSuccess());
        assertTrue(result.hasInferenceData());
        assertTrue(result.hasHighConfidence(0.7));
        assertFalse(result.hasHighConfidence(0.8));
    }

    @Test
    void testScaffoldedProfileDto_LowConfidenceCount() {
        ScaffoldedProfileDto dto = ScaffoldedProfileDto.builder()
                .lowConfidenceAreas(List.of("values", "lifestyle"))
                .overallConfidence(0.45)
                .build();

        assertEquals(2, dto.getLowConfidenceCount());
        assertFalse(dto.hasSufficientConfidence(0.5));
    }

    // Helper methods

    private UserVideoIntroduction createVideoIntroWithInference(User user) {
        UserVideoIntroduction intro = new UserVideoIntroduction();
        intro.setUser(user);
        intro.setTranscript("Test transcript");
        intro.setWorldviewSummary("Test worldview summary");
        intro.setStatus(UserVideoIntroduction.AnalysisStatus.COMPLETED);

        // Set inference data
        intro.setInferredOpenness(70.0);
        intro.setInferredConscientiousness(65.0);
        intro.setInferredExtraversion(55.0);
        intro.setInferredAgreeableness(75.0);
        intro.setInferredNeuroticism(35.0);

        intro.setInferredAttachmentAnxiety(30.0);
        intro.setInferredAttachmentAvoidance(25.0);
        intro.setInferredAttachmentStyle("SECURE");

        intro.setInferredValuesProgressive(60.0);
        intro.setInferredValuesEgalitarian(70.0);

        intro.setInferredLifestyleSocial(55.0);
        intro.setInferredLifestyleHealth(65.0);
        intro.setInferredLifestyleWorkLife(50.0);
        intro.setInferredLifestyleFinance(60.0);

        intro.setOverallInferenceConfidence(0.7);
        intro.setInferenceReviewed(false);
        intro.setInferenceConfirmed(false);

        return videoIntroRepo.save(intro);
    }

    private void createTestPrompts() {
        List<VideoSegmentPrompt> prompts = new ArrayList<>();

        VideoSegmentPrompt worldview = new VideoSegmentPrompt();
        worldview.setPromptKey("worldview");
        worldview.setCategory(VideoSegmentPrompt.PromptCategory.CORE);
        worldview.setTitle("Your Worldview");
        worldview.setDescription("Share your perspective on life");
        worldview.setRequiredForMatching(true);
        worldview.setDisplayOrder(1);
        prompts.add(worldview);

        VideoSegmentPrompt background = new VideoSegmentPrompt();
        background.setPromptKey("background");
        background.setCategory(VideoSegmentPrompt.PromptCategory.CORE);
        background.setTitle("Your Background");
        background.setDescription("Tell us about where you come from");
        background.setRequiredForMatching(true);
        background.setDisplayOrder(2);
        prompts.add(background);

        VideoSegmentPrompt personality = new VideoSegmentPrompt();
        personality.setPromptKey("personality");
        personality.setCategory(VideoSegmentPrompt.PromptCategory.CORE);
        personality.setTitle("Who You Are");
        personality.setDescription("Describe your personality");
        personality.setRequiredForMatching(true);
        personality.setDisplayOrder(3);
        prompts.add(personality);

        VideoSegmentPrompt relationships = new VideoSegmentPrompt();
        relationships.setPromptKey("relationships");
        relationships.setCategory(VideoSegmentPrompt.PromptCategory.DATING);
        relationships.setTitle("Love & Relationships");
        relationships.setDescription("What you're looking for in a partner");
        relationships.setRequiredForMatching(true);
        relationships.setDisplayOrder(4);
        prompts.add(relationships);

        promptRepo.saveAll(prompts);
    }
}
