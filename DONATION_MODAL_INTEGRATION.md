# Donation Modal Integration Guide

This guide explains how to integrate the donation modal into your AURA pages.

## Files Created

1. **Thymeleaf Fragment**: `/src/main/resources/templates/fragments/donation-modal.html`
   - Contains the modal HTML structure with dynamic content
   - Includes prompt type configurations for different scenarios

2. **CSS Styling**: `/src/main/resources/static/css/donation-modal.css`
   - AURA-themed modal styling with dark mode support
   - Celebration animations (hearts, confetti)
   - Responsive design for mobile and desktop

3. **JavaScript**: `/src/main/resources/static/js/donation-modal.js`
   - Modal control functions
   - API integration with backend
   - Local storage rate limiting
   - Celebration animations

## Integration Steps

### 1. Include in Your HTML Template

Add these lines to any page where you want the donation modal to be available:

```html
<!DOCTYPE html>
<html th:with="lang=${#locale.language}" th:lang="${lang}" dir="auto" xmlns:th="http://www.w3.org/1999/xhtml">
<head>
    <!-- Existing head content -->

    <!-- Add donation modal CSS -->
    <link rel="stylesheet" href="/css/donation-modal.css"/>
</head>

<body>
    <!-- Your page content -->

    <!-- Include donation modal fragment (add before closing body tag) -->
    <div th:replace="~{fragments.html::donation-modal}"></div>

    <!-- Add donation modal JavaScript -->
    <script src="/js/donation-modal.js"></script>
</body>
</html>
```

### 2. Trigger Donation Modal from JavaScript

The modal can be triggered in several ways:

#### A. Automatically on Page Load
The JavaScript automatically checks for pending donation prompts after 5 seconds.

#### B. Manually via JavaScript
```javascript
// Trigger with specific prompt type
showDonationModal('AFTER_MATCH', promptId);
showDonationModal('AFTER_DATE', promptId);
showDonationModal('FIRST_LIKE', promptId);
showDonationModal('MILESTONE', promptId);
showDonationModal('RELATIONSHIP_EXIT', promptId);

// Or use the global trigger for testing
triggerDonationPrompt('AFTER_MATCH');
```

#### C. From Backend/Controller
In your Spring controller, you can trigger prompts via the DonationService:

```java
@Autowired
private DonationService donationService;

// After a match is created
if (donationService.showAfterMatchPrompt(user)) {
    // Modal will be shown on next page load
}

// After a video date
if (donationService.showAfterDatePrompt(user)) {
    // Modal will be shown on next page load
}

// When user receives first like
if (donationService.showFirstLikePrompt(user)) {
    // Modal will be shown on next page load
}
```

### 3. Example: Chat Page Integration

Here's how to add it to `/src/main/resources/templates/chat.html`:

```html
<!DOCTYPE html>
<html th:with="lang=${#locale.language}" th:lang="${lang}" dir="auto" xmlns:th="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="utf-8">
    <!-- ... existing head content ... -->

    <link rel="stylesheet" href="/css/lib/bulma.min.css"/>
    <link rel="stylesheet" href="/css/alovoa.css"/>
    <link rel="stylesheet" href="/css/aura.css"/>
    <link rel="stylesheet" href="/css/chat.css"/>
    <!-- ADD THIS LINE -->
    <link rel="stylesheet" href="/css/donation-modal.css"/>

    <title>AURA - Chat</title>
</head>

<body class="preload">
    <!-- ... existing page content ... -->

    <!-- ADD THIS LINE before closing body tag -->
    <div th:replace="~{fragments.html::donation-modal}"></div>

    <!-- Existing scripts -->
    <script src="/js/lib/jquery.min.js"></script>
    <!-- ... other scripts ... -->

    <!-- ADD THIS LINE -->
    <script src="/js/donation-modal.js"></script>
</body>
</html>
```

### 4. Example: Video Date Integration

When a video date completes, trigger the prompt:

```javascript
// In your video date completion handler
function onVideoDateComplete() {
    // Your existing completion logic

    // Show donation prompt
    showDonationModal('AFTER_DATE', null);
}
```

## Prompt Types and Messages

The modal automatically adjusts its message based on the prompt type:

- **AFTER_MATCH**: Celebratory message after matching with someone
- **AFTER_DATE**: Gratitude message after completing a video date
- **FIRST_LIKE**: Encouraging message when someone likes their profile
- **MILESTONE**: Celebration when hitting a milestone (10 matches, etc.)
- **RELATIONSHIP_EXIT**: Special message when user marks as "in a relationship"
- **MONTHLY**: General monthly reminder for active users
- **DEFAULT**: Generic donation message

## Customization

### Modify Donation Amounts

Edit `/src/main/resources/templates/fragments/donation-modal.html`:

```html
<div class="donation-amounts">
    <button class="donation-amount-btn" data-amount="5" onclick="selectDonationAmount(5)">
        <span class="amount">$5</span>
        <span class="label">Coffee</span>
    </button>
    <!-- Add more buttons or modify amounts as needed -->
</div>
```

### Change Payment URL

Update the backend configuration in `application.properties`:

```properties
app.donation.payment-url=https://donate.stripe.com/your-link
```

Or update directly in the DonationController.

### Adjust Rate Limiting

Edit `/src/main/resources/static/js/donation-modal.js`:

```javascript
const MIN_HOURS_BETWEEN_PROMPTS = 48; // Change this value
```

## Testing

To test the donation modal without waiting for prompts:

1. Open browser console
2. Run: `triggerDonationPrompt('AFTER_MATCH')`
3. Modal should appear with celebration animations

To reset local rate limiting:

```javascript
localStorage.removeItem('aura_last_donation_prompt');
localStorage.removeItem('aura_prompt_dismiss_count');
```

## API Endpoints

The donation modal uses these backend endpoints:

- `GET /api/v1/donation/info` - Get current user donation status
- `POST /api/v1/donation/dismiss/{promptId}` - Dismiss a prompt
- `POST /api/v1/donation/record` - Record a donation (called by payment webhook)

## Styling Notes

The modal uses AURA's design system:
- CSS variables from `/css/aura.css`
- AURA purple gradient: `linear-gradient(135deg, #a78bfa 0%, #ec4899 100%)`
- Automatic dark mode support
- Responsive for mobile and desktop
- Accessibility features (keyboard nav, focus states)

## Celebration Animations

The modal includes:
- **Floating Hearts**: Shown for celebratory prompts
- **Confetti**: Shown for special moments (relationship exit, milestones)
- **Icon Pulse**: Subtle animation on the modal icon

These can be disabled by setting `celebration: false` in the prompt configuration.
