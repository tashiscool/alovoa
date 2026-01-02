package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.AssessmentQuestion;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentResponse;
import com.nonononoki.alovoa.entity.user.UserIntakeProgress;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.AssessmentResponseDto;
import com.nonononoki.alovoa.model.IntakeProgressDto;
import com.nonononoki.alovoa.model.IntakeStep;
import com.nonononoki.alovoa.repo.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntakeServiceTest {

    @Autowired
    private IntakeService intakeService;

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
    private UserIntakeProgressRepository intakeProgressRepo;

    @Autowired
    private AssessmentQuestionRepository questionRepo;

    @Autowired
    private UserAssessmentResponseRepository responseRepo;

    @Autowired
    private UserVideoIntroductionRepository videoIntroRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private VideoAnalysisService videoAnalysisService;

    @MockitoBean
    private S3StorageService s3StorageService;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);

        // Create test assessment questions
        createTestQuestions();
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    void testGetIntakeProgress_NewUser() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        IntakeProgressDto progress = intakeService.getIntakeProgress();

        assertNotNull(progress);
        assertFalse(progress.isQuestionsComplete());
        assertFalse(progress.isVideoIntroComplete());
        assertFalse(progress.isPicturesComplete());
        assertFalse(progress.isIntakeComplete());
        assertEquals(0, progress.getQuestionsAnswered());
        assertEquals(0, progress.getCompletionPercentage());
        assertEquals(IntakeStep.QUESTIONS, progress.getCurrentStep());
        assertFalse(progress.isCanProceedToNext());
        assertNotNull(progress.getBlockedReason());
    }

    @Test
    void testGetCoreQuestions() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<Map<String, Object>> questions = intakeService.getCoreQuestions();

        assertNotNull(questions);
        // Should return questions for all core categories
        assertTrue(questions.size() >= 1);

        // Check question structure
        if (!questions.isEmpty()) {
            Map<String, Object> firstQuestion = questions.get(0);
            assertTrue(firstQuestion.containsKey("id"));
            assertTrue(firstQuestion.containsKey("text"));
            assertTrue(firstQuestion.containsKey("category"));
            assertTrue(firstQuestion.containsKey("answered"));
            assertFalse((Boolean) firstQuestion.get("answered"));
        }
    }

    @Test
    void testSubmitCoreAnswers() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Create test question
        AssessmentQuestion question = createSingleCoreQuestion("dealbreakers_safety", "Q1");

        // Submit answer
        List<AssessmentResponseDto> responses = new ArrayList<>();
        AssessmentResponseDto dto = new AssessmentResponseDto();
        dto.setQuestionId("Q1");
        dto.setNumericResponse(3);
        responses.add(dto);

        Map<String, Object> result = intakeService.submitCoreAnswers(responses);

        assertNotNull(result);
        assertEquals(1, result.get("saved"));
        assertTrue((Integer) result.get("totalAnswered") >= 1);

        // Verify response was saved
        Optional<UserAssessmentResponse> savedResponse = responseRepo.findByUserAndQuestion(user, question);
        assertTrue(savedResponse.isPresent());
        assertEquals(3, savedResponse.get().getNumericResponse());
    }

    @Test
    void testSubmitCoreAnswers_UpdateExisting() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        AssessmentQuestion question = createSingleCoreQuestion("dealbreakers_safety", "Q2");

        // Submit initial answer
        List<AssessmentResponseDto> responses = new ArrayList<>();
        AssessmentResponseDto dto = new AssessmentResponseDto();
        dto.setQuestionId("Q2");
        dto.setNumericResponse(3);
        responses.add(dto);
        intakeService.submitCoreAnswers(responses);

        // Update answer
        dto.setNumericResponse(5);
        intakeService.submitCoreAnswers(responses);

        // Verify updated
        Optional<UserAssessmentResponse> response = responseRepo.findByUserAndQuestion(user, question);
        assertTrue(response.isPresent());
        assertEquals(5, response.get().getNumericResponse());
    }

    @Test
    void testUploadVideoIntroduction() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(s3StorageService.uploadMedia(any(), any(), any())).thenReturn("test-s3-key");

        // Mark questions as complete first
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        // Create mock video file
        MockMultipartFile videoFile = new MockMultipartFile(
                "video",
                "test-video.mp4",
                "video/mp4",
                "test video content".getBytes()
        );

        Map<String, Object> result = intakeService.uploadVideoIntroduction(videoFile);

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertTrue(result.containsKey("videoId"));

        // Verify video was saved
        Optional<UserVideoIntroduction> video = videoIntroRepo.findByUser(user);
        assertTrue(video.isPresent());
        assertEquals("test-s3-key", video.get().getS3Key());
        assertEquals("video/mp4", video.get().getMimeType());
    }

    @Test
    void testUploadVideoIntroduction_WithSkipAi() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(s3StorageService.uploadMedia(any(), any(), any())).thenReturn("test-s3-key");

        // Mark questions as complete
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        MockMultipartFile videoFile = new MockMultipartFile(
                "video",
                "test-video.mp4",
                "video/mp4",
                "test video content".getBytes()
        );

        Map<String, Object> result = intakeService.uploadVideoIntroduction(videoFile, true);

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("manualEntry"));

        Optional<UserVideoIntroduction> video = videoIntroRepo.findByUser(user);
        assertTrue(video.isPresent());
        assertTrue(video.get().getManualEntry());
        assertEquals(UserVideoIntroduction.AnalysisStatus.SKIPPED, video.get().getStatus());
    }

    @Test
    void testUploadVideoIntroduction_InvalidFormat() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Mark questions as complete
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        // Create mock non-video file
        MockMultipartFile textFile = new MockMultipartFile(
                "video",
                "test.txt",
                "text/plain",
                "test content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            intakeService.uploadVideoIntroduction(textFile);
        });
    }

    @Test
    void testSubmitManualProfileInfo() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(s3StorageService.uploadMedia(any(), any(), any())).thenReturn("test-s3-key");

        // Upload video first
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        MockMultipartFile videoFile = new MockMultipartFile(
                "video",
                "test.mp4",
                "video/mp4",
                "test".getBytes()
        );
        intakeService.uploadVideoIntroduction(videoFile);

        // Submit profile info
        Map<String, String> profileInfo = new HashMap<>();
        profileInfo.put("worldview", "I believe in kindness and empathy");
        profileInfo.put("background", "Software engineer from California");
        profileInfo.put("lifeStory", "Grew up in a small town, moved to the city for college");

        Map<String, Object> result = intakeService.submitManualProfileInfo(profileInfo);

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("hasWorldview"));
        assertTrue((Boolean) result.get("hasBackground"));
        assertTrue((Boolean) result.get("hasLifeStory"));

        // Verify video was updated
        Optional<UserVideoIntroduction> video = videoIntroRepo.findByUser(user);
        assertTrue(video.isPresent());
        assertEquals("I believe in kindness and empathy", video.get().getWorldviewSummary());
        assertEquals("Software engineer from California", video.get().getBackgroundSummary());
        assertTrue(video.get().getManualEntry());
    }

    @Test
    void testSubmitManualProfileInfo_NoVideoUploaded() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, String> profileInfo = new HashMap<>();
        profileInfo.put("worldview", "Test worldview");

        assertThrows(IllegalArgumentException.class, () -> {
            intakeService.submitManualProfileInfo(profileInfo);
        });
    }

    @Test
    void testSkipVideoAnalysis() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(s3StorageService.uploadMedia(any(), any(), any())).thenReturn("test-s3-key");

        // Upload video first
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        MockMultipartFile videoFile = new MockMultipartFile(
                "video",
                "test.mp4",
                "video/mp4",
                "test".getBytes()
        );
        intakeService.uploadVideoIntroduction(videoFile);

        // Skip analysis
        Map<String, Object> result = intakeService.skipVideoAnalysis();

        assertTrue((Boolean) result.get("success"));

        Optional<UserVideoIntroduction> video = videoIntroRepo.findByUser(user);
        assertTrue(video.isPresent());
        assertEquals(UserVideoIntroduction.AnalysisStatus.SKIPPED, video.get().getStatus());
        assertTrue(video.get().getManualEntry());
    }

    @Test
    void testValidateCanStartStep_Questions() throws Exception {
        User user = testUsers.get(0);

        // Questions is always allowed
        assertDoesNotThrow(() -> intakeService.validateCanStartStep(user, IntakeStep.QUESTIONS));
    }

    @Test
    void testValidateCanStartStep_Video_WithoutQuestions() throws Exception {
        User user = testUsers.get(0);

        // Should throw exception if questions not complete
        assertThrows(AlovoaException.class, () -> {
            intakeService.validateCanStartStep(user, IntakeStep.VIDEO);
        });
    }

    @Test
    void testValidateCanStartStep_Video_WithQuestions() throws Exception {
        User user = testUsers.get(0);

        // Mark questions complete
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        // Should not throw
        assertDoesNotThrow(() -> intakeService.validateCanStartStep(user, IntakeStep.VIDEO));
    }

    @Test
    void testValidateCanStartStep_Photos_WithoutVideo() throws Exception {
        User user = testUsers.get(0);

        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        // Should throw exception if video not complete
        assertThrows(AlovoaException.class, () -> {
            intakeService.validateCanStartStep(user, IntakeStep.PHOTOS);
        });
    }

    @Test
    void testValidateCanStartStep_Photos_WithVideo() throws Exception {
        User user = testUsers.get(0);

        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        progress.setVideoIntroComplete(true);
        intakeProgressRepo.save(progress);

        // Should not throw
        assertDoesNotThrow(() -> intakeService.validateCanStartStep(user, IntakeStep.PHOTOS));
    }

    @Test
    void testGetCurrentStep_NewUser() throws Exception {
        User user = testUsers.get(0);
        UserIntakeProgress progress = getOrCreateProgress(user);

        IntakeStep currentStep = intakeService.getCurrentStep(progress);

        assertEquals(IntakeStep.QUESTIONS, currentStep);
    }

    @Test
    void testGetCurrentStep_QuestionsComplete() throws Exception {
        User user = testUsers.get(0);
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        intakeProgressRepo.save(progress);

        IntakeStep currentStep = intakeService.getCurrentStep(progress);

        assertEquals(IntakeStep.VIDEO, currentStep);
    }

    @Test
    void testGetCurrentStep_VideoComplete() throws Exception {
        User user = testUsers.get(0);
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        progress.setVideoIntroComplete(true);
        intakeProgressRepo.save(progress);

        IntakeStep currentStep = intakeService.getCurrentStep(progress);

        assertEquals(IntakeStep.PHOTOS, currentStep);
    }

    @Test
    void testGetCurrentStep_AllComplete() throws Exception {
        User user = testUsers.get(0);
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        progress.setVideoIntroComplete(true);
        progress.setPicturesComplete(true);
        intakeProgressRepo.save(progress);

        IntakeStep currentStep = intakeService.getCurrentStep(progress);

        assertNull(currentStep); // All steps complete
    }

    @Test
    void testCanProceedToNext() throws Exception {
        User user = testUsers.get(0);
        UserIntakeProgress progress = getOrCreateProgress(user);

        // Initially can't proceed
        assertFalse(intakeService.canProceedToNext(progress));

        // After questions complete
        progress.setQuestionsComplete(true);
        assertTrue(intakeService.canProceedToNext(progress));

        // After video complete
        progress.setVideoIntroComplete(true);
        assertTrue(intakeService.canProceedToNext(progress));

        // After photos complete
        progress.setPicturesComplete(true);
        assertTrue(intakeService.canProceedToNext(progress));
    }

    @Test
    void testGetBlockedReason() throws Exception {
        User user = testUsers.get(0);
        UserIntakeProgress progress = getOrCreateProgress(user);

        String reason = intakeService.getBlockedReason(progress);
        assertNotNull(reason);
        assertTrue(reason.contains("questions"));

        progress.setQuestionsComplete(true);
        reason = intakeService.getBlockedReason(progress);
        assertTrue(reason.contains("video"));

        progress.setVideoIntroComplete(true);
        reason = intakeService.getBlockedReason(progress);
        assertTrue(reason.contains("photo"));

        progress.setPicturesComplete(true);
        reason = intakeService.getBlockedReason(progress);
        assertNull(reason);
    }

    @Test
    void testIsIntakeComplete() throws Exception {
        User user = testUsers.get(0);

        assertFalse(intakeService.isIntakeComplete(user));

        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setQuestionsComplete(true);
        progress.setVideoIntroComplete(true);
        progress.setPicturesComplete(true);
        progress.updateIntakeComplete();
        intakeProgressRepo.save(progress);

        assertTrue(intakeService.isIntakeComplete(user));
    }

    @Test
    void testUploadAudioIntroduction() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio",
                "test-audio.mp3",
                "audio/mpeg",
                "test audio content".getBytes()
        );

        Map<String, Object> result = intakeService.uploadAudioIntroduction(audioFile);

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));

        // Verify progress updated
        Optional<UserIntakeProgress> progress = intakeProgressRepo.findByUser(user);
        assertTrue(progress.isPresent());
        assertTrue(progress.get().getAudioIntroComplete());
    }

    @Test
    void testUploadAudioIntroduction_InvalidFormat() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        MockMultipartFile textFile = new MockMultipartFile(
                "audio",
                "test.txt",
                "text/plain",
                "test content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            intakeService.uploadAudioIntroduction(textFile);
        });
    }

    @Test
    void testProgressPercentageCalculation() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // 0% initially
        IntakeProgressDto progress = intakeService.getIntakeProgress();
        assertEquals(0, progress.getCompletionPercentage());

        // Complete questions
        UserIntakeProgress dbProgress = getOrCreateProgress(user);
        dbProgress.setQuestionsComplete(true);
        intakeProgressRepo.save(dbProgress);
        progress = intakeService.getIntakeProgress();
        assertTrue(progress.getCompletionPercentage() > 0);
        assertTrue(progress.getCompletionPercentage() < 100);

        // Complete video
        dbProgress.setVideoIntroComplete(true);
        intakeProgressRepo.save(dbProgress);
        progress = intakeService.getIntakeProgress();
        assertTrue(progress.getCompletionPercentage() > 33);
        assertTrue(progress.getCompletionPercentage() < 100);

        // Complete photos
        dbProgress.setPicturesComplete(true);
        intakeProgressRepo.save(dbProgress);
        progress = intakeService.getIntakeProgress();
        assertEquals(100, progress.getCompletionPercentage());
    }

    // Helper methods

    private UserIntakeProgress getOrCreateProgress(User user) {
        return intakeProgressRepo.findByUser(user)
                .orElseGet(() -> {
                    UserIntakeProgress progress = new UserIntakeProgress();
                    progress.setUser(user);
                    return intakeProgressRepo.save(progress);
                });
    }

    private void createTestQuestions() {
        // Create a test question for each core category
        List<String> coreCategories = List.of(
                "dealbreakers_safety",
                "relationship_dynamics",
                "attachment_emotional",
                "lifestyle_compatibility",
                "family_future",
                "sex_intimacy",
                "personality_temperament",
                "hypotheticals_scenarios",
                "location_specific",
                "values_politics"
        );

        int index = 1;
        for (String category : coreCategories) {
            createSingleCoreQuestion(category, "CORE_Q" + index++);
        }
    }

    private AssessmentQuestion createSingleCoreQuestion(String subcategory, String externalId) {
        if (questionRepo.existsByExternalId(externalId)) {
            return questionRepo.findByExternalId(externalId).get();
        }

        AssessmentQuestion question = new AssessmentQuestion();
        question.setExternalId(externalId);
        question.setText("Test question for " + subcategory);
        question.setCategory(AssessmentQuestion.QuestionCategory.DEALBREAKER);
        question.setSubcategory(subcategory);
        question.setResponseScale(AssessmentQuestion.ResponseScale.LIKERT_5);
        question.setCoreQuestion(true);
        question.setActive(true);
        question.setDisplayOrder(0);
        return questionRepo.save(question);
    }
}
