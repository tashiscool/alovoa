package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideo;
import com.nonononoki.alovoa.entity.user.UserVideoVerification;
import com.nonononoki.alovoa.entity.user.UserVideoVerification.VerificationStatus;
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

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VideoVerificationServiceTest {

    @Autowired
    private VideoVerificationService videoVerificationService;

    @Autowired
    private UserVideoRepository videoRepo;

    @Autowired
    private UserVideoVerificationRepository verificationRepo;

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
    void testGetVerificationStatus_NoPreviousVerification() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        Map<String, Object> status = videoVerificationService.getVerificationStatus();

        assertNotNull(status);
        assertFalse((Boolean) status.get("verified"));
        assertEquals("NOT_STARTED", status.get("status"));
    }

    @Test
    void testGetVerificationStatus_VerificationInProgress() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create verification in progress
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.PENDING);
        verification.setCreatedAt(new Date());
        verificationRepo.save(verification);

        Map<String, Object> status = videoVerificationService.getVerificationStatus();

        assertNotNull(status);
        assertFalse((Boolean) status.get("verified"));
        assertEquals(VerificationStatus.PENDING.name(), status.get("status"));
    }

    @Test
    void testGetVerificationStatus_Verified() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create completed verification
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.VERIFIED);
        verification.setCreatedAt(new Date());
        verification.setVerifiedAt(new Date());
        verification.setFaceMatchScore(0.95);
        verification.setLivenessScore(0.92);
        verificationRepo.save(verification);

        Map<String, Object> status = videoVerificationService.getVerificationStatus();

        assertNotNull(status);
        assertTrue((Boolean) status.get("verified"));
        assertEquals(VerificationStatus.VERIFIED.name(), status.get("status"));
    }

    @Test
    void testGenerateVerificationChallenge() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        Map<String, Object> challenge = videoVerificationService.generateVerificationChallenge();

        assertNotNull(challenge);
        assertTrue(challenge.containsKey("challengeId"));
        assertTrue(challenge.containsKey("gestures"));
        assertTrue(challenge.containsKey("expiresAt"));

        @SuppressWarnings("unchecked")
        List<String> gestures = (List<String>) challenge.get("gestures");
        assertFalse(gestures.isEmpty());
    }

    @Test
    void testGetVideos_NoVideos() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        List<UserVideo> videos = videoVerificationService.getVideos();

        assertNotNull(videos);
        assertTrue(videos.isEmpty());
    }

    @Test
    void testGetVideos_WithVideos() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create some videos
        UserVideo video1 = new UserVideo();
        video1.setUser(user);
        video1.setVideoUrl("http://example.com/video1.mp4");
        video1.setUploadedAt(new Date());
        video1.setDurationSeconds(30);
        videoRepo.save(video1);

        UserVideo video2 = new UserVideo();
        video2.setUser(user);
        video2.setVideoUrl("http://example.com/video2.mp4");
        video2.setUploadedAt(new Date());
        video2.setDurationSeconds(45);
        videoRepo.save(video2);

        List<UserVideo> videos = videoVerificationService.getVideos();

        assertEquals(2, videos.size());
    }

    @Test
    void testDeleteVideo() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create a video
        UserVideo video = new UserVideo();
        video.setUser(user);
        video.setVideoUrl("http://example.com/video.mp4");
        video.setUploadedAt(new Date());
        video.setDurationSeconds(30);
        video = videoRepo.save(video);

        Long videoId = video.getId();

        // Delete
        videoVerificationService.deleteVideo(videoId);

        // Verify deleted
        assertFalse(videoRepo.findById(videoId).isPresent());
    }

    @Test
    void testDeleteVideo_WrongUser() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user1);

        // Create video for user2
        UserVideo video = new UserVideo();
        video.setUser(user2);
        video.setVideoUrl("http://example.com/video.mp4");
        video.setUploadedAt(new Date());
        video = videoRepo.save(video);

        Long videoId = video.getId();

        // User1 should not be able to delete user2's video
        assertThrows(Exception.class, () ->
                videoVerificationService.deleteVideo(videoId));
    }

    @Test
    void testVerificationScoring() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create verification with various scores
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.VERIFIED);
        verification.setCreatedAt(new Date());
        verification.setVerifiedAt(new Date());
        verification.setFaceMatchScore(0.85);
        verification.setLivenessScore(0.90);
        verification.setDeepfakeScore(0.05);
        verification.setGestureCompletionScore(0.88);
        verificationRepo.save(verification);

        Map<String, Object> status = videoVerificationService.getVerificationStatus();

        assertTrue((Boolean) status.get("verified"));
        assertTrue(status.containsKey("scores"));

        @SuppressWarnings("unchecked")
        Map<String, Object> scores = (Map<String, Object>) status.get("scores");
        assertEquals(0.85, scores.get("faceMatch"));
        assertEquals(0.90, scores.get("liveness"));
    }

    @Test
    void testVerificationFailed() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create failed verification
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setCreatedAt(new Date());
        verification.setFaceMatchScore(0.45);  // Below threshold
        verification.setRejectionReason("Face match score too low");
        verificationRepo.save(verification);

        Map<String, Object> status = videoVerificationService.getVerificationStatus();

        assertFalse((Boolean) status.get("verified"));
        assertEquals(VerificationStatus.REJECTED.name(), status.get("status"));
        assertTrue(status.containsKey("reason"));
    }

    @Test
    void testMultipleVerificationAttempts() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // First failed attempt
        UserVideoVerification attempt1 = new UserVideoVerification();
        attempt1.setUser(user);
        attempt1.setStatus(VerificationStatus.REJECTED);
        attempt1.setCreatedAt(new Date(System.currentTimeMillis() - 3600000)); // 1 hour ago
        verificationRepo.save(attempt1);

        // Second successful attempt
        UserVideoVerification attempt2 = new UserVideoVerification();
        attempt2.setUser(user);
        attempt2.setStatus(VerificationStatus.VERIFIED);
        attempt2.setCreatedAt(new Date());
        attempt2.setVerifiedAt(new Date());
        attempt2.setFaceMatchScore(0.92);
        verificationRepo.save(attempt2);

        Map<String, Object> status = videoVerificationService.getVerificationStatus();

        // Should show as verified (most recent status)
        assertTrue((Boolean) status.get("verified"));
    }

    @Test
    void testVerificationExpiry() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create expired verification
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.EXPIRED);
        verification.setCreatedAt(new Date(System.currentTimeMillis() - 86400000 * 365)); // 1 year ago
        verification.setVerifiedAt(new Date(System.currentTimeMillis() - 86400000 * 365));
        verificationRepo.save(verification);

        Map<String, Object> status = videoVerificationService.getVerificationStatus();

        // Expired verification should not count as verified
        assertFalse((Boolean) status.get("verified"));
        assertEquals(VerificationStatus.EXPIRED.name(), status.get("status"));
    }

    @Test
    void testVerificationChallengeGestureTypes() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Generate multiple challenges to check variety
        for (int i = 0; i < 5; i++) {
            Map<String, Object> challenge = videoVerificationService.generateVerificationChallenge();

            @SuppressWarnings("unchecked")
            List<String> gestures = (List<String>) challenge.get("gestures");

            // All gestures should be from valid set
            for (String gesture : gestures) {
                assertTrue(isValidGesture(gesture),
                        "Invalid gesture: " + gesture);
            }
        }
    }

    private boolean isValidGesture(String gesture) {
        // Valid gestures for liveness detection
        return gesture.equals("BLINK") ||
                gesture.equals("SMILE") ||
                gesture.equals("TURN_LEFT") ||
                gesture.equals("TURN_RIGHT") ||
                gesture.equals("NOD") ||
                gesture.equals("RAISE_EYEBROWS") ||
                gesture.equals("TILT_HEAD");
    }
}
