package com.nonononoki.alovoa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class S3StorageServiceTest {

    @Autowired
    private S3StorageService s3StorageService;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

    private static final String TEST_BUCKET_MEDIA = "test-media-bucket";
    private static final String TEST_BUCKET_VIDEO = "test-video-bucket";
    private static final byte[] TEST_DATA = "test image data".getBytes();
    private static final String TEST_MIME_TYPE = "image/webp";

    @BeforeEach
    void setUp() {
        // Enable S3 and inject mocks
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", true);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", s3Client);
        ReflectionTestUtils.setField(s3StorageService, "presigner", s3Presigner);
        ReflectionTestUtils.setField(s3StorageService, "mediaBucket", TEST_BUCKET_MEDIA);
        ReflectionTestUtils.setField(s3StorageService, "videoBucket", TEST_BUCKET_VIDEO);
    }

    @Test
    void testUploadMediaProfileImage() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String s3Key = s3StorageService.uploadMedia(TEST_DATA, TEST_MIME_TYPE, S3StorageService.S3MediaType.PROFILE);

        // Assert
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("profile/"));
        assertTrue(s3Key.endsWith(".webp"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TEST_BUCKET_MEDIA, capturedRequest.bucket());
        assertEquals(TEST_MIME_TYPE, capturedRequest.contentType());
    }

    @Test
    void testUploadMediaGalleryImage() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String s3Key = s3StorageService.uploadMedia(TEST_DATA, "image/jpeg", S3StorageService.S3MediaType.GALLERY);

        // Assert
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("gallery/"));
        assertTrue(s3Key.endsWith(".jpg"));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadMediaAudio() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String s3Key = s3StorageService.uploadMedia(TEST_DATA, "audio/mp3", S3StorageService.S3MediaType.AUDIO);

        // Assert
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("audio/"));
        assertTrue(s3Key.endsWith(".mp3"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals(TEST_BUCKET_MEDIA, requestCaptor.getValue().bucket());
    }

    @Test
    void testUploadMediaVideo() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String s3Key = s3StorageService.uploadMedia(TEST_DATA, "video/mp4", S3StorageService.S3MediaType.VIDEO);

        // Assert
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("video/"));
        assertTrue(s3Key.endsWith(".mp4"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals(TEST_BUCKET_VIDEO, requestCaptor.getValue().bucket());
    }

    @Test
    void testUploadMediaVerificationPicture() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String s3Key = s3StorageService.uploadMedia(TEST_DATA, "image/png", S3StorageService.S3MediaType.VERIFICATION);

        // Assert
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("verification/"));
        assertTrue(s3Key.endsWith(".png"));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadMediaWithUnknownMimeType() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String s3Key = s3StorageService.uploadMedia(TEST_DATA, "application/octet-stream", S3StorageService.S3MediaType.PROFILE);

        // Assert
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("profile/"));
        assertFalse(s3Key.contains("."));  // No extension for unknown type
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadMediaWhenS3Disabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", false);

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                s3StorageService.uploadMedia(TEST_DATA, TEST_MIME_TYPE, S3StorageService.S3MediaType.PROFILE)
        );
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadMediaFailure() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Upload failed").build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                s3StorageService.uploadMedia(TEST_DATA, TEST_MIME_TYPE, S3StorageService.S3MediaType.PROFILE)
        );
    }

    @Test
    void testDownloadMediaFromMediaBucket() throws Exception {
        // Arrange
        String s3Key = "profile/test-uuid.webp";
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(TEST_DATA);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        // Act
        byte[] result = s3StorageService.downloadMedia(s3Key);

        // Assert
        assertNotNull(result);
        assertArrayEquals(TEST_DATA, result);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        assertEquals(TEST_BUCKET_MEDIA, requestCaptor.getValue().bucket());
        assertEquals(s3Key, requestCaptor.getValue().key());
    }

    @Test
    void testDownloadMediaFromVideoBucket() throws Exception {
        // Arrange
        String s3Key = "video/test-uuid.mp4";
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(TEST_DATA);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        // Act
        byte[] result = s3StorageService.downloadMedia(s3Key);

        // Assert
        assertNotNull(result);
        assertArrayEquals(TEST_DATA, result);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        assertEquals(TEST_BUCKET_VIDEO, requestCaptor.getValue().bucket());
    }

    @Test
    void testDownloadMediaNotFound() throws Exception {
        // Arrange
        String s3Key = "profile/nonexistent.webp";
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        // Act
        byte[] result = s3StorageService.downloadMedia(s3Key);

        // Assert
        assertNull(result);
    }

    @Test
    void testDownloadMediaWithNullKey() throws Exception {
        // Act
        byte[] result = s3StorageService.downloadMedia(null);

        // Assert
        assertNull(result);
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void testDownloadMediaWithEmptyKey() throws Exception {
        // Act
        byte[] result = s3StorageService.downloadMedia("");

        // Assert
        assertNull(result);
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void testDownloadMediaWhenS3Disabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", false);

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                s3StorageService.downloadMedia("profile/test.webp")
        );
    }

    @Test
    void testDownloadMediaConnectionError() throws Exception {
        // Arrange
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Connection error").build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                s3StorageService.downloadMedia("profile/test.webp")
        );
    }

    @Test
    void testDeleteMediaFromMediaBucket() throws Exception {
        // Arrange
        String s3Key = "profile/test-uuid.webp";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // Act
        s3StorageService.deleteMedia(s3Key);

        // Assert
        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertEquals(TEST_BUCKET_MEDIA, requestCaptor.getValue().bucket());
        assertEquals(s3Key, requestCaptor.getValue().key());
    }

    @Test
    void testDeleteMediaFromVideoBucket() throws Exception {
        // Arrange
        String s3Key = "video/test-uuid.mp4";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // Act
        s3StorageService.deleteMedia(s3Key);

        // Assert
        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertEquals(TEST_BUCKET_VIDEO, requestCaptor.getValue().bucket());
    }

    @Test
    void testDeleteMediaWithNullKey() throws Exception {
        // Act
        s3StorageService.deleteMedia(null);

        // Assert
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void testDeleteMediaWithEmptyKey() throws Exception {
        // Act
        s3StorageService.deleteMedia("");

        // Assert
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void testDeleteMediaWhenS3Disabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", false);

        // Act - should not throw exception when disabled
        s3StorageService.deleteMedia("profile/test.webp");

        // Assert
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void testDeleteMediaFailure() throws Exception {
        // Arrange
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Delete failed").build());

        // Act - should not throw exception, just log error
        assertDoesNotThrow(() -> s3StorageService.deleteMedia("profile/test.webp"));
    }

    @Test
    void testGetPresignedUrlForMediaBucket() throws Exception {
        // Arrange
        String s3Key = "profile/test-uuid.webp";
        String expectedUrl = "https://s3.amazonaws.com/test-media-bucket/profile/test-uuid.webp?signature=xyz";
        Duration expiry = Duration.ofHours(1);

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        // Act
        String result = s3StorageService.getPresignedUrl(s3Key, expiry);

        // Assert
        assertNotNull(result);
        assertEquals(expectedUrl, result);

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());
        assertEquals(expiry, requestCaptor.getValue().signatureDuration());
    }

    @Test
    void testGetPresignedUrlForVideoBucket() throws Exception {
        // Arrange
        String s3Key = "video/test-uuid.mp4";
        String expectedUrl = "https://s3.amazonaws.com/test-video-bucket/video/test-uuid.mp4?signature=xyz";
        Duration expiry = Duration.ofMinutes(30);

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        // Act
        String result = s3StorageService.getPresignedUrl(s3Key, expiry);

        // Assert
        assertNotNull(result);
        assertEquals(expectedUrl, result);
    }

    @Test
    void testGetPresignedUrlWithNullKey() throws Exception {
        // Act
        String result = s3StorageService.getPresignedUrl(null, Duration.ofHours(1));

        // Assert
        assertNull(result);
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void testGetPresignedUrlWithEmptyKey() throws Exception {
        // Act
        String result = s3StorageService.getPresignedUrl("", Duration.ofHours(1));

        // Assert
        assertNull(result);
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void testGetPresignedUrlWhenS3Disabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", false);

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                s3StorageService.getPresignedUrl("profile/test.webp", Duration.ofHours(1))
        );
    }

    @Test
    void testGetPresignedUrlFailure() throws Exception {
        // Arrange
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(S3Exception.builder().message("Presign failed").build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                s3StorageService.getPresignedUrl("profile/test.webp", Duration.ofHours(1))
        );
    }

    @Test
    void testIsEnabledWhenEnabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", true);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", s3Client);

        // Act
        boolean result = s3StorageService.isEnabled();

        // Assert
        assertTrue(result);
    }

    @Test
    void testIsEnabledWhenDisabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", false);

        // Act
        boolean result = s3StorageService.isEnabled();

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsEnabledWhenClientIsNull() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(s3StorageService, "s3Enabled", true);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", null);

        // Act
        boolean result = s3StorageService.isEnabled();

        // Assert
        assertFalse(result);
    }

    @Test
    void testUploadMediaWithDifferentImageFormats() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Test PNG
        String pngKey = s3StorageService.uploadMedia(TEST_DATA, "image/png", S3StorageService.S3MediaType.PROFILE);
        assertTrue(pngKey.endsWith(".png"));

        // Test JPEG
        String jpegKey = s3StorageService.uploadMedia(TEST_DATA, "image/jpeg", S3StorageService.S3MediaType.PROFILE);
        assertTrue(jpegKey.endsWith(".jpg"));

        // Test GIF
        String gifKey = s3StorageService.uploadMedia(TEST_DATA, "image/gif", S3StorageService.S3MediaType.PROFILE);
        assertTrue(gifKey.endsWith(".gif"));

        // Test WebP
        String webpKey = s3StorageService.uploadMedia(TEST_DATA, "image/webp", S3StorageService.S3MediaType.PROFILE);
        assertTrue(webpKey.endsWith(".webp"));

        verify(s3Client, times(4)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadMediaWithDifferentAudioFormats() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Test MP3
        String mp3Key = s3StorageService.uploadMedia(TEST_DATA, "audio/mpeg", S3StorageService.S3MediaType.AUDIO);
        assertTrue(mp3Key.endsWith(".mp3"));

        // Test WAV
        String wavKey = s3StorageService.uploadMedia(TEST_DATA, "audio/wav", S3StorageService.S3MediaType.AUDIO);
        assertTrue(wavKey.endsWith(".wav"));

        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadMediaWithDifferentVideoFormats() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Test MP4
        String mp4Key = s3StorageService.uploadMedia(TEST_DATA, "video/mp4", S3StorageService.S3MediaType.VIDEO);
        assertTrue(mp4Key.endsWith(".mp4"));

        // Test WebM
        String webmKey = s3StorageService.uploadMedia(TEST_DATA, "video/webm", S3StorageService.S3MediaType.VIDEO);
        assertTrue(webmKey.endsWith(".webm"));

        // Test QuickTime
        String movKey = s3StorageService.uploadMedia(TEST_DATA, "video/quicktime", S3StorageService.S3MediaType.VIDEO);
        assertTrue(movKey.endsWith(".mov"));

        verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
