package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.model.VideoAnalysisResult;
import com.nonononoki.alovoa.repo.UserVideoIntroductionRepository;
import com.nonononoki.alovoa.service.ai.AiAnalysisProvider;
import com.nonononoki.alovoa.service.ai.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Service for async video analysis using AI providers.
 */
@Service
public class VideoAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoAnalysisService.class);

    @Autowired
    private AiAnalysisProvider aiProvider;

    @Autowired
    private UserVideoIntroductionRepository videoIntroRepo;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Analyze a video introduction asynchronously.
     * This method runs in a separate thread to avoid blocking the upload request.
     */
    @Async
    @Transactional
    public void analyzeVideoAsync(UserVideoIntroduction video) {
        LOGGER.info("Starting async video analysis for user {}", video.getUser().getId());

        try {
            // Update status to transcribing
            video.setStatus(UserVideoIntroduction.AnalysisStatus.TRANSCRIBING);
            videoIntroRepo.save(video);

            // Step 1: Transcribe the video
            String transcript = aiProvider.transcribeVideo(video.getVideoData(), video.getMimeType());
            video.setTranscript(transcript);

            // Update status to analyzing
            video.setStatus(UserVideoIntroduction.AnalysisStatus.ANALYZING);
            videoIntroRepo.save(video);

            // Step 2: Analyze the transcript
            VideoAnalysisResult result = aiProvider.analyzeTranscript(transcript);

            // Update video with analysis results
            video.setWorldviewSummary(result.getWorldviewSummary());
            video.setBackgroundSummary(result.getBackgroundSummary());
            video.setLifeStorySummary(result.getLifeStorySummary());

            if (result.getPersonalityIndicators() != null) {
                video.setPersonalityIndicators(objectMapper.writeValueAsString(result.getPersonalityIndicators()));
            }

            video.setAiProvider(aiProvider.getProviderName());
            video.setAnalyzedAt(new Date());
            video.setStatus(UserVideoIntroduction.AnalysisStatus.COMPLETED);

            videoIntroRepo.save(video);

            LOGGER.info("Video analysis completed for user {}", video.getUser().getId());

        } catch (AiProviderException e) {
            LOGGER.error("AI provider error during video analysis for user {}: {}",
                    video.getUser().getId(), e.getMessage(), e);
            markAsFailed(video);

        } catch (Exception e) {
            LOGGER.error("Unexpected error during video analysis for user {}: {}",
                    video.getUser().getId(), e.getMessage(), e);
            markAsFailed(video);
        }
    }

    /**
     * Retry analysis for a failed video.
     */
    @Async
    @Transactional
    public void retryAnalysis(Long videoId) {
        videoIntroRepo.findById(videoId).ifPresent(video -> {
            if (video.getStatus() == UserVideoIntroduction.AnalysisStatus.FAILED) {
                video.setStatus(UserVideoIntroduction.AnalysisStatus.PENDING);
                videoIntroRepo.save(video);
                analyzeVideoAsync(video);
            }
        });
    }

    /**
     * Get analysis status for a video.
     */
    @Transactional(readOnly = true)
    public UserVideoIntroduction.AnalysisStatus getAnalysisStatus(Long videoId) {
        return videoIntroRepo.findById(videoId)
                .map(UserVideoIntroduction::getStatus)
                .orElse(null);
    }

    /**
     * Check if the AI provider is available.
     */
    public boolean isProviderAvailable() {
        return aiProvider.isAvailable();
    }

    /**
     * Get the current AI provider name.
     */
    public String getProviderName() {
        return aiProvider.getProviderName();
    }

    private void markAsFailed(UserVideoIntroduction video) {
        video.setStatus(UserVideoIntroduction.AnalysisStatus.FAILED);
        videoIntroRepo.save(video);
    }
}
