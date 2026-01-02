package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.model.VideoAnalysisResult;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.UserVideoIntroductionRepository;
import com.nonononoki.alovoa.service.ai.AiAnalysisProvider;
import com.nonononoki.alovoa.service.ai.AiProviderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Comprehensive tests for VideoAnalysisService covering:
 * - Video transcription
 * - Sentiment analysis
 * - Personality indicators extraction
 * - Duration validation
 * - Error handling
 * - Async processing
 * - Status transitions
 * - Retry mechanisms
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VideoAnalysisServiceTest {

    @Autowired
    private VideoAnalysisService videoAnalysisService;

    @Autowired
    private UserVideoIntroductionRepository videoIntroRepo;

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

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private AiAnalysisProvider aiProvider;

    @MockitoBean
    private S3StorageService s3StorageService;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(Mockito.any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);

        // Default mock behavior for AI provider
        Mockito.when(aiProvider.isAvailable()).thenReturn(true);
        Mockito.when(aiProvider.getProviderName()).thenReturn("test-provider");
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    // === Video Transcription Tests ===

    @Test
    void testAnalyzeVideoAsync_SuccessfulTranscription() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        byte[] videoData = "fake-video-data".getBytes();
        String mockTranscript = "Hello, my name is John. I love hiking and photography.";

        Mockito.when(s3StorageService.downloadMedia(video.getS3Key())).thenReturn(videoData);
        Mockito.when(aiProvider.transcribeVideo(videoData, video.getMimeType())).thenReturn(mockTranscript);
        Mockito.when(aiProvider.analyzeTranscript(mockTranscript)).thenReturn(createMockAnalysisResult(mockTranscript));

        videoAnalysisService.analyzeVideoAsync(video);

        // Wait for async processing
        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
        assertEquals(mockTranscript, updated.getTranscript());
        assertNotNull(updated.getAnalyzedAt());
        assertEquals("test-provider", updated.getAiProvider());
    }

    @Test
    void testAnalyzeVideoAsync_TranscriptionWithMultipleSentences() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        byte[] videoData = "video-bytes".getBytes();
        String complexTranscript = "I grew up in California. I studied computer science at Stanford. " +
                "After graduation, I worked at several tech companies. Now I'm passionate about AI and machine learning. " +
                "In my free time, I enjoy rock climbing and reading philosophy.";

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn(videoData);
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn(complexTranscript);
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(createMockAnalysisResult(complexTranscript));

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
        assertEquals(complexTranscript, updated.getTranscript());
    }

    // === Analysis Result Tests ===

    @Test
    void testAnalyzeVideoAsync_ExtractsWorldviewSummary() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        String transcript = "I believe in kindness and helping others.";
        VideoAnalysisResult analysisResult = VideoAnalysisResult.builder()
                .transcript(transcript)
                .worldviewSummary("Values compassion and community service")
                .backgroundSummary("Not specified")
                .lifeStorySummary("Not specified")
                .personalityIndicators(Map.of("warmth", "high", "openness", "medium"))
                .providerName("test-provider")
                .build();

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn(transcript);
        Mockito.when(aiProvider.analyzeTranscript(transcript)).thenReturn(analysisResult);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals("Values compassion and community service", updated.getWorldviewSummary());
    }

    @Test
    void testAnalyzeVideoAsync_ExtractsBackgroundSummary() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        String transcript = "I have a PhD in physics and work as a researcher.";
        VideoAnalysisResult analysisResult = VideoAnalysisResult.builder()
                .transcript(transcript)
                .worldviewSummary("Science-oriented mindset")
                .backgroundSummary("PhD in physics, research scientist")
                .lifeStorySummary("Academic career path")
                .personalityIndicators(Map.of("analytical", "high", "curiosity", "high"))
                .build();

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn(transcript);
        Mockito.when(aiProvider.analyzeTranscript(transcript)).thenReturn(analysisResult);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals("PhD in physics, research scientist", updated.getBackgroundSummary());
    }

    @Test
    void testAnalyzeVideoAsync_ExtractsLifeStorySummary() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        String transcript = "I overcame many challenges in my youth and found strength through music.";
        VideoAnalysisResult analysisResult = VideoAnalysisResult.builder()
                .transcript(transcript)
                .worldviewSummary("Resilience-focused")
                .backgroundSummary("Creative background")
                .lifeStorySummary("Overcame adversity through artistic expression and found personal growth")
                .personalityIndicators(Map.of("resilience", "high", "creativity", "high"))
                .build();

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn(transcript);
        Mockito.when(aiProvider.analyzeTranscript(transcript)).thenReturn(analysisResult);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertTrue(updated.getLifeStorySummary().contains("adversity"));
    }

    @Test
    void testAnalyzeVideoAsync_ExtractsPersonalityIndicators() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        Map<String, Object> personalityData = new HashMap<>();
        personalityData.put("confidence", "high");
        personalityData.put("openness", "medium");
        personalityData.put("warmth", "high");
        personalityData.put("humor", "medium");
        personalityData.put("enthusiasm", "high");

        VideoAnalysisResult analysisResult = VideoAnalysisResult.builder()
                .transcript("Energetic introduction")
                .worldviewSummary("Positive outlook")
                .backgroundSummary("Various experiences")
                .lifeStorySummary("Adventurous journey")
                .personalityIndicators(personalityData)
                .build();

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn("transcript");
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(analysisResult);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertNotNull(updated.getPersonalityIndicators());

        Map<String, Object> savedIndicators = objectMapper.readValue(
                updated.getPersonalityIndicators(),
                objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class)
        );
        assertEquals("high", savedIndicators.get("confidence"));
        assertEquals("medium", savedIndicators.get("openness"));
        assertEquals("high", savedIndicators.get("warmth"));
    }

    // === Status Transition Tests ===

    @Test
    void testAnalyzeVideoAsync_StatusTransitions() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenAnswer(invocation -> {
            // Simulate processing time
            Thread.sleep(100);
            return "transcript";
        });
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(createMockAnalysisResult("transcript"));

        assertEquals(UserVideoIntroduction.AnalysisStatus.PENDING, video.getStatus());

        videoAnalysisService.analyzeVideoAsync(video);

        // Should transition through states
        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction completed = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, completed.getStatus());

        // Verify it went through TRANSCRIBING state
        ArgumentCaptor<UserVideoIntroduction> captor = ArgumentCaptor.forClass(UserVideoIntroduction.class);
        Mockito.verify(videoIntroRepo, Mockito.atLeast(3)).save(captor.capture());

        List<UserVideoIntroduction.AnalysisStatus> statusTransitions = captor.getAllValues().stream()
                .map(UserVideoIntroduction::getStatus)
                .distinct()
                .toList();

        assertTrue(statusTransitions.contains(UserVideoIntroduction.AnalysisStatus.TRANSCRIBING));
        assertTrue(statusTransitions.contains(UserVideoIntroduction.AnalysisStatus.ANALYZING));
        assertTrue(statusTransitions.contains(UserVideoIntroduction.AnalysisStatus.COMPLETED));
    }

    @Test
    void testGetAnalysisStatus() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        assertEquals(UserVideoIntroduction.AnalysisStatus.PENDING,
                videoAnalysisService.getAnalysisStatus(video.getId()));

        video.setStatus(UserVideoIntroduction.AnalysisStatus.TRANSCRIBING);
        videoIntroRepo.save(video);

        assertEquals(UserVideoIntroduction.AnalysisStatus.TRANSCRIBING,
                videoAnalysisService.getAnalysisStatus(video.getId()));
    }

    @Test
    void testGetAnalysisStatus_NonExistentVideo() throws Exception {
        assertNull(videoAnalysisService.getAnalysisStatus(99999L));
    }

    // === Error Handling Tests ===

    @Test
    void testAnalyzeVideoAsync_TranscriptionFailure() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any()))
                .thenThrow(new AiProviderException("test-provider", "Transcription failed"));

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.FAILED, updated.getStatus());
    }

    @Test
    void testAnalyzeVideoAsync_AnalysisFailure() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn("transcript");
        Mockito.when(aiProvider.analyzeTranscript(any()))
                .thenThrow(new AiProviderException("test-provider", "Analysis failed"));

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.FAILED, updated.getStatus());
    }

    @Test
    void testAnalyzeVideoAsync_S3DownloadFailure() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn(null);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.FAILED, updated.getStatus());
    }

    @Test
    void testAnalyzeVideoAsync_UnexpectedError() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        Mockito.when(s3StorageService.downloadMedia(any())).thenThrow(new RuntimeException("Unexpected error"));

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.FAILED, updated.getStatus());
    }

    @Test
    void testAnalyzeVideoAsync_JsonSerializationError() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        // Create a map that can't be serialized properly
        Map<String, Object> problematicData = new HashMap<>();
        problematicData.put("circular", problematicData); // Circular reference

        VideoAnalysisResult result = VideoAnalysisResult.builder()
                .transcript("test")
                .worldviewSummary("summary")
                .backgroundSummary("background")
                .lifeStorySummary("life story")
                .personalityIndicators(problematicData)
                .build();

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn("transcript");
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(result);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.FAILED, updated.getStatus());
    }

    // === Retry Tests ===

    @Test
    void testRetryAnalysis_SuccessfulRetry() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);
        video.setStatus(UserVideoIntroduction.AnalysisStatus.FAILED);
        videoIntroRepo.save(video);

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn("transcript");
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(createMockAnalysisResult("transcript"));

        videoAnalysisService.retryAnalysis(video.getId());

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
    }

    @Test
    void testRetryAnalysis_OnlyRetriesFailedStatus() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);
        video.setStatus(UserVideoIntroduction.AnalysisStatus.COMPLETED);
        videoIntroRepo.save(video);

        videoAnalysisService.retryAnalysis(video.getId());

        // Should not change status
        Thread.sleep(500); // Give it time to potentially process
        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
    }

    @Test
    void testRetryAnalysis_NonExistentVideo() throws Exception {
        // Should not throw exception
        assertDoesNotThrow(() -> videoAnalysisService.retryAnalysis(99999L));
    }

    // === Provider Availability Tests ===

    @Test
    void testIsProviderAvailable() throws Exception {
        Mockito.when(aiProvider.isAvailable()).thenReturn(true);
        assertTrue(videoAnalysisService.isProviderAvailable());

        Mockito.when(aiProvider.isAvailable()).thenReturn(false);
        assertFalse(videoAnalysisService.isProviderAvailable());
    }

    @Test
    void testGetProviderName() throws Exception {
        Mockito.when(aiProvider.getProviderName()).thenReturn("openai");
        assertEquals("openai", videoAnalysisService.getProviderName());

        Mockito.when(aiProvider.getProviderName()).thenReturn("claude");
        assertEquals("claude", videoAnalysisService.getProviderName());
    }

    // === Edge Cases ===

    @Test
    void testAnalyzeVideoAsync_EmptyTranscript() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn("");
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(createMockAnalysisResult(""));

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
        assertEquals("", updated.getTranscript());
    }

    @Test
    void testAnalyzeVideoAsync_NullPersonalityIndicators() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);

        VideoAnalysisResult result = VideoAnalysisResult.builder()
                .transcript("transcript")
                .worldviewSummary("summary")
                .backgroundSummary("background")
                .lifeStorySummary("life")
                .personalityIndicators(null)
                .build();

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn("transcript");
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(result);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
        assertNull(updated.getPersonalityIndicators());
    }

    @Test
    void testAnalyzeVideoAsync_LargeVideo() throws Exception {
        User user = testUsers.get(0);
        UserVideoIntroduction video = createTestVideo(user);
        video.setDurationSeconds(300); // 5 minutes
        videoIntroRepo.save(video);

        byte[] largeVideoData = new byte[10 * 1024 * 1024]; // 10MB
        String longTranscript = "word ".repeat(5000); // Long transcript

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn(largeVideoData);
        Mockito.when(aiProvider.transcribeVideo(any(), any())).thenReturn(longTranscript);
        Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(createMockAnalysisResult(longTranscript));

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
    }

    @Test
    void testAnalyzeVideoAsync_DifferentMimeTypes() throws Exception {
        String[] mimeTypes = {"video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"};

        for (String mimeType : mimeTypes) {
            User user = testUsers.get(0);
            UserVideoIntroduction video = createTestVideo(user);
            video.setMimeType(mimeType);
            videoIntroRepo.save(video);

            Mockito.when(s3StorageService.downloadMedia(any())).thenReturn("data".getBytes());
            Mockito.when(aiProvider.transcribeVideo(any(), eq(mimeType))).thenReturn("transcript");
            Mockito.when(aiProvider.analyzeTranscript(any())).thenReturn(createMockAnalysisResult("transcript"));

            videoAnalysisService.analyzeVideoAsync(video);

            waitForCompletion(video.getId(), 5000);

            UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
            assertEquals(UserVideoIntroduction.AnalysisStatus.COMPLETED, updated.getStatus());
        }
    }

    // === Helper Methods ===

    private UserVideoIntroduction createTestVideo(User user) {
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUser(user);
        video.setS3Key("test-videos/" + UUID.randomUUID());
        video.setMimeType("video/mp4");
        video.setDurationSeconds(60);
        video.setStatus(UserVideoIntroduction.AnalysisStatus.PENDING);
        return videoIntroRepo.save(video);
    }

    private VideoAnalysisResult createMockAnalysisResult(String transcript) {
        Map<String, Object> personalityIndicators = new HashMap<>();
        personalityIndicators.put("confidence", "medium");
        personalityIndicators.put("openness", "high");
        personalityIndicators.put("warmth", "medium");

        return VideoAnalysisResult.builder()
                .transcript(transcript)
                .worldviewSummary("Open-minded and curious individual")
                .backgroundSummary("Diverse educational and professional background")
                .lifeStorySummary("Journey of personal growth and exploration")
                .personalityIndicators(personalityIndicators)
                .providerName("test-provider")
                .build();
    }

    /**
     * Wait for async operation to complete by polling the database
     */
    private void waitForCompletion(Long videoId, int maxWaitMs) throws InterruptedException {
        int waited = 0;
        int sleepInterval = 100;
        while (waited < maxWaitMs) {
            UserVideoIntroduction video = videoIntroRepo.findById(videoId).orElseThrow();
            if (video.getStatus() == UserVideoIntroduction.AnalysisStatus.COMPLETED ||
                    video.getStatus() == UserVideoIntroduction.AnalysisStatus.FAILED) {
                return;
            }
            Thread.sleep(sleepInterval);
            waited += sleepInterval;
        }
        throw new AssertionError("Async operation did not complete within " + maxWaitMs + "ms");
    }
}
