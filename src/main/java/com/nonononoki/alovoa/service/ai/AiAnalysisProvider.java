package com.nonononoki.alovoa.service.ai;

import com.nonononoki.alovoa.model.VideoAnalysisResult;

/**
 * Interface for AI providers that can analyze video introductions.
 * Implementations can use OpenAI, Claude, or local services.
 */
public interface AiAnalysisProvider {

    /**
     * Transcribe video audio to text using speech-to-text.
     *
     * @param videoData The video file bytes
     * @param mimeType  The MIME type of the video (e.g., "video/mp4")
     * @return The transcribed text
     * @throws AiProviderException if transcription fails
     */
    String transcribeVideo(byte[] videoData, String mimeType) throws AiProviderException;

    /**
     * Analyze a transcript to extract user information.
     *
     * @param transcript The speech transcript to analyze
     * @return Analysis result with worldview, background, life story, etc.
     * @throws AiProviderException if analysis fails
     */
    VideoAnalysisResult analyzeTranscript(String transcript) throws AiProviderException;

    /**
     * Full pipeline: transcribe video and analyze the transcript.
     *
     * @param videoData The video file bytes
     * @param mimeType  The MIME type of the video
     * @return Complete analysis result
     * @throws AiProviderException if any step fails
     */
    default VideoAnalysisResult analyzeVideo(byte[] videoData, String mimeType) throws AiProviderException {
        String transcript = transcribeVideo(videoData, mimeType);
        VideoAnalysisResult result = analyzeTranscript(transcript);
        result.setTranscript(transcript);
        result.setProviderName(getProviderName());
        return result;
    }

    /**
     * Get the name of this provider for logging/tracking.
     *
     * @return Provider name (e.g., "openai", "claude", "local")
     */
    String getProviderName();

    /**
     * Check if this provider is properly configured and available.
     *
     * @return true if the provider can accept requests
     */
    boolean isAvailable();
}
