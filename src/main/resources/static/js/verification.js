/**
 * AURA Video Verification JavaScript
 * Handles camera access, liveness detection, and verification flow
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

    // DOM Elements
    elements: {
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

        // Results
        resultSuccess: document.getElementById('result-success'),
        resultFailure: document.getElementById('result-failure'),
        failureReason: document.getElementById('failure-reason'),
        scoreFace: document.getElementById('score-face'),
        scoreLiveness: document.getElementById('score-liveness'),
        scoreAuthenticity: document.getElementById('score-authenticity'),
    },

    /**
     * Initialize the app
     */
    init() {
        console.log('Initializing Video Verification...');
        this.bindEvents();
        this.checkExistingVerification();
    },

    /**
     * Bind event listeners
     */
    bindEvents() {
        this.elements.btnStart.addEventListener('click', () => this.startVerification());
        this.elements.btnCapture.addEventListener('click', () => this.captureVideo());
        this.elements.btnCancelCapture.addEventListener('click', () => this.cancelCapture());
        if (this.elements.btnRetry) {
            this.elements.btnRetry.addEventListener('click', () => this.retry());
        }
    },

    /**
     * Check if user already has verification
     */
    async checkExistingVerification() {
        try {
            const response = await fetch('/verification/api/status');
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
            const response = await fetch('/verification/api/start', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            const data = await response.json();

            if (!data.sessionId) {
                throw new Error('Failed to start verification session');
            }

            this.sessionId = data.sessionId;
            this.challenges = data.challenges?.challenges || [];

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
            this.elements.cameraError.style.display = 'none';

            // Enable capture button after a short delay
            setTimeout(() => {
                this.elements.btnCapture.disabled = false;
            }, 1000);

        } catch (error) {
            console.error('Camera access error:', error);
            this.elements.cameraPreview.style.display = 'none';
            this.elements.cameraError.style.display = 'block';
            throw error;
        }
    },

    /**
     * Display liveness challenges
     */
    displayChallenges() {
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
        this.showNextChallenge();
    },

    /**
     * Show next liveness challenge
     */
    showNextChallenge() {
        if (this.currentChallengeIndex >= this.challenges.length) {
            this.updatePrompt('Ready to capture!');
            return;
        }

        const challenge = this.challenges[this.currentChallengeIndex];
        const challengeItems = this.elements.challengesList.querySelectorAll('.challenge-item');

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
        setTimeout(() => {
            challengeItems[this.currentChallengeIndex]?.classList.add('completed');
            this.currentChallengeIndex++;
            this.showNextChallenge();
        }, 3000);
    },

    /**
     * Update liveness prompt
     */
    updatePrompt(text) {
        const promptText = this.elements.livenessPrompt.querySelector('.prompt-text');
        promptText.textContent = text;
    },

    /**
     * Capture video
     */
    async captureVideo() {
        try {
            this.elements.btnCapture.disabled = true;

            // Show countdown
            await this.showCountdown();

            // Record a short video (3-5 seconds)
            await this.recordVideo();

        } catch (error) {
            console.error('Capture error:', error);
            alert('Failed to capture video. Please try again.');
            this.elements.btnCapture.disabled = false;
        }
    },

    /**
     * Show countdown before capture
     */
    showCountdown() {
        return new Promise((resolve) => {
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
     * Record video
     */
    async recordVideo() {
        return new Promise((resolve, reject) => {
            this.recordedChunks = [];

            try {
                // Create media recorder
                this.mediaRecorder = new MediaRecorder(this.stream, {
                    mimeType: 'video/webm;codecs=vp9'
                });

                this.mediaRecorder.ondataavailable = (event) => {
                    if (event.data.size > 0) {
                        this.recordedChunks.push(event.data);
                    }
                };

                this.mediaRecorder.onstop = () => {
                    const blob = new Blob(this.recordedChunks, { type: 'video/webm' });
                    this.submitVideo(blob);
                    resolve();
                };

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
     * Submit video for verification
     */
    async submitVideo(videoBlob) {
        try {
            // Stop camera
            this.stopCamera();

            // Move to processing step
            this.goToStep(3);
            this.startProcessing();

            // Create form data
            const formData = new FormData();
            formData.append('video', videoBlob, 'verification.webm');
            formData.append('sessionId', this.sessionId);

            // Mark upload as active
            this.elements.processingUpload.classList.add('active');

            // Submit to server
            const response = await fetch('/verification/api/submit', {
                method: 'POST',
                body: formData
            });

            const data = await response.json();

            // Mark upload complete
            this.elements.processingUpload.classList.remove('active');
            this.elements.processingUpload.classList.add('completed');

            // Process result
            this.processResult(data);

        } catch (error) {
            console.error('Submit error:', error);
            this.showFailure({ message: 'Failed to submit verification. Please try again.' });
        }
    },

    /**
     * Start processing animation
     */
    startProcessing() {
        setTimeout(() => {
            this.elements.processingAnalyze.classList.add('active');
        }, 1000);

        setTimeout(() => {
            this.elements.processingAnalyze.classList.remove('active');
            this.elements.processingAnalyze.classList.add('completed');
            this.elements.processingVerify.classList.add('active');
        }, 3000);
    },

    /**
     * Process verification result
     */
    processResult(data) {
        setTimeout(() => {
            this.elements.processingVerify.classList.remove('active');
            this.elements.processingVerify.classList.add('completed');

            this.goToStep(4);

            if (data.success && data.status === 'VERIFIED') {
                this.showSuccess(data);
            } else {
                this.showFailure(data);
            }
        }, 2000);
    },

    /**
     * Show success result
     */
    showSuccess(data) {
        this.elements.resultSuccess.style.display = 'block';
        this.elements.resultFailure.style.display = 'none';

        // Display scores if available
        if (data.scores) {
            this.elements.scoreFace.textContent = Math.round(data.scores.faceMatch * 100) + '%';
            this.elements.scoreLiveness.textContent = Math.round(data.scores.liveness * 100) + '%';
            this.elements.scoreAuthenticity.textContent = Math.round(data.scores.authenticity * 100) + '%';
        }
    },

    /**
     * Show failure result
     */
    showFailure(data) {
        this.elements.resultSuccess.style.display = 'none';
        this.elements.resultFailure.style.display = 'block';

        if (data.message) {
            this.elements.failureReason.textContent = data.message;
        }
    },

    /**
     * Navigate to step
     */
    goToStep(stepNumber) {
        this.currentStep = stepNumber;

        // Update step indicators
        this.elements.progressSteps.forEach((step, index) => {
            const stepNum = index + 1;
            step.classList.remove('active', 'completed');

            if (stepNum === stepNumber) {
                step.classList.add('active');
            } else if (stepNum < stepNumber) {
                step.classList.add('completed');
            }
        });

        // Update content
        this.elements.steps.forEach((step, index) => {
            step.classList.remove('active');
            if (index === stepNumber - 1) {
                step.classList.add('active');
            }
        });
    },

    /**
     * Cancel capture and return to instructions
     */
    cancelCapture() {
        this.stopCamera();
        this.goToStep(1);
        this.elements.btnStart.disabled = false;
        this.elements.btnStart.textContent = 'Start Verification';
    },

    /**
     * Retry verification
     */
    retry() {
        // Reset state
        this.sessionId = null;
        this.currentChallengeIndex = 0;
        this.recordedChunks = [];

        // Reset processing items
        this.elements.processingUpload.classList.remove('active', 'completed');
        this.elements.processingAnalyze.classList.remove('active', 'completed');
        this.elements.processingVerify.classList.remove('active', 'completed');

        // Go back to instructions
        this.goToStep(1);
        this.elements.btnStart.disabled = false;
        this.elements.btnStart.textContent = 'Start Verification';
    },

    /**
     * Stop camera stream
     */
    stopCamera() {
        if (this.stream) {
            this.stream.getTracks().forEach(track => track.stop());
            this.stream = null;
            this.elements.cameraPreview.srcObject = null;
        }
    },

    /**
     * Cleanup on page unload
     */
    cleanup() {
        this.stopCamera();
        if (this.captureTimeout) {
            clearTimeout(this.captureTimeout);
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
