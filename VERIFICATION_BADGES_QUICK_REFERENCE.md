# Verification Badges - Quick Reference

## Quick Start

### Display Badges in Your Template

**Option 1: Full Badges (Profile Pages)**
```html
<div th:replace="fragments :: verification-badges(${user})"></div>
```
Result: Shows "Verified" text with icon and "Trusted" text with shield icon

**Option 2: Compact Icons (Search Cards, Lists)**
```html
<div th:replace="fragments :: verification-icons(${user})"></div>
```
Result: Shows only icons, ideal for small spaces

## CSS Classes

### Video Verification Badge
```html
<span class="aura-verification-badge">
    <i class="fas fa-check-circle"></i>
    <span>Verified</span>
</span>
```

### Trust Level Badges

```html
<!-- New Member (usually hidden) -->
<span class="aura-trust-badge new-member">
    <i class="fas fa-shield-alt"></i>
    <span>New Member</span>
</span>

<!-- Verified -->
<span class="aura-trust-badge verified">
    <i class="fas fa-shield-alt"></i>
    <span>Verified</span>
</span>

<!-- Trusted -->
<span class="aura-trust-badge trusted">
    <i class="fas fa-shield-alt"></i>
    <span>Trusted</span>
</span>

<!-- Highly Trusted -->
<span class="aura-trust-badge highly-trusted">
    <i class="fas fa-shield-alt"></i>
    <span>Highly Trusted</span>
</span>

<!-- Under Review -->
<span class="aura-trust-badge under-review">
    <i class="fas fa-shield-alt"></i>
    <span>Under Review</span>
</span>

<!-- Restricted -->
<span class="aura-trust-badge restricted">
    <i class="fas fa-shield-alt"></i>
    <span>Restricted</span>
</span>
```

## Thymeleaf Conditional Display

### Show Only If Video Verified
```html
<span th:if="${user.videoVerified}" class="aura-verification-badge">
    <i class="fas fa-check-circle"></i>
    <span>Verified</span>
</span>
```

### Show Based on Trust Level
```html
<span th:if="${user.trustLevel != null and user.trustLevel != 'NEW_MEMBER'}"
      class="aura-trust-badge"
      th:classappend="${#strings.toLowerCase(user.trustLevel).replace('_', '-')}">
    <i class="fas fa-shield-alt"></i>
    <span th:text="${user.trustLevel}">Trusted</span>
</span>
```

### Complete Example with Both Badges
```html
<div class="aura-badge-group">
    <!-- Video Verification -->
    <span th:if="${user.videoVerified}" class="aura-verification-badge" title="Video Verified">
        <i class="fas fa-check-circle"></i>
        <span>Verified</span>
    </span>

    <!-- Trust Level -->
    <span th:if="${user.trustLevel == 'TRUSTED'}" class="aura-trust-badge trusted" title="Trusted Member">
        <i class="fas fa-shield-alt"></i>
        <span>Trusted</span>
    </span>
</div>
```

## Layout Helper Classes

### Badge Group (horizontal layout with spacing)
```html
<div class="aura-badge-group">
    <!-- Multiple badges here -->
</div>
```

### Badge Inline (minimal spacing, for tight layouts)
```html
<div class="aura-badge-inline">
    <!-- Multiple badges here -->
</div>
```

## Common Use Cases

### 1. Profile Header
```html
<div class="profile-header">
    <h1 th:text="${user.firstName}">John Doe</h1>
    <div th:replace="fragments :: verification-badges(${user})"></div>
    <p class="profile-bio" th:text="${user.description}">Bio...</p>
</div>
```

### 2. User Card in Search Results
```html
<div class="user-card">
    <img th:src="${user.profilePicture}" alt="Profile">
    <div class="user-info">
        <h3>
            <span th:text="${user.firstName}">Jane</span>
            <div th:replace="fragments :: verification-icons(${user})"></div>
        </h3>
    </div>
</div>
```

### 3. Chat Message Header
```html
<div class="message-header">
    <img th:src="${sender.profilePicture}" class="avatar">
    <span class="sender-name" th:text="${sender.firstName}">User</span>
    <span th:if="${sender.videoVerified}" class="aura-verification-badge" style="padding: 0.2rem 0.5rem;">
        <i class="fas fa-video"></i>
    </span>
</div>
```

### 4. User List Item
```html
<li class="user-list-item">
    <div class="user-name">
        <span th:text="${user.firstName}">Alex</span>
        <div th:replace="fragments :: verification-icons(${user})"></div>
    </div>
    <span class="user-age" th:text="${user.age}">28</span>
</li>
```

## Accessing User Data in Controller

The verification fields are automatically populated when creating a UserDto:

```java
UserDto dto = UserDto.userToUserDto(UserDto.DtoBuilder.builder()
    .user(user)
    .currentUser(currentUser)
    .userService(userService)
    .ignoreIntention(false)
    .build());

// dto.isVideoVerified() - returns true/false
// dto.getTrustLevel() - returns "TRUSTED", "VERIFIED", etc.
// dto.getReputationScore() - returns 0-100 score
```

## Trust Level Values

| Trust Level | CSS Class | Color Scheme | Meaning |
|------------|-----------|--------------|---------|
| NEW_MEMBER | new-member | Gray | Default for new users |
| VERIFIED | verified | Blue | Basic verification |
| TRUSTED | trusted | Green | Good reputation |
| HIGHLY_TRUSTED | highly-trusted | Gradient Green | Exemplary user |
| UNDER_REVIEW | under-review | Orange | Investigation |
| RESTRICTED | restricted | Red | Account restrictions |

## Color Variables

Use these CSS custom properties for consistency:

```css
--aura-success: #10b981;      /* Green for positive badges */
--aura-info: #3b82f6;          /* Blue for info badges */
--aura-warning: #f59e0b;       /* Orange for warnings */
--aura-danger: #ef4444;        /* Red for restrictions */
--aura-gradient-success: linear-gradient(135deg, #10b981 0%, #06b6d4 100%);
```

## FontAwesome Icons Used

- Video Verification: `fas fa-check-circle` or `fas fa-video`
- Trust Shield: `fas fa-shield-alt`
- Alternative: `fas fa-badge-check`, `fas fa-certificate`

## Styling Tips

### Adjust Badge Size
```html
<span class="aura-verification-badge" style="font-size: 0.65rem; padding: 0.2rem 0.6rem;">
    <i class="fas fa-check-circle"></i>
    <span>Verified</span>
</span>
```

### Icon Only (No Text)
```html
<span class="aura-verification-badge" style="padding: 0.3rem 0.5rem;" title="Video Verified">
    <i class="fas fa-video"></i>
</span>
```

### Custom Colors
```html
<span class="aura-verification-badge" style="background: linear-gradient(135deg, #ec4899, #7c3aed);">
    <i class="fas fa-star"></i>
    <span>Premium</span>
</span>
```

## Debugging

### Check if user has verification data:
```html
<p>Video Verified: <span th:text="${user.videoVerified}">false</span></p>
<p>Trust Level: <span th:text="${user.trustLevel}">NEW_MEMBER</span></p>
<p>Reputation: <span th:text="${user.reputationScore}">50.0</span></p>
```

### Display raw badge without conditions (for testing):
```html
<!-- This will always show, regardless of verification status -->
<span class="aura-verification-badge">
    <i class="fas fa-check-circle"></i>
    <span>Test Badge</span>
</span>
```
