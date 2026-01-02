package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.Tools;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAudio;
import com.nonononoki.alovoa.entity.user.UserImage;
import com.nonononoki.alovoa.entity.user.UserProfilePicture;
import com.nonononoki.alovoa.entity.user.UserVerificationPicture;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.UUID;

@Service
public class MediaService {

    private static final String IMAGE_DATA_START = "data:";
    public static final String MEDIA_TYPE_IMAGE_WEBP = "image/webp";
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserImageRepository userImageRepository;

    @Autowired
    private UserProfilePictureRepository userProfilePictureRepository;

    @Autowired
    private UserAudioRepository userAudioRepository;

    @Autowired
    private UserVerificationPictureRepository userVerificationPictureRepository;

    @Autowired
    private S3StorageService s3StorageService;

    @Autowired(required = false)
    private UserVideoIntroductionRepository userVideoIntroductionRepository;

    public ResponseEntity<byte[]> getProfilePicture(UUID uuid) {
        UserProfilePicture profilePic = userProfilePictureRepository.findByUuid(uuid);
        if (profilePic == null) {
            return null;
        }
        byte[] data = s3StorageService.downloadMedia(profilePic.getS3Key());
        if (data == null) {
            return null;
        }
        return getImageDataBase(data, profilePic.getBinMime());
    }

    public ResponseEntity<byte[]> getVerificationPicture(UUID uuid) {
        UserVerificationPicture verificationPicture = userVerificationPictureRepository.findByUuid(uuid);
        if (verificationPicture != null) {
            byte[] data = s3StorageService.downloadMedia(verificationPicture.getS3Key());
            if (data != null) {
                return getImageDataBase(data, verificationPicture.getBinMime());
            }
        }
        User user = userRepository.findByUuid(uuid);
        if (user != null && user.getVerificationPicture() != null) {
            byte[] data = s3StorageService.downloadMedia(user.getVerificationPicture().getS3Key());
            if (data != null) {
                return getImageDataBase(data, user.getVerificationPicture().getBinMime());
            }
        }
        return null;
    }

    public ResponseEntity<byte[]> getAudio(UUID uuid) {
        UserAudio userAudio = userAudioRepository.findByUuid(uuid);
        byte[] bytes = null;
        String mimeType = "audio/wav";

        if (userAudio != null && userAudio.getS3Key() != null) {
            bytes = s3StorageService.downloadMedia(userAudio.getS3Key());
            if (userAudio.getBinMime() != null) {
                mimeType = userAudio.getBinMime();
            }
        }
        if (bytes == null) {
            User user = userRepository.findByUuid(uuid);
            if (user != null && user.getAudio() != null && user.getAudio().getS3Key() != null) {
                bytes = s3StorageService.downloadMedia(user.getAudio().getS3Key());
                if (user.getAudio().getBinMime() != null) {
                    mimeType = user.getAudio().getBinMime();
                }
            }
        }
        if (bytes == null) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        String[] parts = mimeType.split("/");
        String subtype = parts.length > 1 ? parts[1] : "wav";
        // Strip any parameters like ;charset=utf-8
        if (subtype.contains(";")) {
            subtype = subtype.split(";")[0];
        }
        headers.setContentType(new MediaType(parts[0], subtype));
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    public ResponseEntity<byte[]> getImage(UUID uuid) {
        UserImage image = userImageRepository.findByUuid(uuid);
        if (image == null) {
            return null;
        }
        byte[] data = s3StorageService.downloadMedia(image.getS3Key());
        if (data == null) {
            return null;
        }
        return getImageDataBase(data, image.getBinMime());
    }

    /**
     * Get video introduction for a user
     */
    public ResponseEntity<byte[]> getVideoIntroduction(UUID uuid) {
        if (userVideoIntroductionRepository == null) {
            return null;
        }
        UserVideoIntroduction video = userVideoIntroductionRepository.findByUuid(uuid).orElse(null);
        if (video == null || video.getS3Key() == null) {
            return null;
        }
        byte[] data = s3StorageService.downloadMedia(video.getS3Key());
        if (data == null) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        String mimeType = video.getMimeType() != null ? video.getMimeType() : "video/mp4";
        String[] parts = mimeType.split("/");
        headers.setContentType(new MediaType(parts[0], parts.length > 1 ? parts[1] : "mp4"));
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    private byte[] getBase64Data(String base64) {
        String data = base64;
        if (data.contains(",")) {
            data = data.split(",", 2)[1];
        }
        return Base64.getDecoder().decode(data);
    }

    private ResponseEntity<byte[]> getImageDataBase(String imageB64) {
        byte[] bytes = getBase64Data(imageB64);
        MediaType mimeType = getImageMimeType(imageB64);
        return getImageDataBase(bytes, Tools.buildMimeTypeString(mimeType));
    }

    private ResponseEntity<byte[]> getImageDataBase(byte[] bytes, String mimeType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(getImageMimeType(IMAGE_DATA_START + mimeType));
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    public MediaType getImageMimeType(String imageB64) {
        MediaType mediaType;
        if (imageB64.startsWith(IMAGE_DATA_START + MEDIA_TYPE_IMAGE_WEBP)) {
            mediaType = new MediaType("image", "webp");
        } else if (imageB64.startsWith(IMAGE_DATA_START + MediaType.IMAGE_PNG)) {
            mediaType = MediaType.IMAGE_PNG;
        } else {
            mediaType = MediaType.IMAGE_JPEG;
        }
        return mediaType;
    }
}
