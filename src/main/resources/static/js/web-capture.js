/**
 * Web-based video/screen capture (Tier A)
 *
 * Tier A: Record in-browser → upload once on stop (presigned PUT)
 * Suitable for short recordings up to 2-3 minutes at 720p.
 *
 * Key design points:
 * - WebM only (vp9/vp8 + opus) - MP4 transcoding happens server-side
 * - Proper audio mixing via WebAudio API (mic + system audio if available)
 * - Bitrate caps to prevent file bloat
 * - Single blob upload with progress + retry on stop
 * - Tab close warning during active recording
 */

(function() {
    'use strict';

    // ============================================================
    // Configuration
    // ============================================================

    const CONFIG = {
        // Target 2.5 Mbps for 720p - keeps 2 min recording under 40MB
        videoBitsPerSecond: 2500000,
        audioBitsPerSecond: 128000,

        // Audio-only bitrate (64kbps = ~1MB per 2 min)
        audioOnlyBitsPerSecond: 64000,

        // Recording limits
        maxDurationSeconds: 180,  // 3 minutes max
        warningAtSeconds: 150,    // Warn at 2.5 min

        // Audio-only can be longer (smaller files)
        audioMaxDurationSeconds: 600,  // 10 minutes
        audioWarningAtSeconds: 540,    // 9 min

        // Upload
        maxRetries: 3,
        retryDelayMs: 2000,

        // Video MIME type priority (WebM only - transcode to MP4 server-side)
        videoMimeTypes: [
            'video/webm;codecs=vp9,opus',
            'video/webm;codecs=vp8,opus',
            'video/webm'
        ],

        // Audio MIME type priority
        audioMimeTypes: [
            'audio/webm;codecs=opus',  // Best on Chrome/Firefox
            'audio/ogg;codecs=opus',   // Firefox fallback
            'audio/webm',
            'audio/ogg'
        ]
    };

    // ============================================================
    // State
    // ============================================================

    let state = {
        // Capture session
        captureId: null,
        s3Key: null,
        putUrl: null,
        maxSizeBytes: 0,

        // Streams and recorder
        screenStream: null,
        micStream: null,
        combinedStream: null,
        audioContext: null,
        mediaRecorder: null,
        recordedChunks: [],

        // Recording state
        isRecording: false,
        isPaused: false,
        isAudioOnly: false,
        startTime: null,
        durationMs: 0,
        durationTimer: null,

        // Callbacks
        onStatusChange: null,
        onProgress: null,
        onError: null,
        onComplete: null
    };

    // ============================================================
    // MIME Type Detection
    // ============================================================

    function getSupportedVideoMimeType() {
        for (const mimeType of CONFIG.videoMimeTypes) {
            if (MediaRecorder.isTypeSupported(mimeType)) {
                console.log('[Capture] Using video MIME type:', mimeType);
                return mimeType;
            }
        }
        // Fallback - let browser choose
        console.warn('[Capture] No preferred video MIME type supported, using default');
        return '';
    }

    function getSupportedAudioMimeType() {
        for (const mimeType of CONFIG.audioMimeTypes) {
            if (MediaRecorder.isTypeSupported(mimeType)) {
                console.log('[Capture] Using audio MIME type:', mimeType);
                return mimeType;
            }
        }
        // Fallback - let browser choose
        console.warn('[Capture] No preferred audio MIME type supported, using default');
        return '';
    }

    function getSupportedMimeType() {
        return state.isAudioOnly ? getSupportedAudioMimeType() : getSupportedVideoMimeType();
    }

    // ============================================================
    // Stream Building with WebAudio Mixing
    // ============================================================

    /**
     * Build audio-only stream (microphone only).
     * Simplest and most reliable capture mode.
     *
     * @returns {MediaStream} Audio stream ready for recording
     */
    async function buildAudioOnlyStream() {
        try {
            state.micStream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true
                },
                video: false
            });

            state.combinedStream = state.micStream;
            console.log('[Capture] Audio-only stream ready');
            return state.combinedStream;
        } catch (err) {
            if (err.name === 'NotAllowedError') {
                throw new Error('Microphone permission denied');
            }
            throw err;
        }
    }

    /**
     * Build capture stream with proper audio mixing.
     * Combines screen video + mixed audio (mic + system if available).
     *
     * @param {Object} options - { includeScreen, includeMic, includeSystemAudio, includeWebcam }
     * @returns {MediaStream} Combined stream ready for recording
     */
    async function buildCaptureStream(options = {}) {
        const {
            includeScreen = true,
            includeMic = true,
            includeSystemAudio = true,
            includeWebcam = false
        } = options;

        const tracks = [];

        // Get screen/window capture
        if (includeScreen) {
            try {
                state.screenStream = await navigator.mediaDevices.getDisplayMedia({
                    video: {
                        frameRate: 30,
                        cursor: 'always',
                        displaySurface: 'monitor'  // or 'window', 'browser'
                    },
                    audio: includeSystemAudio  // System audio (Chrome only)
                });

                // Add video track
                const videoTrack = state.screenStream.getVideoTracks()[0];
                if (videoTrack) {
                    tracks.push(videoTrack);

                    // Handle user stopping screen share via browser UI
                    videoTrack.onended = () => {
                        console.log('[Capture] Screen share ended by user');
                        if (state.isRecording) {
                            stopRecording();
                        }
                    };
                }
            } catch (err) {
                if (err.name === 'NotAllowedError') {
                    throw new Error('Screen sharing permission denied');
                }
                throw err;
            }
        }

        // Get webcam if requested (for picture-in-picture overlay)
        let webcamStream = null;
        if (includeWebcam) {
            try {
                webcamStream = await navigator.mediaDevices.getUserMedia({
                    video: { width: 320, height: 240, facingMode: 'user' }
                });
                // Note: Webcam video would need canvas compositing for PiP
                // For Tier A, we'll skip this complexity
            } catch (err) {
                console.warn('[Capture] Webcam not available:', err.message);
            }
        }

        // Build mixed audio track using WebAudio API
        if (includeMic || includeSystemAudio) {
            try {
                state.audioContext = new AudioContext();
                const destination = state.audioContext.createMediaStreamDestination();

                // Add system audio if available (from screen capture)
                if (includeSystemAudio && state.screenStream) {
                    const systemAudioTracks = state.screenStream.getAudioTracks();
                    if (systemAudioTracks.length > 0) {
                        const systemSource = state.audioContext.createMediaStreamSource(
                            new MediaStream([systemAudioTracks[0]])
                        );
                        systemSource.connect(destination);
                        console.log('[Capture] Added system audio to mix');
                    }
                }

                // Add microphone
                if (includeMic) {
                    try {
                        state.micStream = await navigator.mediaDevices.getUserMedia({
                            audio: {
                                echoCancellation: true,
                                noiseSuppression: true,
                                autoGainControl: true
                            }
                        });
                        const micSource = state.audioContext.createMediaStreamSource(state.micStream);
                        micSource.connect(destination);
                        console.log('[Capture] Added microphone to mix');
                    } catch (err) {
                        console.warn('[Capture] Microphone not available:', err.message);
                    }
                }

                // Get mixed audio track
                const mixedAudioTracks = destination.stream.getAudioTracks();
                if (mixedAudioTracks.length > 0) {
                    tracks.push(mixedAudioTracks[0]);
                }
            } catch (err) {
                console.error('[Capture] Audio mixing failed:', err);
            }
        }

        if (tracks.length === 0) {
            throw new Error('No media tracks available for capture');
        }

        state.combinedStream = new MediaStream(tracks);
        return state.combinedStream;
    }

    // ============================================================
    // Session Management
    // ============================================================

    /**
     * Create a capture session with the backend.
     * Gets presigned upload URL.
     */
    async function createSession(captureType = 'SCREEN_MIC') {
        const response = await fetch('/api/v1/capture/sessions?type=' + captureType, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.error || 'Failed to create capture session');
        }

        const data = await response.json();
        state.captureId = data.captureId;
        state.s3Key = data.s3Key;
        state.putUrl = data.putUrl;
        state.maxSizeBytes = data.maxSizeBytes || (100 * 1024 * 1024);

        console.log('[Capture] Session created:', state.captureId);
        return data;
    }

    /**
     * Confirm upload with backend after S3 PUT succeeds.
     */
    async function confirmUpload() {
        const response = await fetch('/api/v1/capture/sessions/' + state.captureId + '/confirm', {
            method: 'POST'
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.error || 'Failed to confirm upload');
        }

        return await response.json();
    }

    // ============================================================
    // Recording Controls
    // ============================================================

    /**
     * Start recording.
     * Creates session, builds stream, starts MediaRecorder.
     */
    async function startRecording(options = {}) {
        if (state.isRecording) {
            throw new Error('Recording already in progress');
        }

        try {
            updateStatus('initializing');

            // Create backend session
            const captureType = options.captureType || 'SCREEN_MIC';
            state.isAudioOnly = captureType === 'AUDIO_ONLY';
            await createSession(captureType);

            // Build capture stream based on type
            if (state.isAudioOnly) {
                await buildAudioOnlyStream();
            } else {
                await buildCaptureStream({
                    includeScreen: captureType !== 'WEBCAM_ONLY' && captureType !== 'WEBCAM_MIC',
                    includeMic: captureType !== 'SCREEN_ONLY',
                    includeSystemAudio: captureType === 'SCREEN_SYSTEM' || captureType === 'SCREEN_ALL',
                    includeWebcam: captureType === 'WEBCAM_ONLY' || captureType === 'WEBCAM_MIC' || captureType === 'SCREEN_WEBCAM'
                });
            }

            // Set up MediaRecorder with appropriate options
            const mimeType = getSupportedMimeType();
            const recorderOptions = state.isAudioOnly
                ? { audioBitsPerSecond: CONFIG.audioOnlyBitsPerSecond }
                : { videoBitsPerSecond: CONFIG.videoBitsPerSecond, audioBitsPerSecond: CONFIG.audioBitsPerSecond };

            if (mimeType) {
                recorderOptions.mimeType = mimeType;
            }

            state.mediaRecorder = new MediaRecorder(state.combinedStream, recorderOptions);
            state.recordedChunks = [];

            state.mediaRecorder.ondataavailable = (event) => {
                if (event.data && event.data.size > 0) {
                    state.recordedChunks.push(event.data);
                }
            };

            state.mediaRecorder.onstop = () => {
                console.log('[Capture] Recording stopped, processing...');
                handleRecordingComplete();
            };

            state.mediaRecorder.onerror = (event) => {
                console.error('[Capture] Recorder error:', event.error);
                handleError(event.error || new Error('Recording failed'));
            };

            // Start recording
            state.mediaRecorder.start();
            state.isRecording = true;
            state.isPaused = false;
            state.startTime = Date.now();

            // Start duration timer
            startDurationTimer();

            // Add beforeunload warning
            window.addEventListener('beforeunload', beforeUnloadHandler);

            updateStatus('recording');
            console.log('[Capture] Recording started');

        } catch (err) {
            cleanup();
            handleError(err);
            throw err;
        }
    }

    /**
     * Pause recording.
     */
    function pauseRecording() {
        if (!state.isRecording || state.isPaused) return;
        if (state.mediaRecorder && state.mediaRecorder.state === 'recording') {
            state.mediaRecorder.pause();
            state.isPaused = true;
            updateStatus('paused');
            console.log('[Capture] Recording paused');
        }
    }

    /**
     * Resume recording.
     */
    function resumeRecording() {
        if (!state.isRecording || !state.isPaused) return;
        if (state.mediaRecorder && state.mediaRecorder.state === 'paused') {
            state.mediaRecorder.resume();
            state.isPaused = false;
            updateStatus('recording');
            console.log('[Capture] Recording resumed');
        }
    }

    /**
     * Stop recording and trigger upload.
     */
    function stopRecording() {
        if (!state.isRecording) return;

        console.log('[Capture] Stopping recording...');

        if (state.mediaRecorder && state.mediaRecorder.state !== 'inactive') {
            state.mediaRecorder.stop();
        }

        state.isRecording = false;
        stopDurationTimer();
        window.removeEventListener('beforeunload', beforeUnloadHandler);
    }

    /**
     * Cancel recording without uploading.
     */
    function cancelRecording() {
        stopRecording();
        state.recordedChunks = [];
        cleanup();
        updateStatus('cancelled');
        console.log('[Capture] Recording cancelled');
    }

    // ============================================================
    // Upload Logic
    // ============================================================

    /**
     * Handle recording complete - build blob and upload.
     */
    async function handleRecordingComplete() {
        updateStatus('processing');

        if (state.recordedChunks.length === 0) {
            handleError(new Error('No recording data captured'));
            return;
        }

        // Build final blob
        const mimeType = state.mediaRecorder?.mimeType || 'video/webm';
        const blob = new Blob(state.recordedChunks, { type: mimeType });
        state.recordedChunks = [];  // Free memory

        console.log('[Capture] Recording complete:', (blob.size / 1024 / 1024).toFixed(2), 'MB');

        // Check size
        if (blob.size > state.maxSizeBytes) {
            handleError(new Error(`Recording too large (${(blob.size / 1024 / 1024).toFixed(1)}MB). Maximum is ${(state.maxSizeBytes / 1024 / 1024).toFixed(0)}MB`));
            cleanup();
            return;
        }

        // Upload with retry
        try {
            await uploadWithRetry(blob);

            // Confirm with backend
            updateStatus('verifying');
            const result = await confirmUpload();

            cleanup();
            updateStatus('complete');

            if (state.onComplete) {
                state.onComplete({
                    captureId: state.captureId,
                    fileSizeBytes: blob.size,
                    durationMs: state.durationMs,
                    ...result
                });
            }
        } catch (err) {
            handleError(err);
            cleanup();
        }
    }

    /**
     * Upload blob to S3 with retry logic.
     */
    async function uploadWithRetry(blob, attempt = 1) {
        updateStatus('uploading');

        try {
            const xhr = new XMLHttpRequest();

            await new Promise((resolve, reject) => {
                xhr.upload.onprogress = (event) => {
                    if (event.lengthComputable && state.onProgress) {
                        const percent = Math.round((event.loaded / event.total) * 100);
                        state.onProgress({
                            phase: 'uploading',
                            percent,
                            loaded: event.loaded,
                            total: event.total
                        });
                    }
                };

                xhr.onload = () => {
                    if (xhr.status >= 200 && xhr.status < 300) {
                        resolve();
                    } else {
                        reject(new Error(`Upload failed: HTTP ${xhr.status}`));
                    }
                };

                xhr.onerror = () => reject(new Error('Network error during upload'));
                xhr.ontimeout = () => reject(new Error('Upload timed out'));

                xhr.open('PUT', state.putUrl);
                xhr.setRequestHeader('Content-Type', blob.type || 'video/webm');
                xhr.timeout = 5 * 60 * 1000;  // 5 minute timeout
                xhr.send(blob);
            });

            console.log('[Capture] Upload complete');
        } catch (err) {
            if (attempt < CONFIG.maxRetries) {
                console.warn(`[Capture] Upload attempt ${attempt} failed, retrying...`);
                await delay(CONFIG.retryDelayMs);
                return uploadWithRetry(blob, attempt + 1);
            }
            throw err;
        }
    }

    // ============================================================
    // Duration Timer
    // ============================================================

    function startDurationTimer() {
        state.durationTimer = setInterval(() => {
            if (state.isRecording && !state.isPaused) {
                state.durationMs = Date.now() - state.startTime;
                const seconds = Math.floor(state.durationMs / 1000);

                // Use audio-specific limits for audio-only mode
                const maxDuration = state.isAudioOnly
                    ? CONFIG.audioMaxDurationSeconds
                    : CONFIG.maxDurationSeconds;
                const warningAt = state.isAudioOnly
                    ? CONFIG.audioWarningAtSeconds
                    : CONFIG.warningAtSeconds;

                // Check max duration
                if (seconds >= maxDuration) {
                    console.log('[Capture] Max duration reached');
                    stopRecording();
                    return;
                }

                // Warning near limit
                if (seconds === warningAt && state.onStatusChange) {
                    state.onStatusChange({
                        status: 'warning',
                        message: 'Approaching time limit',
                        durationMs: state.durationMs
                    });
                }

                if (state.onProgress) {
                    state.onProgress({
                        phase: 'recording',
                        durationMs: state.durationMs,
                        durationFormatted: formatDuration(state.durationMs),
                        isAudioOnly: state.isAudioOnly
                    });
                }
            }
        }, 1000);
    }

    function stopDurationTimer() {
        if (state.durationTimer) {
            clearInterval(state.durationTimer);
            state.durationTimer = null;
        }
    }

    // ============================================================
    // Cleanup
    // ============================================================

    function cleanup() {
        // Stop streams
        [state.screenStream, state.micStream, state.combinedStream].forEach(stream => {
            if (stream) {
                stream.getTracks().forEach(track => track.stop());
            }
        });
        state.screenStream = null;
        state.micStream = null;
        state.combinedStream = null;

        // Close audio context
        if (state.audioContext) {
            state.audioContext.close().catch(() => {});
            state.audioContext = null;
        }

        // Clear recorder
        state.mediaRecorder = null;
        state.recordedChunks = [];

        // Clear timer
        stopDurationTimer();

        // Remove beforeunload
        window.removeEventListener('beforeunload', beforeUnloadHandler);
    }

    // ============================================================
    // Helpers
    // ============================================================

    function updateStatus(status, message) {
        if (state.onStatusChange) {
            state.onStatusChange({ status, message, durationMs: state.durationMs });
        }
    }

    function handleError(error) {
        console.error('[Capture] Error:', error);
        updateStatus('error', error.message);
        if (state.onError) {
            state.onError(error);
        }
    }

    function beforeUnloadHandler(event) {
        if (state.isRecording) {
            event.preventDefault();
            event.returnValue = 'Recording in progress. Are you sure you want to leave?';
            return event.returnValue;
        }
    }

    function formatDuration(ms) {
        const totalSeconds = Math.floor(ms / 1000);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    }

    function delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    // ============================================================
    // Browser Support Check
    // ============================================================

    function checkBrowserSupport(mode = 'video') {
        const issues = [];
        const isAudioMode = mode === 'audio';

        // Audio-only mode has much better browser support
        if (!isAudioMode && (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia)) {
            issues.push('Screen capture not supported (try Chrome or Firefox)');
        }

        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            issues.push('Microphone access not supported');
        }

        if (typeof MediaRecorder === 'undefined') {
            issues.push('MediaRecorder not supported');
        }

        if (!isAudioMode && !window.AudioContext && !window.webkitAudioContext) {
            issues.push('Web Audio API not supported');
        }

        // Check for Safari limitations
        const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
        if (isSafari) {
            if (isAudioMode) {
                // Safari audio MediaRecorder support varies by version
                const safariMatch = navigator.userAgent.match(/Version\/(\d+)/);
                const safariVersion = safariMatch ? parseInt(safariMatch[1]) : 0;
                if (safariVersion < 14) {
                    issues.push('Safari version too old for audio recording. Please update Safari or use Chrome.');
                }
            } else {
                issues.push('Safari has limited screen capture support. Chrome or Firefox recommended.');
            }
        }

        // iOS limitations
        const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent);
        if (isIOS) {
            if (!isAudioMode) {
                issues.push('Screen capture not available on iOS. Use audio-only mode.');
            } else {
                issues.push('iOS: Recording may stop if app is backgrounded.');
            }
        }

        return {
            supported: issues.length === 0 ||
                (issues.length === 1 && (issues[0].includes('backgrounded') || issues[0].includes('Safari has limited'))),
            audioSupported: !issues.some(i => i.includes('Microphone') || i.includes('MediaRecorder')),
            videoSupported: !issues.some(i => i.includes('Screen capture') || i.includes('iOS')),
            issues
        };
    }

    // ============================================================
    // Public API
    // ============================================================

    window.WebCapture = {
        // Browser support
        checkSupport: checkBrowserSupport,

        // Recording controls
        start: startRecording,
        pause: pauseRecording,
        resume: resumeRecording,
        stop: stopRecording,
        cancel: cancelRecording,

        // State
        isRecording: () => state.isRecording,
        isPaused: () => state.isPaused,
        isAudioOnly: () => state.isAudioOnly,
        getDuration: () => state.durationMs,
        getCaptureId: () => state.captureId,

        // Event handlers
        onStatusChange: (cb) => { state.onStatusChange = cb; },
        onProgress: (cb) => { state.onProgress = cb; },
        onError: (cb) => { state.onError = cb; },
        onComplete: (cb) => { state.onComplete = cb; },

        // Configuration
        config: CONFIG,

        // Capture types (for reference)
        CaptureType: {
            SCREEN_ONLY: 'SCREEN_ONLY',
            SCREEN_MIC: 'SCREEN_MIC',
            SCREEN_SYSTEM: 'SCREEN_SYSTEM',
            SCREEN_ALL: 'SCREEN_ALL',
            WEBCAM_ONLY: 'WEBCAM_ONLY',
            WEBCAM_MIC: 'WEBCAM_MIC',
            SCREEN_WEBCAM: 'SCREEN_WEBCAM',
            AUDIO_ONLY: 'AUDIO_ONLY'  // Simplest, most reliable
        }
    };

})();
