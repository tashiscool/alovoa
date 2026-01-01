/**
 * Video Introduction JavaScript
 * Handles video recording, upload, AI analysis polling, and manual tag fallback
 */

// State management
let mediaRecorder;
let recordedChunks = [];
let stream;
let isRecording = false;
let recordingStartTime;
let recordingTimer;
let videoBlob;
let uploadedVideoId;
let pollInterval;

const MAX_RECORDING_SECONDS = 120; // 2 minutes

// Predefined tag options
const TAG_OPTIONS = {
    interests: [
        { id: 'hiking', label: 'Hiking', icon: 'fa-mountain' },
        { id: 'reading', label: 'Reading', icon: 'fa-book' },
        { id: 'travel', label: 'Travel', icon: 'fa-plane' },
        { id: 'cooking', label: 'Cooking', icon: 'fa-utensils' },
        { id: 'music', label: 'Music', icon: 'fa-music' },
        { id: 'fitness', label: 'Fitness', icon: 'fa-dumbbell' },
        { id: 'photography', label: 'Photography', icon: 'fa-camera' },
        { id: 'gaming', label: 'Gaming', icon: 'fa-gamepad' },
        { id: 'art', label: 'Art', icon: 'fa-palette' },
        { id: 'sports', label: 'Sports', icon: 'fa-futbol' },
        { id: 'movies', label: 'Movies', icon: 'fa-film' },
        { id: 'volunteering', label: 'Volunteering', icon: 'fa-hands-helping' }
    ],
    personality: [
        { id: 'adventurous', label: 'Adventurous', icon: 'fa-compass' },
        { id: 'creative', label: 'Creative', icon: 'fa-lightbulb' },
        { id: 'thoughtful', label: 'Thoughtful', icon: 'fa-brain' },
        { id: 'humorous', label: 'Humorous', icon: 'fa-smile' },
        { id: 'ambitious', label: 'Ambitious', icon: 'fa-rocket' },
        { id: 'caring', label: 'Caring', icon: 'fa-heart' },
        { id: 'intellectual', label: 'Intellectual', icon: 'fa-graduation-cap' },
        { id: 'easygoing', label: 'Easy-going', icon: 'fa-leaf' },
        { id: 'passionate', label: 'Passionate', icon: 'fa-fire' },
        { id: 'authentic', label: 'Authentic', icon: 'fa-check-circle' }
    ],
    topics: [
        { id: 'career', label: 'Career', icon: 'fa-briefcase' },
        { id: 'family', label: 'Family', icon: 'fa-users' },
        { id: 'values', label: 'Values', icon: 'fa-balance-scale' },
        { id: 'goals', label: 'Goals', icon: 'fa-bullseye' },
        { id: 'lifestyle', label: 'Lifestyle', icon: 'fa-home' },
        { id: 'spirituality', label: 'Spirituality', icon: 'fa-om' },
        { id: 'education', label: 'Education', icon: 'fa-university' },
        { id: 'relationships', label: 'Relationships', icon: 'fa-heart-circle' }
    ]
};

// Selected tags storage
const selectedTags = {
    interests: new Set(),
    personality: new Set(),
    topics: new Set(),
    custom: new Set()
};

/**
 * Initialize the page
 */
document.addEventListener('DOMContentLoaded', () => {
    console.log('Video intro page loaded');
});

/**
 * Start recording flow
 */
async function startRecording() {
    try {
        // Request camera and microphone access
        stream = await navigator.mediaDevices.getUserMedia({
            video: {
                facingMode: 'user',
                width: { ideal: 1280 },
                height: { ideal: 720 }
            },
            audio: true
        });

        const videoPreview = document.getElementById('video-preview');
        videoPreview.srcObject = stream;

        // Initialize MediaRecorder
        const options = { mimeType: 'video/webm;codecs=vp9,opus' };
        if (!MediaRecorder.isTypeSupported(options.mimeType)) {
            options.mimeType = 'video/webm';
        }

        mediaRecorder = new MediaRecorder(stream, options);

        mediaRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                recordedChunks.push(event.data);
            }
        };

        mediaRecorder.onstop = handleRecordingStopped;

        // Show recording step
        showStep('step-recording');

    } catch (error) {
        console.error('Failed to access camera:', error);
        showError('Unable to access your camera. Please grant camera permissions and try again.');
    }
}

/**
 * Toggle recording on/off
 */
function toggleRecording() {
    if (!isRecording) {
        startRecordingVideo();
    } else {
        stopRecordingVideo();
    }
}

/**
 * Start recording video
 */
function startRecordingVideo() {
    recordedChunks = [];
    mediaRecorder.start();
    isRecording = true;
    recordingStartTime = Date.now();

    // Update UI
    document.getElementById('record-btn').classList.add('recording');
    document.getElementById('record-text').textContent = 'Stop';
    document.getElementById('recording-indicator').classList.remove('is-hidden');

    // Start timer
    recordingTimer = setInterval(updateRecordingTimer, 100);
}

/**
 * Stop recording video
 */
function stopRecordingVideo() {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
        isRecording = false;

        // Update UI
        document.getElementById('record-btn').classList.remove('recording');
        document.getElementById('record-text').textContent = 'Start';
        document.getElementById('recording-indicator').classList.add('is-hidden');

        // Stop timer
        clearInterval(recordingTimer);
        document.getElementById('recording-progress').style.width = '0%';
    }
}

/**
 * Update recording timer and progress bar
 */
function updateRecordingTimer() {
    const elapsed = Math.floor((Date.now() - recordingStartTime) / 1000);
    const minutes = Math.floor(elapsed / 60);
    const seconds = elapsed % 60;

    document.getElementById('recording-time').textContent =
        `${minutes}:${seconds.toString().padStart(2, '0')}`;

    // Update progress bar
    const progress = Math.min((elapsed / MAX_RECORDING_SECONDS) * 100, 100);
    document.getElementById('recording-progress').style.width = progress + '%';

    // Auto-stop at max time
    if (elapsed >= MAX_RECORDING_SECONDS) {
        stopRecordingVideo();
    }
}

/**
 * Handle recording stopped
 */
function handleRecordingStopped() {
    videoBlob = new Blob(recordedChunks, { type: 'video/webm' });

    // Show preview and upload buttons
    document.getElementById('preview-btn').classList.remove('is-hidden');
    document.getElementById('upload-btn').classList.remove('is-hidden');
    document.getElementById('record-btn').classList.add('is-hidden');
}

/**
 * Preview the recorded video
 */
function previewRecording() {
    const videoPreview = document.getElementById('video-preview');
    const videoPlayback = document.getElementById('video-playback');

    videoPreview.classList.add('is-hidden');
    videoPlayback.classList.remove('is-hidden');

    videoPlayback.src = URL.createObjectURL(videoBlob);
    videoPlayback.play();
}

/**
 * Cancel recording and go back
 */
function cancelRecording() {
    if (stream) {
        stream.getTracks().forEach(track => track.stop());
    }
    clearInterval(recordingTimer);
    showStep('step-intro');
}

/**
 * Upload video to server
 */
async function uploadVideo() {
    if (!videoBlob) {
        showError('No video recorded. Please record a video first.');
        return;
    }

    showStep('step-processing');

    try {
        const formData = new FormData();
        formData.append('video', videoBlob, 'intro.webm');

        const response = await fetch('/api/video-intro/upload', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error('Upload failed');
        }

        const result = await response.json();
        uploadedVideoId = result.videoId;

        // Stop camera stream
        if (stream) {
            stream.getTracks().forEach(track => track.stop());
        }

        // Start polling for analysis status
        startPollingAnalysis();

    } catch (error) {
        console.error('Upload error:', error);
        showError('Failed to upload video. Please try again.');
    }
}

/**
 * Start polling for AI analysis completion
 */
function startPollingAnalysis() {
    let pollCount = 0;
    const maxPolls = 120; // Poll for up to 2 minutes (2 second intervals)

    updateProcessingStep(1, 'complete');
    updateProcessingStep(2, 'active');

    pollInterval = setInterval(async () => {
        pollCount++;

        try {
            const response = await fetch(`/api/video-intro/status/${uploadedVideoId}`);
            const status = await response.json();

            switch (status.analysisStatus) {
                case 'TRANSCRIBING':
                    updateProcessingStep(2, 'active');
                    break;

                case 'ANALYZING':
                    updateProcessingStep(2, 'complete');
                    updateProcessingStep(3, 'active');
                    break;

                case 'COMPLETED':
                    clearInterval(pollInterval);
                    updateProcessingStep(2, 'complete');
                    updateProcessingStep(3, 'complete');
                    updateProcessingStep(4, 'complete');
                    handleAnalysisSuccess(status);
                    break;

                case 'FAILED':
                    clearInterval(pollInterval);
                    handleAnalysisFailure();
                    break;

                case 'SKIPPED':
                    clearInterval(pollInterval);
                    handleAnalysisFailure();
                    break;
            }

            // Timeout after max polls
            if (pollCount >= maxPolls && status.analysisStatus !== 'COMPLETED') {
                clearInterval(pollInterval);
                handleAnalysisFailure();
            }

        } catch (error) {
            console.error('Polling error:', error);
            // Continue polling despite errors
        }
    }, 2000); // Poll every 2 seconds
}

/**
 * Update processing step UI
 */
function updateProcessingStep(stepNumber, state) {
    const step = document.getElementById(`process-step-${stepNumber}`);
    if (!step) return;

    step.classList.remove('active', 'complete');

    if (state === 'active') {
        step.classList.add('active');
        step.querySelector('i').className = 'fas fa-spinner fa-spin';
    } else if (state === 'complete') {
        step.classList.add('complete');
        step.querySelector('i').className = 'fas fa-check-circle';
    }
}

/**
 * Handle successful AI analysis
 */
function handleAnalysisSuccess(status) {
    // Extract detected topics/tags from analysis
    const detectedTags = [];

    if (status.personalityIndicators) {
        try {
            const indicators = JSON.parse(status.personalityIndicators);
            Object.keys(indicators).forEach(key => {
                detectedTags.push(key);
            });
        } catch (e) {
            console.error('Failed to parse personality indicators:', e);
        }
    }

    // Display detected tags
    const detectedTagsEl = document.getElementById('detected-tags');
    if (detectedTags.length > 0) {
        detectedTagsEl.innerHTML = detectedTags.map(tag =>
            `<span class="aura-trait aura-trait-positive">
                <i class="fas fa-tag"></i> ${tag}
            </span>`
        ).join('');
        document.getElementById('detected-tags-container').style.display = 'block';
    } else {
        document.getElementById('detected-tags-container').style.display = 'none';
    }

    showStep('step-success');
}

/**
 * Handle AI analysis failure - show manual tagging
 */
function handleAnalysisFailure() {
    // Initialize tag selection UI
    initializeTagSelection();
    showStep('step-manual-tagging');
}

/**
 * Initialize tag selection interface
 */
function initializeTagSelection() {
    // Render interests tags
    renderTagCategory('interests', TAG_OPTIONS.interests);
    // Render personality tags
    renderTagCategory('personality', TAG_OPTIONS.personality);
    // Render topics tags
    renderTagCategory('topics', TAG_OPTIONS.topics);
}

/**
 * Render a tag category
 */
function renderTagCategory(category, tags) {
    const container = document.getElementById(`${category}-tags`);
    container.innerHTML = tags.map(tag =>
        `<div class="tag-chip" data-category="${category}" data-tag-id="${tag.id}"
              onclick="toggleTag('${category}', '${tag.id}')">
            <i class="fas ${tag.icon}"></i>
            <span>${tag.label}</span>
        </div>`
    ).join('');
}

/**
 * Toggle tag selection
 */
function toggleTag(category, tagId) {
    const tagSet = selectedTags[category];
    const tagElement = document.querySelector(
        `.tag-chip[data-category="${category}"][data-tag-id="${tagId}"]`
    );

    if (tagSet.has(tagId)) {
        tagSet.delete(tagId);
        tagElement.classList.remove('selected');
    } else {
        tagSet.add(tagId);
        tagElement.classList.add('selected');
    }
}

/**
 * Handle custom tag input
 */
function handleCustomTagInput(event) {
    if (event.key === 'Enter') {
        event.preventDefault();
        const input = event.target;
        const tagValue = input.value.trim();

        if (tagValue && tagValue.length <= 30) {
            addCustomTag(tagValue);
            input.value = '';
        }
    }
}

/**
 * Add a custom tag
 */
function addCustomTag(tagValue) {
    if (selectedTags.custom.has(tagValue)) {
        return; // Already added
    }

    selectedTags.custom.add(tagValue);

    const customTagsDisplay = document.getElementById('custom-tags-display');
    const tagElement = document.createElement('div');
    tagElement.className = 'custom-tag';
    tagElement.innerHTML = `
        <span>${tagValue}</span>
        <i class="fas fa-times remove-tag" onclick="removeCustomTag('${tagValue}')"></i>
    `;
    customTagsDisplay.appendChild(tagElement);
}

/**
 * Remove a custom tag
 */
function removeCustomTag(tagValue) {
    selectedTags.custom.delete(tagValue);
    const customTagsDisplay = document.getElementById('custom-tags-display');
    const tagElements = customTagsDisplay.querySelectorAll('.custom-tag');

    tagElements.forEach(el => {
        if (el.querySelector('span').textContent === tagValue) {
            el.remove();
        }
    });
}

/**
 * Submit manual tags
 */
async function submitManualTags() {
    // Collect all selected tags
    const allTags = [];

    Object.keys(selectedTags).forEach(category => {
        selectedTags[category].forEach(tag => {
            if (category === 'custom') {
                allTags.push(tag);
            } else {
                // Find the tag label
                const tagOption = TAG_OPTIONS[category]?.find(t => t.id === tag);
                if (tagOption) {
                    allTags.push(tagOption.label);
                }
            }
        });
    });

    if (allTags.length < 3) {
        alert('Please select at least 3 tags to describe your video.');
        return;
    }

    try {
        const response = await fetch('/api/video-intro/manual-tags', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                videoId: uploadedVideoId,
                tags: allTags,
                categories: {
                    interests: Array.from(selectedTags.interests),
                    personality: Array.from(selectedTags.personality),
                    topics: Array.from(selectedTags.topics)
                }
            })
        });

        if (!response.ok) {
            throw new Error('Failed to submit tags');
        }

        // Show success with manual tags
        const detectedTagsEl = document.getElementById('detected-tags');
        detectedTagsEl.innerHTML = allTags.map(tag =>
            `<span class="aura-trait aura-trait-neutral">
                <i class="fas fa-tag"></i> ${tag}
            </span>`
        ).join('');
        document.getElementById('detected-tags-container').style.display = 'block';

        showStep('step-success');

    } catch (error) {
        console.error('Submit tags error:', error);
        showError('Failed to save your tags. Please try again.');
    }
}

/**
 * Retry AI analysis
 */
async function retryAnalysis() {
    showStep('step-processing');

    try {
        const response = await fetch(`/api/video-intro/retry/${uploadedVideoId}`, {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Retry failed');
        }

        // Start polling again
        startPollingAnalysis();

    } catch (error) {
        console.error('Retry error:', error);
        showError('Failed to retry analysis. Please try again later.');
    }
}

/**
 * Retry from the start
 */
function retryFromStart() {
    // Reset state
    recordedChunks = [];
    videoBlob = null;
    uploadedVideoId = null;
    clearInterval(pollInterval);

    // Reset selected tags
    Object.keys(selectedTags).forEach(key => selectedTags[key].clear());

    showStep('step-intro');
}

/**
 * Retry intro (when already completed)
 */
function retryIntro() {
    if (confirm('This will replace your current video introduction. Continue?')) {
        window.location.reload();
    }
}

/**
 * Show a specific step
 */
function showStep(stepId) {
    const steps = document.querySelectorAll('.intro-step');
    steps.forEach(step => step.classList.add('is-hidden'));

    const targetStep = document.getElementById(stepId);
    if (targetStep) {
        targetStep.classList.remove('is-hidden');
    }
}

/**
 * Show error step
 */
function showError(message) {
    document.getElementById('error-message').textContent = message;
    showStep('step-error');
}
