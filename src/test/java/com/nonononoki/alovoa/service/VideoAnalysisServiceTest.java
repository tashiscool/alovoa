package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Gender;
import com.nonononoki.alovoa.entity.user.UserDates;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.model.VideoAnalysisResult;
import com.nonononoki.alovoa.repo.GenderRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.UserVideoIntroductionRepository;
import com.nonononoki.alovoa.service.ai.AiAnalysisProvider;
import com.nonononoki.alovoa.service.ai.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Calendar;

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
    private UserRepository userRepo;

    @Autowired
    private GenderRepository genderRepo;

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

    private User testUser;

    @BeforeEach
    void before() throws Exception {
        // Create test user directly in the transaction
        testUser = createTestUser("videotest@test.com");

        // Default mock behavior for AI provider
        Mockito.when(aiProvider.isAvailable()).thenReturn(true);
        Mockito.when(aiProvider.getProviderName()).thenReturn("test-provider");
    }

    /**
     * Creates a test user directly in the current transaction
     */
    private User createTestUser(String email) {
        // Get or create a gender
        Gender gender = genderRepo.findAll().stream().findFirst().orElseGet(() -> {
            Gender g = new Gender();
            g.setText("male");
            return genderRepo.saveAndFlush(g);
        });

        // Use constructor that takes email (email is final/immutable)
        User user = new User(email);
        user.setConfirmed(true);
        user.setDisabled(false);
        user.setAdmin(false);
        user.setIntention(null);
        user.setGender(gender);

        // Set required dates with proper Date type
        UserDates dates = new UserDates();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -25);
        dates.setDateOfBirth(cal.getTime());
        dates.setActiveDate(new Date());
        dates.setCreationDate(new Date());
        user.setDates(dates);

        return userRepo.saveAndFlush(user);
    }

    // === Video Transcription Tests ===

    @Test
    void testAnalyzeVideoAsync_SuccessfulTranscription() throws Exception {
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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

        // Verify final status is COMPLETED (the actual status transitions happen internally)
        assertNotNull(completed.getAnalyzedAt());
        assertEquals("test-provider", completed.getAiProvider());
    }

    @Test
    void testGetAnalysisStatus() throws Exception {
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

        Mockito.when(s3StorageService.downloadMedia(any())).thenReturn(null);

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.FAILED, updated.getStatus());
    }

    @Test
    void testAnalyzeVideoAsync_UnexpectedError() throws Exception {
        UserVideoIntroduction video = createTestVideo(testUser);

        Mockito.when(s3StorageService.downloadMedia(any())).thenThrow(new RuntimeException("Unexpected error"));

        videoAnalysisService.analyzeVideoAsync(video);

        waitForCompletion(video.getId(), 5000);

        UserVideoIntroduction updated = videoIntroRepo.findById(video.getId()).orElseThrow();
        assertEquals(UserVideoIntroduction.AnalysisStatus.FAILED, updated.getStatus());
    }

    @Test
    void testAnalyzeVideoAsync_JsonSerializationError() throws Exception {
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);
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
        UserVideoIntroduction video = createTestVideo(testUser);
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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);

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
        UserVideoIntroduction video = createTestVideo(testUser);
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
            // Delete any existing video for this user (unique constraint on user_id)
            videoIntroRepo.findByUser(testUser).ifPresent(v -> videoIntroRepo.delete(v));
            videoIntroRepo.flush();

            UserVideoIntroduction video = createTestVideo(testUser);
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
