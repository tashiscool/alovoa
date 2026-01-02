package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAudio;
import com.nonononoki.alovoa.entity.user.UserImage;
import com.nonononoki.alovoa.entity.user.UserProfilePicture;
import com.nonononoki.alovoa.entity.user.UserVerificationPicture;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MediaServiceTest {

    @Autowired
    private MediaService mediaService;

    @MockitoBean
    private S3StorageService s3StorageService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserImageRepository userImageRepository;

    @MockitoBean
    private UserProfilePictureRepository userProfilePictureRepository;

    @MockitoBean
    private UserAudioRepository userAudioRepository;

    @MockitoBean
    private UserVerificationPictureRepository userVerificationPictureRepository;

    @MockitoBean
    private UserVideoIntroductionRepository userVideoIntroductionRepository;

    private static final byte[] TEST_IMAGE_DATA = "test image data".getBytes();
    private static final byte[] TEST_AUDIO_DATA = "test audio data".getBytes();
    private static final byte[] TEST_VIDEO_DATA = "test video data".getBytes();
    private static final String TEST_S3_KEY = "profile/test-uuid.webp";
    private static final UUID TEST_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(s3StorageService, userRepository, userImageRepository, userProfilePictureRepository,
                userAudioRepository, userVerificationPictureRepository, userVideoIntroductionRepository);
    }

    // Profile Picture Tests

    @Test
    void testGetProfilePictureSuccess() throws Exception {
        // Arrange
        UserProfilePicture profilePic = new UserProfilePicture();
        profilePic.setUuid(TEST_UUID);
        profilePic.setS3Key(TEST_S3_KEY);
        profilePic.setBinMime("image/webp");

        when(userProfilePictureRepository.findByUuid(TEST_UUID)).thenReturn(profilePic);
        when(s3StorageService.downloadMedia(TEST_S3_KEY)).thenReturn(TEST_IMAGE_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getProfilePicture(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(TEST_IMAGE_DATA, response.getBody());
        assertEquals(MediaType.parseMediaType("image/webp"), response.getHeaders().getContentType());
        assertEquals(TEST_IMAGE_DATA.length, response.getHeaders().getContentLength());

        verify(userProfilePictureRepository).findByUuid(TEST_UUID);
        verify(s3StorageService).downloadMedia(TEST_S3_KEY);
    }

    @Test
    void testGetProfilePictureNotFound() throws Exception {
        // Arrange
        when(userProfilePictureRepository.findByUuid(TEST_UUID)).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getProfilePicture(TEST_UUID);

        // Assert
        assertNull(response);
        verify(userProfilePictureRepository).findByUuid(TEST_UUID);
        verify(s3StorageService, never()).downloadMedia(any());
    }

    @Test
    void testGetProfilePictureS3DataNull() throws Exception {
        // Arrange
        UserProfilePicture profilePic = new UserProfilePicture();
        profilePic.setUuid(TEST_UUID);
        profilePic.setS3Key(TEST_S3_KEY);
        profilePic.setBinMime("image/webp");

        when(userProfilePictureRepository.findByUuid(TEST_UUID)).thenReturn(profilePic);
        when(s3StorageService.downloadMedia(TEST_S3_KEY)).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getProfilePicture(TEST_UUID);

        // Assert
        assertNull(response);
        verify(s3StorageService).downloadMedia(TEST_S3_KEY);
    }

    @Test
    void testGetProfilePictureWithJpegMimeType() throws Exception {
        // Arrange
        UserProfilePicture profilePic = new UserProfilePicture();
        profilePic.setUuid(TEST_UUID);
        profilePic.setS3Key("profile/test-uuid.jpg");
        profilePic.setBinMime("image/jpeg");

        when(userProfilePictureRepository.findByUuid(TEST_UUID)).thenReturn(profilePic);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(TEST_IMAGE_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getProfilePicture(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
    }

    @Test
    void testGetProfilePictureWithPngMimeType() throws Exception {
        // Arrange
        UserProfilePicture profilePic = new UserProfilePicture();
        profilePic.setUuid(TEST_UUID);
        profilePic.setS3Key("profile/test-uuid.png");
        profilePic.setBinMime("image/png");

        when(userProfilePictureRepository.findByUuid(TEST_UUID)).thenReturn(profilePic);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(TEST_IMAGE_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getProfilePicture(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
    }

    // Verification Picture Tests

    @Test
    void testGetVerificationPictureFromRepository() throws Exception {
        // Arrange
        UserVerificationPicture verificationPicture = new UserVerificationPicture();
        verificationPicture.setUuid(TEST_UUID);
        verificationPicture.setS3Key(TEST_S3_KEY);
        verificationPicture.setBinMime("image/webp");

        when(userVerificationPictureRepository.findByUuid(TEST_UUID)).thenReturn(verificationPicture);
        when(s3StorageService.downloadMedia(TEST_S3_KEY)).thenReturn(TEST_IMAGE_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVerificationPicture(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(TEST_IMAGE_DATA, response.getBody());
        verify(userVerificationPictureRepository).findByUuid(TEST_UUID);
        verify(s3StorageService).downloadMedia(TEST_S3_KEY);
    }

    @Test
    void testGetVerificationPictureFromUser() throws Exception {
        // Arrange
        UserVerificationPicture verificationPicture = new UserVerificationPicture();
        verificationPicture.setS3Key(TEST_S3_KEY);
        verificationPicture.setBinMime("image/webp");

        User user = new User();
        user.setUuid(TEST_UUID);
        user.setVerificationPicture(verificationPicture);

        when(userVerificationPictureRepository.findByUuid(TEST_UUID)).thenReturn(null);
        when(userRepository.findByUuid(TEST_UUID)).thenReturn(user);
        when(s3StorageService.downloadMedia(TEST_S3_KEY)).thenReturn(TEST_IMAGE_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVerificationPicture(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findByUuid(TEST_UUID);
        verify(s3StorageService).downloadMedia(TEST_S3_KEY);
    }

    @Test
    void testGetVerificationPictureNotFound() throws Exception {
        // Arrange
        when(userVerificationPictureRepository.findByUuid(TEST_UUID)).thenReturn(null);
        when(userRepository.findByUuid(TEST_UUID)).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVerificationPicture(TEST_UUID);

        // Assert
        assertNull(response);
    }

    // Image Tests

    @Test
    void testGetImageSuccess() throws Exception {
        // Arrange
        UserImage image = new UserImage();
        image.setUuid(TEST_UUID);
        image.setS3Key("gallery/test-uuid.webp");
        image.setBinMime("image/webp");

        when(userImageRepository.findByUuid(TEST_UUID)).thenReturn(image);
        when(s3StorageService.downloadMedia("gallery/test-uuid.webp")).thenReturn(TEST_IMAGE_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getImage(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(TEST_IMAGE_DATA, response.getBody());
        verify(userImageRepository).findByUuid(TEST_UUID);
        verify(s3StorageService).downloadMedia("gallery/test-uuid.webp");
    }

    @Test
    void testGetImageNotFound() throws Exception {
        // Arrange
        when(userImageRepository.findByUuid(TEST_UUID)).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getImage(TEST_UUID);

        // Assert
        assertNull(response);
        verify(userImageRepository).findByUuid(TEST_UUID);
        verify(s3StorageService, never()).downloadMedia(any());
    }

    @Test
    void testGetImageS3DataNull() throws Exception {
        // Arrange
        UserImage image = new UserImage();
        image.setUuid(TEST_UUID);
        image.setS3Key("gallery/test-uuid.webp");
        image.setBinMime("image/webp");

        when(userImageRepository.findByUuid(TEST_UUID)).thenReturn(image);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getImage(TEST_UUID);

        // Assert
        assertNull(response);
    }

    // Audio Tests

    @Test
    void testGetAudioFromUserAudioRepository() throws Exception {
        // Arrange
        UserAudio userAudio = new UserAudio();
        userAudio.setUuid(TEST_UUID);
        userAudio.setS3Key("audio/test-uuid.mp3");
        userAudio.setBinMime("audio/mpeg");

        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(userAudio);
        when(s3StorageService.downloadMedia("audio/test-uuid.mp3")).thenReturn(TEST_AUDIO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(TEST_AUDIO_DATA, response.getBody());
        assertEquals(MediaType.parseMediaType("audio/mpeg"), response.getHeaders().getContentType());
        assertEquals(TEST_AUDIO_DATA.length, response.getHeaders().getContentLength());
        verify(userAudioRepository).findByUuid(TEST_UUID);
        verify(s3StorageService).downloadMedia("audio/test-uuid.mp3");
    }

    @Test
    void testGetAudioFromUser() throws Exception {
        // Arrange
        UserAudio userAudio = new UserAudio();
        userAudio.setS3Key("audio/test-uuid.wav");
        userAudio.setBinMime("audio/wav");

        User user = new User();
        user.setUuid(TEST_UUID);
        user.setAudio(userAudio);

        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(null);
        when(userRepository.findByUuid(TEST_UUID)).thenReturn(user);
        when(s3StorageService.downloadMedia("audio/test-uuid.wav")).thenReturn(TEST_AUDIO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("audio/wav"), response.getHeaders().getContentType());
        verify(userRepository).findByUuid(TEST_UUID);
    }

    @Test
    void testGetAudioWithDefaultMimeType() throws Exception {
        // Arrange
        UserAudio userAudio = new UserAudio();
        userAudio.setUuid(TEST_UUID);
        userAudio.setS3Key("audio/test-uuid.wav");
        userAudio.setBinMime(null);  // No mime type set

        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(userAudio);
        when(s3StorageService.downloadMedia("audio/test-uuid.wav")).thenReturn(TEST_AUDIO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(MediaType.parseMediaType("audio/wav"), response.getHeaders().getContentType());
    }

    @Test
    void testGetAudioNotFound() throws Exception {
        // Arrange
        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(null);
        when(userRepository.findByUuid(TEST_UUID)).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNull(response);
    }

    @Test
    void testGetAudioS3DataNull() throws Exception {
        // Arrange
        UserAudio userAudio = new UserAudio();
        userAudio.setUuid(TEST_UUID);
        userAudio.setS3Key("audio/test-uuid.mp3");
        userAudio.setBinMime("audio/mpeg");

        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(userAudio);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNull(response);
    }

    @Test
    void testGetAudioWithNullS3Key() throws Exception {
        // Arrange
        UserAudio userAudio = new UserAudio();
        userAudio.setUuid(TEST_UUID);
        userAudio.setS3Key(null);
        userAudio.setBinMime("audio/mpeg");

        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(userAudio);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNull(response);
        verify(s3StorageService, never()).downloadMedia(any());
    }

    // Video Introduction Tests

    @Test
    void testGetVideoIntroductionSuccess() throws Exception {
        // Arrange
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUuid(TEST_UUID);
        video.setS3Key("video/test-uuid.mp4");
        video.setMimeType("video/mp4");

        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.of(video));
        when(s3StorageService.downloadMedia("video/test-uuid.mp4")).thenReturn(TEST_VIDEO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(TEST_VIDEO_DATA, response.getBody());
        assertEquals(MediaType.parseMediaType("video/mp4"), response.getHeaders().getContentType());
        assertEquals(TEST_VIDEO_DATA.length, response.getHeaders().getContentLength());
        verify(userVideoIntroductionRepository).findByUuid(TEST_UUID);
        verify(s3StorageService).downloadMedia("video/test-uuid.mp4");
    }

    @Test
    void testGetVideoIntroductionWithDefaultMimeType() throws Exception {
        // Arrange
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUuid(TEST_UUID);
        video.setS3Key("video/test-uuid.mp4");
        video.setMimeType(null);  // No mime type set

        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.of(video));
        when(s3StorageService.downloadMedia("video/test-uuid.mp4")).thenReturn(TEST_VIDEO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(MediaType.parseMediaType("video/mp4"), response.getHeaders().getContentType());
    }

    @Test
    void testGetVideoIntroductionNotFound() throws Exception {
        // Arrange
        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNull(response);
        verify(userVideoIntroductionRepository).findByUuid(TEST_UUID);
        verify(s3StorageService, never()).downloadMedia(any());
    }

    @Test
    void testGetVideoIntroductionWithNullS3Key() throws Exception {
        // Arrange
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUuid(TEST_UUID);
        video.setS3Key(null);
        video.setMimeType("video/mp4");

        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.of(video));

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNull(response);
        verify(s3StorageService, never()).downloadMedia(any());
    }

    @Test
    void testGetVideoIntroductionS3DataNull() throws Exception {
        // Arrange
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUuid(TEST_UUID);
        video.setS3Key("video/test-uuid.mp4");
        video.setMimeType("video/mp4");

        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.of(video));
        when(s3StorageService.downloadMedia(anyString())).thenReturn(null);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNull(response);
    }

    @Test
    void testGetVideoIntroductionWhenRepositoryIsNull() throws Exception {
        // Arrange - This simulates when the repository bean is not available
        MediaService serviceWithNullRepo = new MediaService();
        // The repository is not injected (null)

        // Act
        ResponseEntity<byte[]> response = serviceWithNullRepo.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNull(response);
    }

    @Test
    void testGetVideoIntroductionWithWebMMimeType() throws Exception {
        // Arrange
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUuid(TEST_UUID);
        video.setS3Key("video/test-uuid.webm");
        video.setMimeType("video/webm");

        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.of(video));
        when(s3StorageService.downloadMedia("video/test-uuid.webm")).thenReturn(TEST_VIDEO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(MediaType.parseMediaType("video/webm"), response.getHeaders().getContentType());
    }

    // MIME Type Detection Tests

    @Test
    void testGetImageMimeTypeWebP() throws Exception {
        // Arrange
        String imageB64 = "data:image/webp;base64,xxxxx";

        // Act
        MediaType mimeType = mediaService.getImageMimeType(imageB64);

        // Assert
        assertEquals(new MediaType("image", "webp"), mimeType);
    }

    @Test
    void testGetImageMimeTypePng() throws Exception {
        // Arrange
        String imageB64 = "data:image/png;base64,xxxxx";

        // Act
        MediaType mimeType = mediaService.getImageMimeType(imageB64);

        // Assert
        assertEquals(MediaType.IMAGE_PNG, mimeType);
    }

    @Test
    void testGetImageMimeTypeDefaultsToJpeg() throws Exception {
        // Arrange
        String imageB64 = "data:image/unknown;base64,xxxxx";

        // Act
        MediaType mimeType = mediaService.getImageMimeType(imageB64);

        // Assert
        assertEquals(MediaType.IMAGE_JPEG, mimeType);
    }

    // Content Length Tests

    @Test
    void testContentLengthSetCorrectlyForImages() throws Exception {
        // Arrange
        UserImage image = new UserImage();
        image.setUuid(TEST_UUID);
        image.setS3Key("gallery/test.webp");
        image.setBinMime("image/webp");

        byte[] largeData = new byte[1024 * 1024]; // 1MB
        when(userImageRepository.findByUuid(TEST_UUID)).thenReturn(image);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(largeData);

        // Act
        ResponseEntity<byte[]> response = mediaService.getImage(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(largeData.length, response.getHeaders().getContentLength());
    }

    @Test
    void testContentLengthSetCorrectlyForAudio() throws Exception {
        // Arrange
        UserAudio audio = new UserAudio();
        audio.setUuid(TEST_UUID);
        audio.setS3Key("audio/test.mp3");
        audio.setBinMime("audio/mpeg");

        byte[] largeData = new byte[2 * 1024 * 1024]; // 2MB
        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(audio);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(largeData);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(largeData.length, response.getHeaders().getContentLength());
    }

    @Test
    void testContentLengthSetCorrectlyForVideo() throws Exception {
        // Arrange
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUuid(TEST_UUID);
        video.setS3Key("video/test.mp4");
        video.setMimeType("video/mp4");

        byte[] largeData = new byte[5 * 1024 * 1024]; // 5MB
        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.of(video));
        when(s3StorageService.downloadMedia(anyString())).thenReturn(largeData);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(largeData.length, response.getHeaders().getContentLength());
    }

    // Edge Cases

    @Test
    void testGetAudioWithComplexMimeType() throws Exception {
        // Arrange
        UserAudio audio = new UserAudio();
        audio.setUuid(TEST_UUID);
        audio.setS3Key("audio/test.mp3");
        audio.setBinMime("audio/mpeg;charset=utf-8");

        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(audio);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(TEST_AUDIO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNotNull(response);
        // Should parse the first part before semicolon
        assertNotNull(response.getHeaders().getContentType());
    }

    @Test
    void testGetAudioWithInvalidMimeType() throws Exception {
        // Arrange
        UserAudio audio = new UserAudio();
        audio.setUuid(TEST_UUID);
        audio.setS3Key("audio/test.mp3");
        audio.setBinMime("invalid");

        when(userAudioRepository.findByUuid(TEST_UUID)).thenReturn(audio);
        when(s3StorageService.downloadMedia(anyString())).thenReturn(TEST_AUDIO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getAudio(TEST_UUID);

        // Assert
        assertNotNull(response);
        // Should handle gracefully
    }

    @Test
    void testGetVideoIntroductionWithQuickTimeMimeType() throws Exception {
        // Arrange
        UserVideoIntroduction video = new UserVideoIntroduction();
        video.setUuid(TEST_UUID);
        video.setS3Key("video/test.mov");
        video.setMimeType("video/quicktime");

        when(userVideoIntroductionRepository.findByUuid(TEST_UUID)).thenReturn(Optional.of(video));
        when(s3StorageService.downloadMedia(anyString())).thenReturn(TEST_VIDEO_DATA);

        // Act
        ResponseEntity<byte[]> response = mediaService.getVideoIntroduction(TEST_UUID);

        // Assert
        assertNotNull(response);
        assertEquals(MediaType.parseMediaType("video/quicktime"), response.getHeaders().getContentType());
    }
}
