/**
 * AURA Video Verification JavaScript
 * Handles camera access, liveness detection, and verification flow
 *
 * Production-ready with:
 * - Cross-browser mimeType negotiation
 * - Challenge timestamp tracking for backend correlation
 * - Status polling instead of fake processing animations
 * - Proper DOM element caching
 * - Upload retry with exponential backoff
 * - Server response validation
 * - Capture metadata for session binding
 */

const VerificationApp = {
    // State
    currentStep: 1,
    sessionId: null,
    stream: null,
    challenges: [],
    currentChallengeIndex: 0,
    recordedChunks: [],
    mediaRecorder: null,
    captureTimeout: null,
    isInitialized: false,

    // Capture metadata for server correlation
    captureMetadata: {
        mimeType: null,
        recordingStartTime: null,
        recordingEndTime: null,
        videoWidth: null,
        videoHeight: null,
        challengeTimestamps: []  // [{type: 'BLINK', startedAt: timestamp, endedAt: timestamp}]
    },

    // Polling state
    pollingInterval: null,
    maxPollingAttempts: 60,  // 2 minutes at 2-second intervals

    // Upload retry config
    maxRetries: 3,
    retryDelayMs: 1000,

    // DOM Elements - populated in init()
    elements: {},

    /**
     * Pick the best supported mimeType for MediaRecorder
     * Falls back through preferred codecs for cross-browser compatibility
     */
    pickMimeType() {
        const mimeTypes = [
            'video/webm;codecs=vp9',
            'video/webm;codecs=vp8',
            'video/webm',
            'video/mp4;codecs=avc1',
            'video/mp4'
        ];

        for (const mimeType of mimeTypes) {
            if (MediaRecorder.isTypeSupported(mimeType)) {
                console.log(`Using mimeType: ${mimeType}`);
                return mimeType;
            }
        }

        // Last resort - let browser pick
        console.warn('No preferred mimeType supported, using browser default');
        return '';
    },

    /**
     * Initialize the app - caches all DOM elements here
     */
    init() {
        if (this.isInitialized) {
            console.warn('VerificationApp already initialized');
            return;
        }

        console.log('Initializing Video Verification...');

        // Cache DOM elements AFTER DOM is ready
        this.elements = {
            steps: document.querySelectorAll('.verification-step'),
            progressSteps: document.querySelectorAll('.step'),
            cameraPreview: document.getElementById('camera-preview'),
            captureCanvas: document.getElementById('capture-canvas'),
            livenessPrompt: document.getElementById('liveness-prompt'),
            countdownOverlay: document.getElementById('countdown-overlay'),
            countdownNumber: document.getElementById('countdown-number'),
            cameraError: document.getElementById('camera-error'),
            challengesList: document.getElementById('challenges-list'),

            // Buttons
            btnStart: document.getElementById('btn-start'),
            btnCapture: document.getElementById('btn-capture'),
            btnCancelCapture: document.getElementById('btn-cancel-capture'),
            btnRetry: document.getElementById('btn-retry'),

            // Processing
            processingUpload: document.getElementById('processing-upload'),
            processingAnalyze: document.getElementById('processing-analyze'),
            processingVerify: document.getElementById('processing-verify'),
            processingStatus: document.getElementById('processing-status'),

            // Results
            resultSuccess: document.getElementById('result-success'),
            resultFailure: document.getElementById('result-failure'),
            failureReason: document.getElementById('failure-reason'),
            scoreFace: document.getElementById('score-face'),
            scoreLiveness: document.getElementById('score-liveness'),
            scoreAuthenticity: document.getElementById('score-authenticity'),
        };

        this.bindEvents();
        this.checkExistingVerification();
        this.isInitialized = true;
    },

    /**
     * Bind event listeners
     */
    bindEvents() {
        if (this.elements.btnStart) {
            this.elements.btnStart.addEventListener('click', () => this.startVerification());
        }
        if (this.elements.btnCapture) {
            this.elements.btnCapture.addEventListener('click', () => this.captureVideo());
        }
        if (this.elements.btnCancelCapture) {
            this.elements.btnCancelCapture.addEventListener('click', () => this.cancelCapture());
        }
        if (this.elements.btnRetry) {
            this.elements.btnRetry.addEventListener('click', () => this.retry());
        }
    },

    /**
     * Validate server response shape
     */
    validateResponse(response, requiredFields = []) {
        if (!response || typeof response !== 'object') {
            throw new Error('Invalid server response');
        }

        for (const field of requiredFields) {
            if (!(field in response)) {
                throw new Error(`Missing required field: ${field}`);
            }
        }

        return response;
    },

    /**
     * Check if user already has verification
     */
    async checkExistingVerification() {
        try {
            const response = await fetch('/video/verification/status');

            if (!response.ok) {
                console.warn('Verification status check failed:', response.status);
                return;
            }

            const data = await response.json();

            if (data.isVerified) {
                // Show success step directly
                this.goToStep(4);
                this.showSuccess(data);
            }
        } catch (error) {
            console.error('Error checking verification status:', error);
        }
    },

    /**
     * Start verification process
     */
    async startVerification() {
        try {
            this.elements.btnStart.disabled = true;
            this.elements.btnStart.textContent = 'Starting...';

            // Start session
            const response = await fetch('/video/verification/start', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || `Server error: ${response.status}`);
            }

            const data = await response.json();
            this.validateResponse(data, ['sessionId']);

            this.sessionId = data.sessionId;
            this.challenges = data.challenges?.challenges || [];

            // Reset capture metadata
            this.captureMetadata = {
                mimeType: null,
                recordingStartTime: null,
                recordingEndTime: null,
                videoWidth: null,
                videoHeight: null,
                challengeTimestamps: []
            };

            // Request camera access
            await this.requestCamera();

            // Move to capture step
            this.goToStep(2);
            this.displayChallenges();
            this.startLivenessDetection();

        } catch (error) {
            console.error('Error starting verification:', error);
            alert('Failed to start verification. Please try again.');
            this.elements.btnStart.disabled = false;
            this.elements.btnStart.textContent = 'Start Verification';
        }
    },

    /**
     * Request camera access
     */
    async requestCamera() {
        try {
            this.stream = await navigator.mediaDevices.getUserMedia({
                video: {
                    width: { ideal: 1280 },
                    height: { ideal: 720 },
                    facingMode: 'user'
                },
                audio: false
            });

            this.elements.cameraPreview.srcObject = this.stream;

            // Get actual video dimensions for metadata
            const videoTrack = this.stream.getVideoTracks()[0];
            if (videoTrack) {
                const settings = videoTrack.getSettings();
                this.captureMetadata.videoWidth = settings.width;
                this.captureMetadata.videoHeight = settings.height;
            }

            if (this.elements.cameraError) {
                this.elements.cameraError.style.display = 'none';
            }

            // Enable capture button after a short delay
            setTimeout(() => {
                if (this.elements.btnCapture) {
                    this.elements.btnCapture.disabled = false;
                }
            }, 1000);

        } catch (error) {
            console.error('Camera access error:', error);
            if (this.elements.cameraPreview) {
                this.elements.cameraPreview.style.display = 'none';
            }
            if (this.elements.cameraError) {
                this.elements.cameraError.style.display = 'block';
            }
            throw error;
        }
    },

    /**
     * Display liveness challenges
     */
    displayChallenges() {
        if (!this.elements.challengesList) return;

        this.elements.challengesList.innerHTML = '';

        if (this.challenges.length === 0) {
            // Default challenges if none provided
            this.challenges = [
                { type: 'READY', instruction: 'Position your face in the oval' },
                { type: 'BLINK', instruction: 'Please blink naturally' },
                { type: 'TURN_HEAD_LEFT', instruction: 'Turn your head slightly to the left' },
                { type: 'SMILE', instruction: 'Please smile' }
            ];
        }

        this.challenges.forEach((challenge, index) => {
            const item = document.createElement('div');
            item.className = 'challenge-item';
            item.dataset.challenge = challenge.type.toLowerCase();
            item.innerHTML = `
                <span class="challenge-status"></span>
                <span class="challenge-text">${challenge.instruction}</span>
            `;
            this.elements.challengesList.appendChild(item);
        });
    },

    /**
     * Start liveness detection prompts
     */
    startLivenessDetection() {
        this.currentChallengeIndex = 0;
        this.captureMetadata.challengeTimestamps = [];
        this.showNextChallenge();
    },

    /**
     * Show next liveness challenge with timestamp tracking
     */
    showNextChallenge() {
        if (this.currentChallengeIndex >= this.challenges.length) {
            this.updatePrompt('Ready to capture!');
            return;
        }

        const challenge = this.challenges[this.currentChallengeIndex];
        const challengeItems = this.elements.challengesList?.querySelectorAll('.challenge-item') || [];
        const startTime = Date.now();

        // Record challenge start timestamp
        this.captureMetadata.challengeTimestamps.push({
            type: challenge.type,
            index: this.currentChallengeIndex,
            startedAt: startTime,
            endedAt: null
        });

        // Mark current as active
        challengeItems.forEach((item, index) => {
            item.classList.remove('active', 'completed');
            if (index === this.currentChallengeIndex) {
                item.classList.add('active');
            } else if (index < this.currentChallengeIndex) {
                item.classList.add('completed');
            }
        });

        this.updatePrompt(challenge.instruction);

        // Auto-advance after 3 seconds
        this.captureTimeout = setTimeout(() => {
            // Record challenge end timestamp
            const currentTimestamp = this.captureMetadata.challengeTimestamps[this.currentChallengeIndex];
            if (currentTimestamp) {
                currentTimestamp.endedAt = Date.now();
            }

            if (challengeItems[this.currentChallengeIndex]) {
                challengeItems[this.currentChallengeIndex].classList.add('completed');
            }
            this.currentChallengeIndex++;
            this.showNextChallenge();
        }, 3000);
    },

    /**
     * Update liveness prompt
     */
    updatePrompt(text) {
        const promptElement = this.elements.livenessPrompt;
        if (!promptElement) return;

        const promptText = promptElement.querySelector('.prompt-text');
        if (promptText) {
            promptText.textContent = text;
        }
    },

    /**
     * Capture video
     */
    async captureVideo() {
        try {
            if (this.elements.btnCapture) {
                this.elements.btnCapture.disabled = true;
            }

            // Clear any pending challenge timeout
            if (this.captureTimeout) {
                clearTimeout(this.captureTimeout);
                this.captureTimeout = null;
            }

            // Show countdown
            await this.showCountdown();

            // Record a short video (3-5 seconds)
            await this.recordVideo();

        } catch (error) {
            console.error('Capture error:', error);
            alert('Failed to capture video. Please try again.');
            if (this.elements.btnCapture) {
                this.elements.btnCapture.disabled = false;
            }
        }
    },

    /**
     * Show countdown before capture
     */
    showCountdown() {
        return new Promise((resolve) => {
            if (!this.elements.countdownOverlay || !this.elements.countdownNumber) {
                resolve();
                return;
            }

            this.elements.countdownOverlay.style.display = 'flex';
            let count = 3;

            const countdown = setInterval(() => {
                this.elements.countdownNumber.textContent = count;

                if (count === 0) {
                    clearInterval(countdown);
                    this.elements.countdownOverlay.style.display = 'none';
                    resolve();
                }
                count--;
            }, 1000);
        });
    },

    /**
     * Record video with proper mimeType negotiation
     */
    async recordVideo() {
        return new Promise((resolve, reject) => {
            this.recordedChunks = [];

            try {
                // Pick the best supported mimeType
                const mimeType = this.pickMimeType();
                this.captureMetadata.mimeType = mimeType || 'video/webm';

                const recorderOptions = mimeType ? { mimeType } : {};

                // Create media recorder with negotiated mimeType
                this.mediaRecorder = new MediaRecorder(this.stream, recorderOptions);

                this.mediaRecorder.ondataavailable = (event) => {
                    if (event.data.size > 0) {
                        this.recordedChunks.push(event.data);
                    }
                };

                this.mediaRecorder.onstop = () => {
                    this.captureMetadata.recordingEndTime = Date.now();

                    const actualMimeType = this.mediaRecorder.mimeType || this.captureMetadata.mimeType;
                    const blob = new Blob(this.recordedChunks, { type: actualMimeType });

                    // Determine file extension from mimeType
                    const extension = actualMimeType.includes('mp4') ? 'mp4' : 'webm';

                    this.submitVideo(blob, extension);
                    resolve();
                };

                this.mediaRecorder.onerror = (event) => {
                    console.error('MediaRecorder error:', event.error);
                    reject(event.error);
                };

                // Record timestamp before starting
                this.captureMetadata.recordingStartTime = Date.now();

                // Start recording
                this.mediaRecorder.start();
                this.updatePrompt('Recording...');

                // Stop after 5 seconds
                setTimeout(() => {
                    if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
                        this.mediaRecorder.stop();
                    }
                }, 5000);

            } catch (error) {
                reject(error);
            }
        });
    },

    /**
     * Submit video for verification with retry logic
     */
    async submitVideo(videoBlob, extension = 'webm') {
        try {
            // Stop camera
            this.stopCamera();

            // Move to processing step
            this.goToStep(3);
            this.showProcessingStatus('Uploading video...');

            if (this.elements.processingUpload) {
                this.elements.processingUpload.classList.add('active');
            }

            // Create form data with metadata
            const formData = new FormData();
            formData.append('video', videoBlob, `verification.${extension}`);
            formData.append('sessionId', this.sessionId);
            formData.append('metadata', JSON.stringify({
                mimeType: this.captureMetadata.mimeType,
                recordingStartTime: this.captureMetadata.recordingStartTime,
                recordingEndTime: this.captureMetadata.recordingEndTime,
                durationMs: this.captureMetadata.recordingEndTime - this.captureMetadata.recordingStartTime,
                videoWidth: this.captureMetadata.videoWidth,
                videoHeight: this.captureMetadata.videoHeight,
                challengeTimestamps: this.captureMetadata.challengeTimestamps,
                userAgent: navigator.userAgent
            }));

            // Submit with retry
            let lastError = null;
            for (let attempt = 1; attempt <= this.maxRetries; attempt++) {
                try {
                    const response = await fetch('/video/verification/submit', {
                        method: 'POST',
                        body: formData
                    });

                    if (!response.ok) {
                        const errorData = await response.json().catch(() => ({}));
                        throw new Error(errorData.error || `Server error: ${response.status}`);
                    }

                    const data = await response.json();

                    // Mark upload complete
                    if (this.elements.processingUpload) {
                        this.elements.processingUpload.classList.remove('active');
                        this.elements.processingUpload.classList.add('completed');
                    }

                    // Start polling for result instead of using fake timers
                    this.startStatusPolling(data);
                    return;

                } catch (error) {
                    lastError = error;
                    console.warn(`Upload attempt ${attempt}/${this.maxRetries} failed:`, error);

                    if (attempt < this.maxRetries) {
                        // Exponential backoff
                        const delay = this.retryDelayMs * Math.pow(2, attempt - 1);
                        this.showProcessingStatus(`Upload failed, retrying in ${delay/1000}s...`);
                        await this.sleep(delay);
                    }
                }
            }

            // All retries failed
            throw lastError;

        } catch (error) {
            console.error('Submit error:', error);
            this.showFailure({ message: `Failed to submit verification: ${error.message}` });
        }
    },

    /**
     * Sleep utility for retry backoff
     */
    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    },

    /**
     * Show processing status message
     */
    showProcessingStatus(message) {
        if (this.elements.processingStatus) {
            this.elements.processingStatus.textContent = message;
        }
    },

    /**
     * Start polling for verification status instead of fake animations
     */
    startStatusPolling(initialResponse) {
        let attempts = 0;

        this.showProcessingStatus('Analyzing video...');
        if (this.elements.processingAnalyze) {
            this.elements.processingAnalyze.classList.add('active');
        }

        // If initial response already has a final status, handle it immediately
        if (initialResponse.status === 'VERIFIED' || initialResponse.status === 'FAILED' || initialResponse.status === 'REJECTED') {
            this.handleFinalResult(initialResponse);
            return;
        }

        // Poll for status updates
        this.pollingInterval = setInterval(async () => {
            attempts++;

            if (attempts > this.maxPollingAttempts) {
                clearInterval(this.pollingInterval);
                this.pollingInterval = null;
                this.showFailure({ message: 'Verification timed out. Please try again.' });
                return;
            }

            try {
                const response = await fetch('/video/verification/status');

                if (!response.ok) {
                    console.warn('Status poll failed:', response.status);
                    return; // Continue polling
                }

                const data = await response.json();

                // Update UI based on processing stage
                if (data.stage === 'ANALYZING') {
                    this.showProcessingStatus('Analyzing facial features...');
                } else if (data.stage === 'VERIFYING') {
                    if (this.elements.processingAnalyze) {
                        this.elements.processingAnalyze.classList.remove('active');
                        this.elements.processingAnalyze.classList.add('completed');
                    }
                    if (this.elements.processingVerify) {
                        this.elements.processingVerify.classList.add('active');
                    }
                    this.showProcessingStatus('Verifying identity...');
                }

                // Check for final status
                if (data.status === 'VERIFIED' || data.status === 'FAILED' || data.status === 'REJECTED') {
                    clearInterval(this.pollingInterval);
                    this.pollingInterval = null;
                    this.handleFinalResult(data);
                }

            } catch (error) {
                console.error('Status poll error:', error);
                // Continue polling on error
            }
        }, 2000);
    },

    /**
     * Handle final verification result
     */
    handleFinalResult(data) {
        // Complete all processing indicators
        if (this.elements.processingAnalyze) {
            this.elements.processingAnalyze.classList.remove('active');
            this.elements.processingAnalyze.classList.add('completed');
        }
        if (this.elements.processingVerify) {
            this.elements.processingVerify.classList.remove('active');
            this.elements.processingVerify.classList.add('completed');
        }

        this.goToStep(4);

        if (data.status === 'VERIFIED') {
            this.showSuccess(data);
        } else {
            this.showFailure(data);
        }
    },

    /**
     * Show success result
     */
    showSuccess(data) {
        if (this.elements.resultSuccess) {
            this.elements.resultSuccess.style.display = 'block';
        }
        if (this.elements.resultFailure) {
            this.elements.resultFailure.style.display = 'none';
        }

        // Display scores if available
        if (data.scores) {
            if (this.elements.scoreFace) {
                this.elements.scoreFace.textContent = Math.round((data.scores.faceMatch || 0) * 100) + '%';
            }
            if (this.elements.scoreLiveness) {
                this.elements.scoreLiveness.textContent = Math.round((data.scores.liveness || 0) * 100) + '%';
            }
            if (this.elements.scoreAuthenticity) {
                this.elements.scoreAuthenticity.textContent = Math.round((data.scores.authenticity || 0) * 100) + '%';
            }
        }
    },

    /**
     * Show failure result
     */
    showFailure(data) {
        if (this.elements.resultSuccess) {
            this.elements.resultSuccess.style.display = 'none';
        }
        if (this.elements.resultFailure) {
            this.elements.resultFailure.style.display = 'block';
        }

        if (data.message && this.elements.failureReason) {
            this.elements.failureReason.textContent = data.message;
        } else if (data.error && this.elements.failureReason) {
            this.elements.failureReason.textContent = data.error;
        }
    },

    /**
     * Navigate to step
     */
    goToStep(stepNumber) {
        this.currentStep = stepNumber;

        // Update step indicators
        if (this.elements.progressSteps) {
            this.elements.progressSteps.forEach((step, index) => {
                const stepNum = index + 1;
                step.classList.remove('active', 'completed');

                if (stepNum === stepNumber) {
                    step.classList.add('active');
                } else if (stepNum < stepNumber) {
                    step.classList.add('completed');
                }
            });
        }

        // Update content
        if (this.elements.steps) {
            this.elements.steps.forEach((step, index) => {
                step.classList.remove('active');
                if (index === stepNumber - 1) {
                    step.classList.add('active');
                }
            });
        }
    },

    /**
     * Cancel capture and return to instructions
     */
    cancelCapture() {
        // Clear any pending timeout
        if (this.captureTimeout) {
            clearTimeout(this.captureTimeout);
            this.captureTimeout = null;
        }

        this.stopCamera();
        this.goToStep(1);

        if (this.elements.btnStart) {
            this.elements.btnStart.disabled = false;
            this.elements.btnStart.textContent = 'Start Verification';
        }
    },

    /**
     * Retry verification
     */
    retry() {
        // Stop any polling
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }

        // Clear any pending timeout
        if (this.captureTimeout) {
            clearTimeout(this.captureTimeout);
            this.captureTimeout = null;
        }

        // Reset state
        this.sessionId = null;
        this.currentChallengeIndex = 0;
        this.recordedChunks = [];
        this.captureMetadata = {
            mimeType: null,
            recordingStartTime: null,
            recordingEndTime: null,
            videoWidth: null,
            videoHeight: null,
            challengeTimestamps: []
        };

        // Reset processing items
        if (this.elements.processingUpload) {
            this.elements.processingUpload.classList.remove('active', 'completed');
        }
        if (this.elements.processingAnalyze) {
            this.elements.processingAnalyze.classList.remove('active', 'completed');
        }
        if (this.elements.processingVerify) {
            this.elements.processingVerify.classList.remove('active', 'completed');
        }

        // Go back to instructions
        this.goToStep(1);

        if (this.elements.btnStart) {
            this.elements.btnStart.disabled = false;
            this.elements.btnStart.textContent = 'Start Verification';
        }
    },

    /**
     * Stop camera stream
     */
    stopCamera() {
        if (this.stream) {
            this.stream.getTracks().forEach(track => track.stop());
            this.stream = null;
            if (this.elements.cameraPreview) {
                this.elements.cameraPreview.srcObject = null;
            }
        }
    },

    /**
     * Cleanup on page unload
     */
    cleanup() {
        this.stopCamera();

        if (this.captureTimeout) {
            clearTimeout(this.captureTimeout);
            this.captureTimeout = null;
        }

        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }
    }
};

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    VerificationApp.init();
});

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    VerificationApp.cleanup();
});
