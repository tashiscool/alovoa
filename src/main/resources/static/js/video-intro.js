/**
 * Video Introduction JavaScript
 * Handles video recording, upload, AI analysis polling, and manual tag fallback
 */

// State management
let mediaRecorder = null;
let recordedChunks = [];
let stream = null;
let isRecording = false;
let recordingStartTime;
let recordingTimer;
let videoBlob = null;
let uploadedVideoId = null;
let playbackUrl = null; // Track object URL for cleanup

// Polling state (non-overlapping)
let isPolling = false;
let stopPolling = false;
let pollTimeoutId = null;

const MAX_RECORDING_SECONDS = 120; // 2 minutes
const MAX_VIDEO_SIZE_MB = 50; // Maximum upload size
const MAX_POLL_COUNT = 120; // 2 minutes at 2s intervals
let pollCount = 0;

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

// ========================================
// MIME TYPE NEGOTIATION (Safari/iOS support)
// ========================================

/**
 * Pick the best supported MIME type for MediaRecorder
 * Includes Safari/iOS fallbacks
 */
function pickIntroMimeType() {
    const candidates = [
        'video/webm;codecs=vp9,opus',
        'video/webm;codecs=vp8,opus',
        'video/webm;codecs=vp8',
        'video/webm',
        'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
        'video/mp4'
    ];
    return candidates.find(t => MediaRecorder.isTypeSupported(t)) || '';
}

/**
 * Check if MediaRecorder is available
 */
function isMediaRecorderSupported() {
    return typeof MediaRecorder !== 'undefined' && typeof navigator.mediaDevices !== 'undefined';
}

// ========================================
// INITIALIZATION
// ========================================

document.addEventListener('DOMContentLoaded', () => {
    console.log('Video intro page loaded');

    // Check browser support
    if (!isMediaRecorderSupported()) {
        console.warn('MediaRecorder not supported - showing upload fallback');
        showUploadFallback();
    }
});

// Stop polling on page unload to prevent orphaned requests
window.addEventListener('beforeunload', () => {
    stopPolling = true;
    if (pollTimeoutId) {
        clearTimeout(pollTimeoutId);
    }
});

/**
 * Show fallback for browsers without MediaRecorder
 */
function showUploadFallback() {
    // Hide record button, show file upload option
    const recordSection = document.getElementById('record-section');
    const uploadFallback = document.getElementById('upload-fallback');

    if (recordSection) recordSection.style.display = 'none';
    if (uploadFallback) uploadFallback.style.display = 'block';
}

// ========================================
// RECORDING FLOW
// ========================================

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

        // Get best supported MIME type
        const mimeType = pickIntroMimeType();
        const options = mimeType ? { mimeType } : {};

        try {
            mediaRecorder = new MediaRecorder(stream, options);
        } catch (e) {
            console.warn('MediaRecorder failed with options, trying without:', e);
            mediaRecorder = new MediaRecorder(stream);
        }

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
    // Determine MIME type from recorder or default
    const mimeType = mediaRecorder.mimeType || 'video/webm';
    videoBlob = new Blob(recordedChunks, { type: mimeType });

    // Show preview and upload buttons
    document.getElementById('preview-btn').classList.remove('is-hidden');
    document.getElementById('upload-btn').classList.remove('is-hidden');
    document.getElementById('record-btn').classList.add('is-hidden');
}

/**
 * Preview the recorded video (with URL cleanup)
 */
function previewRecording() {
    const videoPreview = document.getElementById('video-preview');
    const videoPlayback = document.getElementById('video-playback');

    videoPreview.classList.add('is-hidden');
    videoPlayback.classList.remove('is-hidden');

    // Revoke previous URL to prevent memory leak
    if (playbackUrl) {
        URL.revokeObjectURL(playbackUrl);
    }
    playbackUrl = URL.createObjectURL(videoBlob);
    videoPlayback.src = playbackUrl;
    videoPlayback.play();
}

/**
 * Cancel recording and go back
 */
function cancelRecording() {
    cleanupResources();
    showStep('step-intro');
}

// ========================================
// UPLOAD FLOW
// ========================================

/**
 * Upload video to server
 */
async function uploadVideo() {
    if (!videoBlob) {
        showError('No video recorded. Please record a video first.');
        return;
    }

    // Validate file size
    const maxBytes = MAX_VIDEO_SIZE_MB * 1024 * 1024;
    if (videoBlob.size > maxBytes) {
        showError(`Video is too large. Please record a shorter clip (max ${MAX_VIDEO_SIZE_MB}MB).`);
        return;
    }

    showStep('step-processing');

    try {
        const formData = new FormData();
        // Use appropriate extension based on MIME type
        const extension = videoBlob.type.includes('mp4') ? 'mp4' : 'webm';
        formData.append('video', videoBlob, `intro.${extension}`);

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

// ========================================
// POLLING (Non-overlapping, with cleanup)
// ========================================

/**
 * Start polling for AI analysis completion
 * Uses self-scheduling loop to prevent overlap
 */
function startPollingAnalysis() {
    pollCount = 0;
    stopPolling = false;
    isPolling = false;

    updateProcessingStep(1, 'complete');
    updateProcessingStep(2, 'active');

    // Start the polling loop
    pollStatusLoop();
}

/**
 * Self-scheduling poll loop (prevents overlap)
 */
async function pollStatusLoop() {
    // Guard against overlapping or stopped polls
    if (isPolling || stopPolling) return;

    isPolling = true;
    pollCount++;

    try {
        const response = await fetch(`/api/video-intro/status/${uploadedVideoId}`, {
            cache: 'no-store' // Prevent cached responses
        });
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
                stopPolling = true;
                updateProcessingStep(2, 'complete');
                updateProcessingStep(3, 'complete');
                updateProcessingStep(4, 'complete');
                handleAnalysisSuccess(status);
                return; // Don't reschedule

            case 'FAILED':
                stopPolling = true;
                handleAnalysisFailure();
                return; // Don't reschedule

            case 'SKIPPED':
                stopPolling = true;
                handleAnalysisFailure();
                return; // Don't reschedule

            default:
                // Unknown status - keep polling but log it
                console.warn('Unknown analysis status:', status.analysisStatus);
                break;
        }

        // Timeout after max polls
        if (pollCount >= MAX_POLL_COUNT) {
            stopPolling = true;
            handleAnalysisFailure();
            return;
        }

    } catch (error) {
        console.error('Polling error:', error);
        // Continue polling despite errors (network hiccups)
    } finally {
        isPolling = false;
    }

    // Schedule next poll if not stopped
    if (!stopPolling) {
        pollTimeoutId = setTimeout(pollStatusLoop, 2000);
    }
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

// ========================================
// ANALYSIS RESULTS (XSS-safe rendering)
// ========================================

/**
 * Handle successful AI analysis
 * Uses DOM-safe rendering to prevent XSS
 */
function handleAnalysisSuccess(status) {
    const detectedTags = [];

    // Parse detected tags from analysis result
    // Prefer structured format: status.detectedTags = [{label, category, confidence}]
    if (status.detectedTags && Array.isArray(status.detectedTags)) {
        status.detectedTags.forEach(tag => {
            if (tag.label) {
                detectedTags.push({
                    label: tag.label,
                    confidence: tag.confidence || 1.0,
                    category: tag.category || 'general'
                });
            }
        });
    } else if (status.personalityIndicators) {
        // Fallback: parse old format
        try {
            const indicators = JSON.parse(status.personalityIndicators);
            Object.keys(indicators).forEach(key => {
                detectedTags.push({
                    label: formatTagLabel(key),
                    confidence: indicators[key] || 1.0,
                    category: 'personality'
                });
            });
        } catch (e) {
            console.error('Failed to parse personality indicators:', e);
        }
    }

    // Display detected tags using DOM-safe rendering
    const detectedTagsEl = document.getElementById('detected-tags');
    detectedTagsEl.innerHTML = ''; // Clear

    if (detectedTags.length > 0) {
        detectedTags.forEach(tag => {
            const span = document.createElement('span');
            span.className = 'aura-trait aura-trait-positive';

            const icon = document.createElement('i');
            icon.className = 'fas fa-tag';
            span.appendChild(icon);
            span.appendChild(document.createTextNode(' '));

            // Use textContent to prevent XSS
            const labelText = document.createTextNode(tag.label);
            span.appendChild(labelText);

            // Optionally show confidence
            if (tag.confidence < 1.0) {
                const confSpan = document.createElement('span');
                confSpan.className = 'tag-confidence';
                confSpan.textContent = ` (${Math.round(tag.confidence * 100)}%)`;
                span.appendChild(confSpan);
            }

            detectedTagsEl.appendChild(span);
        });
        document.getElementById('detected-tags-container').style.display = 'block';
    } else {
        document.getElementById('detected-tags-container').style.display = 'none';
    }

    showStep('step-success');
}

/**
 * Format internal tag key to user-friendly label
 */
function formatTagLabel(key) {
    // Convert snake_case or camelCase to Title Case
    return key
        .replace(/_/g, ' ')
        .replace(/([a-z])([A-Z])/g, '$1 $2')
        .replace(/\b\w/g, c => c.toUpperCase());
}

/**
 * Handle AI analysis failure - show manual tagging
 */
function handleAnalysisFailure() {
    // Initialize tag selection UI
    initializeTagSelection();
    showStep('step-manual-tagging');
}

// ========================================
// MANUAL TAGGING (XSS-safe)
// ========================================

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
 * Render a tag category (DOM-safe)
 */
function renderTagCategory(category, tags) {
    const container = document.getElementById(`${category}-tags`);
    container.innerHTML = '';

    tags.forEach(tag => {
        const div = document.createElement('div');
        div.className = 'tag-chip';
        div.dataset.category = category;
        div.dataset.tagId = tag.id;

        const icon = document.createElement('i');
        icon.className = `fas ${tag.icon}`;
        div.appendChild(icon);

        const span = document.createElement('span');
        span.textContent = tag.label;
        div.appendChild(span);

        // Use event listener instead of inline onclick
        div.addEventListener('click', () => toggleTag(category, tag.id));

        container.appendChild(div);
    });
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
 * Add a custom tag (DOM-safe, no innerHTML with user input)
 */
function addCustomTag(tagValue) {
    if (selectedTags.custom.has(tagValue)) {
        return; // Already added
    }

    selectedTags.custom.add(tagValue);

    const customTagsDisplay = document.getElementById('custom-tags-display');

    const wrap = document.createElement('div');
    wrap.className = 'custom-tag';

    const span = document.createElement('span');
    span.textContent = tagValue; // Safe: textContent escapes HTML

    const removeIcon = document.createElement('i');
    removeIcon.className = 'fas fa-times remove-tag';
    // Use event listener instead of inline onclick with user data
    removeIcon.addEventListener('click', () => {
        selectedTags.custom.delete(tagValue);
        wrap.remove();
    });

    wrap.appendChild(span);
    wrap.appendChild(removeIcon);
    customTagsDisplay.appendChild(wrap);
}

/**
 * Remove a custom tag (legacy function for compatibility)
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

        // Show success with manual tags (DOM-safe rendering)
        const detectedTagsEl = document.getElementById('detected-tags');
        detectedTagsEl.innerHTML = '';

        allTags.forEach(tag => {
            const span = document.createElement('span');
            span.className = 'aura-trait aura-trait-neutral';

            const icon = document.createElement('i');
            icon.className = 'fas fa-tag';
            span.appendChild(icon);
            span.appendChild(document.createTextNode(' '));
            span.appendChild(document.createTextNode(tag));

            detectedTagsEl.appendChild(span);
        });

        document.getElementById('detected-tags-container').style.display = 'block';
        showStep('step-success');

    } catch (error) {
        console.error('Submit tags error:', error);
        showError('Failed to save your tags. Please try again.');
    }
}

// ========================================
// RETRY & CLEANUP
// ========================================

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
    cleanupResources();

    // Reset state
    recordedChunks = [];
    videoBlob = null;
    uploadedVideoId = null;

    // Reset selected tags
    Object.keys(selectedTags).forEach(key => selectedTags[key].clear());

    showStep('step-intro');
}

/**
 * Clean up all resources (memory, streams, timers)
 */
function cleanupResources() {
    // Stop polling
    stopPolling = true;
    if (pollTimeoutId) {
        clearTimeout(pollTimeoutId);
        pollTimeoutId = null;
    }

    // Clear recording timer
    if (recordingTimer) {
        clearInterval(recordingTimer);
        recordingTimer = null;
    }

    // Revoke object URL to free memory
    if (playbackUrl) {
        URL.revokeObjectURL(playbackUrl);
        playbackUrl = null;
    }

    // Stop all media tracks
    if (stream) {
        stream.getTracks().forEach(track => track.stop());
        stream = null;
    }

    // Clear recorder
    if (mediaRecorder) {
        if (mediaRecorder.state !== 'inactive') {
            try {
                mediaRecorder.stop();
            } catch (e) {
                // Ignore errors when stopping already-stopped recorder
            }
        }
        mediaRecorder = null;
    }
}

/**
 * Retry intro (when already completed)
 */
function retryIntro() {
    if (confirm('This will replace your current video introduction. Continue?')) {
        window.location.reload();
    }
}

// ========================================
// UI HELPERS
// ========================================

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
