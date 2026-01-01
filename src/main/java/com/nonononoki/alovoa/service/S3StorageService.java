package com.nonononoki.alovoa.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3StorageService.class);

    @Value("${app.storage.s3.enabled:false}")
    private boolean s3Enabled;

    @Value("${app.storage.s3.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${app.storage.s3.region:us-east-1}")
    private String region;

    @Value("${app.storage.s3.access-key:}")
    private String accessKey;

    @Value("${app.storage.s3.secret-key:}")
    private String secretKey;

    @Value("${app.storage.s3.bucket.media:aura-media}")
    private String mediaBucket;

    @Value("${app.storage.s3.bucket.video:aura-video}")
    private String videoBucket;

    private S3Client s3Client;
    private S3Presigner presigner;

    /**
     * Media type prefixes for organizing objects in S3
     */
    public enum S3MediaType {
        PROFILE("profile/"),
        GALLERY("gallery/"),
        AUDIO("audio/"),
        VIDEO("video/"),
        VERIFICATION("verification/");

        private final String prefix;

        S3MediaType(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    @PostConstruct
    public void init() {
        if (!s3Enabled) {
            LOGGER.info("S3 storage is disabled");
            return;
        }

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

            s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .forcePathStyle(true) // Required for MinIO
                    .build();

            presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();

            // Create buckets if they don't exist
            createBucketIfNotExists(mediaBucket);
            createBucketIfNotExists(videoBucket);

            LOGGER.info("S3 storage initialized successfully with endpoint: {}", endpoint);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize S3 storage", e);
        }
    }

    private void createBucketIfNotExists(String bucketName) {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            LOGGER.debug("Bucket {} already exists", bucketName);
        } catch (NoSuchBucketException e) {
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.createBucket(createBucketRequest);
            LOGGER.info("Created bucket: {}", bucketName);
        }
    }

    /**
     * Upload media to S3
     *
     * @param data     Binary data to upload
     * @param mimeType MIME type of the content
     * @param type     Type of media (determines bucket and prefix)
     * @return S3 key for the uploaded object
     */
    public String uploadMedia(byte[] data, String mimeType, S3MediaType type) {
        if (!s3Enabled || s3Client == null) {
            throw new IllegalStateException("S3 storage is not enabled or not initialized");
        }

        String bucket = type == S3MediaType.VIDEO ? videoBucket : mediaBucket;
        String extension = getExtensionFromMimeType(mimeType);
        String key = type.getPrefix() + UUID.randomUUID() + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(mimeType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
            LOGGER.debug("Uploaded {} bytes to s3://{}/{}", data.length, bucket, key);

            return key;
        } catch (Exception e) {
            LOGGER.error("Failed to upload to S3: {}", e.getMessage());
            throw new RuntimeException("Failed to upload media to S3", e);
        }
    }

    /**
     * Download media from S3
     *
     * @param s3Key S3 key (includes prefix, e.g., "profile/uuid.webp")
     * @return Binary data
     */
    public byte[] downloadMedia(String s3Key) {
        if (!s3Enabled || s3Client == null) {
            throw new IllegalStateException("S3 storage is not enabled or not initialized");
        }

        if (s3Key == null || s3Key.isEmpty()) {
            return null;
        }

        String bucket = s3Key.startsWith("video/") ? videoBucket : mediaBucket;

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();

            return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
        } catch (NoSuchKeyException e) {
            LOGGER.warn("S3 object not found: s3://{}/{}", bucket, s3Key);
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to download from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to download media from S3", e);
        }
    }

    /**
     * Delete media from S3
     *
     * @param s3Key S3 key to delete
     */
    public void deleteMedia(String s3Key) {
        if (!s3Enabled || s3Client == null) {
            return;
        }

        if (s3Key == null || s3Key.isEmpty()) {
            return;
        }

        String bucket = s3Key.startsWith("video/") ? videoBucket : mediaBucket;

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            LOGGER.debug("Deleted s3://{}/{}", bucket, s3Key);
        } catch (Exception e) {
            LOGGER.error("Failed to delete from S3: {}", e.getMessage());
        }
    }

    /**
     * Generate a presigned URL for temporary access to an object
     *
     * @param s3Key  S3 key
     * @param expiry Duration before URL expires
     * @return Presigned URL
     */
    public String getPresignedUrl(String s3Key, Duration expiry) {
        if (!s3Enabled || presigner == null) {
            throw new IllegalStateException("S3 storage is not enabled or not initialized");
        }

        if (s3Key == null || s3Key.isEmpty()) {
            return null;
        }

        String bucket = s3Key.startsWith("video/") ? videoBucket : mediaBucket;

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            LOGGER.error("Failed to generate presigned URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    /**
     * Check if S3 storage is enabled and available
     */
    public boolean isEnabled() {
        return s3Enabled && s3Client != null;
    }

    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }

        return switch (mimeType.toLowerCase()) {
            case "image/webp" -> ".webp";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "audio/wav", "audio/wave" -> ".wav";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            default -> "";
        };
    }
}
