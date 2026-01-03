/**
 * Donation Modal JavaScript
 * Handles donation prompt display, user interactions, and celebrations
 */

// ========================================
// GLOBAL STATE
// ========================================
let selectedDonationAmount = 25; // Default to $25 (Believer tier)
let currentPromptId = null;
let currentPromptType = 'DEFAULT';

// localStorage keys for rate limiting
const LAST_PROMPT_KEY = 'aura_last_donation_prompt';
const PROMPT_DISMISS_COUNT_KEY = 'aura_prompt_dismiss_count';
const MIN_HOURS_BETWEEN_PROMPTS = 48;

// Amount validation constraints
const MIN_DONATION_AMOUNT = 5;
const MAX_DONATION_AMOUNT = 500;
const MAX_DISMISSALS_BEFORE_EXTENDED_COOLDOWN = 3;
const EXTENDED_COOLDOWN_DAYS = 30;

// ========================================
// AMOUNT VALIDATION
// ========================================

/**
 * Normalize and validate donation amount
 * @param {string|number} value - The input value
 * @returns {number|null} - Validated amount or null if invalid
 */
function normalizeAmount(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return null;
    const rounded = Math.round(n); // Integer cents, no fractions
    if (rounded < MIN_DONATION_AMOUNT || rounded > MAX_DONATION_AMOUNT) return null;
    return rounded;
}

// ========================================
// MODAL CONTROL
// ========================================

/**
 * Show the donation modal
 * @param {string} promptType - Type of prompt (AFTER_MATCH, AFTER_DATE, etc.)
 * @param {number} promptId - Backend prompt ID (optional)
 */
function showDonationModal(promptType = 'DEFAULT', promptId = null) {
    // Check localStorage rate limiting (secondary guard - backend is primary)
    if (!shouldShowPrompt()) {
        console.log('Donation prompt rate-limited locally');
        return;
    }

    currentPromptType = promptType;
    currentPromptId = promptId;

    // Get prompt configuration
    const config = window.DONATION_PROMPT_MESSAGES[promptType] || window.DONATION_PROMPT_MESSAGES.DEFAULT;

    // Update modal content
    updateModalContent(config);

    // Show modal
    const modal = document.getElementById('donation-modal');
    if (modal) {
        modal.classList.add('active');
        document.body.style.overflow = 'hidden';

        // Show celebration animations if applicable
        if (config.celebration) {
            setTimeout(() => showCelebration(), 300);
        }

        // Track in localStorage
        localStorage.setItem(LAST_PROMPT_KEY, new Date().toISOString());
    }
}

/**
 * Close the donation modal
 */
function closeDonationModal() {
    const modal = document.getElementById('donation-modal');
    if (modal) {
        modal.classList.remove('active');
        document.body.style.overflow = '';

        // Clear celebrations
        clearCelebrations();
    }
}

/**
 * Dismiss the modal (track dismissal)
 */
function dismissDonationModal() {
    // Track dismissal count
    const dismissCount = parseInt(localStorage.getItem(PROMPT_DISMISS_COUNT_KEY) || '0');
    localStorage.setItem(PROMPT_DISMISS_COUNT_KEY, (dismissCount + 1).toString());

    // Send dismissal to backend if we have a prompt ID
    if (currentPromptId) {
        fetch(`/api/v1/donation/dismiss/${currentPromptId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        }).catch(err => console.error('Failed to record dismissal:', err));
    }

    closeDonationModal();
}

/**
 * Update modal content based on prompt type
 */
function updateModalContent(config) {
    // Update icon
    const icon = document.getElementById('donation-icon');
    if (icon) {
        icon.innerHTML = `<i class="fas ${config.icon}"></i>`;
    }

    // Update headline
    const headline = document.getElementById('donation-headline');
    if (headline) {
        headline.textContent = config.headline;
    }

    // Update message
    const message = document.getElementById('donation-message');
    if (message) {
        message.textContent = config.message;
    }
}

// ========================================
// AMOUNT SELECTION
// ========================================

/**
 * Select a predefined donation amount
 */
function selectDonationAmount(amount) {
    const normalized = normalizeAmount(amount);
    if (normalized === null) {
        console.warn('Invalid preset amount:', amount);
        return;
    }

    selectedDonationAmount = normalized;

    // Update button states
    document.querySelectorAll('.donation-amount-btn').forEach(btn => {
        const btnAmount = parseInt(btn.dataset.amount);
        if (btnAmount === normalized) {
            btn.classList.add('selected');
        } else {
            btn.classList.remove('selected');
        }
    });

    // Clear custom amount input
    const customInput = document.getElementById('custom-amount-input');
    if (customInput) {
        customInput.value = '';
    }

    // Show tier info
    updateTierInfo(normalized);
}

/**
 * Select a custom donation amount
 */
function selectCustomAmount(value) {
    const normalized = normalizeAmount(value);
    if (normalized === null) {
        // Show validation error
        const customInput = document.getElementById('custom-amount-input');
        if (customInput) {
            customInput.setCustomValidity(`Please enter an amount between $${MIN_DONATION_AMOUNT} and $${MAX_DONATION_AMOUNT}`);
            customInput.reportValidity();
        }
        return;
    }

    selectedDonationAmount = normalized;

    // Clear validation error
    const customInput = document.getElementById('custom-amount-input');
    if (customInput) {
        customInput.setCustomValidity('');
    }

    // Deselect all preset buttons
    document.querySelectorAll('.donation-amount-btn').forEach(btn => {
        btn.classList.remove('selected');
    });

    // Show tier info
    updateTierInfo(normalized);
}

/**
 * Update tier benefits display
 */
function updateTierInfo(amount) {
    const tierInfo = document.getElementById('tier-info');
    const tierText = document.getElementById('tier-benefit-text');

    if (!tierInfo || !tierText) return;

    let benefit = '';
    if (amount >= 100) {
        benefit = 'Founding Member: Quarterly updates, feature input, founder badge, and our eternal gratitude!';
    } else if (amount >= 51) {
        benefit = 'Builder: Early access to new cities, founder badge, and priority support!';
    } else if (amount >= 21) {
        benefit = 'Believer: Your name on our supporters page (optional) and a supporter badge!';
    } else if (amount >= MIN_DONATION_AMOUNT) {
        benefit = 'Supporter: Thank you email and our gratitude for helping keep AURA free!';
    } else {
        tierInfo.style.display = 'none';
        return;
    }

    tierText.textContent = benefit;
    tierInfo.style.display = 'block';
}

// ========================================
// DONATION FLOW
// ========================================

/**
 * Proceed to donation payment page via Stripe Checkout session
 */
function proceedToDonation() {
    const amount = normalizeAmount(selectedDonationAmount);
    if (amount === null) {
        alert(`Please select an amount between $${MIN_DONATION_AMOUNT} and $${MAX_DONATION_AMOUNT}`);
        return;
    }

    // Disable button while processing
    const donateBtn = document.getElementById('donate-btn');
    if (donateBtn) {
        donateBtn.disabled = true;
        donateBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';
    }

    // Create checkout session via backend
    fetch('/api/v1/donation/checkout', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            amount: amount,
            promptId: currentPromptId,
            promptType: currentPromptType
        })
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to create checkout session');
            }
            return response.json();
        })
        .then(data => {
            if (data.checkoutUrl) {
                // Redirect to Stripe Checkout (same tab is better UX)
                window.location.href = data.checkoutUrl;
            } else {
                throw new Error('No checkout URL returned');
            }
        })
        .catch(err => {
            console.error('Failed to create checkout session:', err);
            // Re-enable button
            if (donateBtn) {
                donateBtn.disabled = false;
                donateBtn.innerHTML = '<i class="fas fa-heart"></i> <span>Support AURA</span>';
            }
            alert('Unable to process donation. Please try again or visit our donation page directly.');
        });
}

/**
 * Show thank you message after donation
 */
function showThankYouMessage() {
    const headline = document.getElementById('donation-headline');
    const message = document.getElementById('donation-message');

    if (headline) {
        headline.textContent = 'Thank You!';
    }
    if (message) {
        message.textContent = 'Opening donation page... Your support helps keep AURA free and ad-free for everyone.';
    }

    // Show extra celebration
    showConfetti();
}

// ========================================
// RATE LIMITING (Client-side secondary guard)
// ========================================

/**
 * Check if we should show a prompt based on localStorage
 * This is a secondary guard - backend is the primary source of truth
 */
function shouldShowPrompt() {
    // Check dismiss count - after N dismissals, use extended cooldown
    const dismissCount = parseInt(localStorage.getItem(PROMPT_DISMISS_COUNT_KEY) || '0');
    if (dismissCount >= MAX_DISMISSALS_BEFORE_EXTENDED_COOLDOWN) {
        const lastPrompt = localStorage.getItem(LAST_PROMPT_KEY);
        if (lastPrompt) {
            const lastPromptDate = new Date(lastPrompt);
            const daysSince = (new Date() - lastPromptDate) / (1000 * 60 * 60 * 24);
            if (daysSince < EXTENDED_COOLDOWN_DAYS) {
                console.log(`Extended cooldown: ${dismissCount} dismissals, ${Math.round(daysSince)} days since last prompt`);
                return false;
            }
            // Reset dismiss count after extended cooldown expires
            localStorage.setItem(PROMPT_DISMISS_COUNT_KEY, '0');
        }
    }

    // Check minimum hours between prompts
    const lastPrompt = localStorage.getItem(LAST_PROMPT_KEY);
    if (!lastPrompt) return true;

    const lastPromptDate = new Date(lastPrompt);
    const hoursSince = (new Date() - lastPromptDate) / (1000 * 60 * 60);

    return hoursSince >= MIN_HOURS_BETWEEN_PROMPTS;
}

/**
 * Check backend for pending donation prompt
 */
function checkForDonationPrompt() {
    fetch('/api/v1/donation/info')
        .then(response => response.json())
        .then(data => {
            if (data.showPrompt) {
                // Use backend-provided promptType and promptId
                const promptType = data.promptType || 'MONTHLY';
                const promptId = data.promptId || null;
                showDonationModal(promptType, promptId);
            }
        })
        .catch(err => console.error('Failed to check donation prompt:', err));
}

// ========================================
// CELEBRATION ANIMATIONS
// ========================================

/**
 * Show celebration (hearts, confetti)
 */
function showCelebration() {
    // Show floating hearts
    showFloatingHearts();

    // Optional: Show confetti for special moments
    if (currentPromptType === 'RELATIONSHIP_EXIT' || currentPromptType === 'MILESTONE') {
        showConfetti();
    }
}

/**
 * Clear all celebration animations
 */
function clearCelebrations() {
    const heartsContainer = document.getElementById('donation-hearts');
    if (heartsContainer) {
        heartsContainer.innerHTML = '';
    }

    const confettiCanvas = document.getElementById('donation-confetti');
    if (confettiCanvas) {
        confettiCanvas.classList.remove('active');
    }
}

/**
 * Show floating hearts animation
 */
function showFloatingHearts() {
    const heartsContainer = document.getElementById('donation-hearts');
    if (!heartsContainer) return;

    for (let i = 0; i < 8; i++) {
        setTimeout(() => {
            const heart = document.createElement('div');
            heart.className = 'donation-heart';
            heart.innerHTML = '<i class="fas fa-heart"></i>';
            heart.style.left = Math.random() * 100 + '%';
            heart.style.animationDelay = Math.random() * 0.5 + 's';
            heartsContainer.appendChild(heart);

            // Remove after animation
            setTimeout(() => heart.remove(), 3000);
        }, i * 200);
    }
}

/**
 * Show confetti animation
 */
function showConfetti() {
    const canvas = document.getElementById('donation-confetti');
    if (!canvas) return;

    canvas.classList.add('active');
    const ctx = canvas.getContext('2d');

    // Set canvas size
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    // Confetti particles
    const particles = [];
    const colors = ['#a78bfa', '#ec4899', '#06b6d4', '#10b981', '#f59e0b'];

    for (let i = 0; i < 100; i++) {
        particles.push({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height - canvas.height,
            size: Math.random() * 8 + 4,
            speedY: Math.random() * 3 + 2,
            speedX: Math.random() * 2 - 1,
            color: colors[Math.floor(Math.random() * colors.length)],
            rotation: Math.random() * 360,
            rotationSpeed: Math.random() * 10 - 5
        });
    }

    let animationFrame;
    function animate() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // Iterate backwards to safely remove particles
        for (let i = particles.length - 1; i >= 0; i--) {
            const p = particles[i];

            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate((p.rotation * Math.PI) / 180);
            ctx.fillStyle = p.color;
            ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size);
            ctx.restore();

            p.y += p.speedY;
            p.x += p.speedX;
            p.rotation += p.rotationSpeed;

            // Remove particles that fall off screen
            if (p.y > canvas.height) {
                particles.splice(i, 1);
            }
        }

        if (particles.length > 0) {
            animationFrame = requestAnimationFrame(animate);
        } else {
            canvas.classList.remove('active');
            cancelAnimationFrame(animationFrame);
        }
    }

    animate();

    // Stop after 5 seconds regardless
    setTimeout(() => {
        canvas.classList.remove('active');
        if (animationFrame) {
            cancelAnimationFrame(animationFrame);
        }
    }, 5000);
}

// ========================================
// EVENT LISTENERS & INITIALIZATION
// ========================================

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    // Select default amount on load
    selectDonationAmount(25);

    // Check for pending donation prompt after a delay
    // (give page time to load)
    setTimeout(() => {
        checkForDonationPrompt();
    }, 5000);

    // Handle window resize for confetti canvas
    window.addEventListener('resize', function() {
        const canvas = document.getElementById('donation-confetti');
        if (canvas && canvas.classList.contains('active')) {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        }
    });

    // Escape key closes modal
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            const modal = document.getElementById('donation-modal');
            if (modal && modal.classList.contains('active')) {
                dismissDonationModal();
            }
        }
    });

    // Click outside modal to close
    const modal = document.getElementById('donation-modal');
    if (modal) {
        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                dismissDonationModal();
            }
        });
    }
});

// ========================================
// PUBLIC API
// ========================================

// Expose functions globally for use in Thymeleaf templates
window.showDonationModal = showDonationModal;
window.closeDonationModal = closeDonationModal;
window.dismissDonationModal = dismissDonationModal;
window.selectDonationAmount = selectDonationAmount;
window.selectCustomAmount = selectCustomAmount;
window.proceedToDonation = proceedToDonation;

// Function to manually trigger donation modal (for testing or special occasions)
window.triggerDonationPrompt = function(type = 'DEFAULT') {
    showDonationModal(type, null);
};
