# Compatibility Explanation Implementation

This document describes the implementation of compatibility explanation features in the AURA dating app.

## Overview

The compatibility explanation feature allows users to see detailed breakdowns of why they are compatible (or not) with potential matches. It displays:

- Overall compatibility score (0-100)
- Enemy/incompatibility percentage (like OKCupid 2016)
- Dimension scores (values, personality, lifestyle, etc.)
- Top compatibility strengths
- Potential challenges to navigate
- AI-generated summary

## Backend Implementation

### 1. CompatibilityExplanationDto

**Location:** `/src/main/java/com/nonononoki/alovoa/model/CompatibilityExplanationDto.java`

A new DTO that structures compatibility data for display:

```java
public class CompatibilityExplanationDto {
    private Double overallScore;           // 0-100
    private Double enemyScore;             // Incompatibility % (0-100)
    private List<String> topCompatibilities;
    private List<String> potentialChallenges;
    private Map<String, Double> dimensionScores;
    private String summary;
    private Map<String, Object> detailedExplanation;
}
```

### 2. MatchingService Updates

**Location:** `/src/main/java/com/nonononoki/alovoa/service/MatchingService.java`

Enhanced `getCompatibilityExplanation(String matchUuid)` method to:

- Fetch or calculate compatibility scores
- Parse stored `topCompatibilities` and `potentialChallenges` from JSON
- Generate default explanations when AI service data is unavailable
- Create human-readable summaries based on score ranges
- Return structured DTO instead of raw Map

**New Helper Methods:**
- `generateDefaultCompatibilities()` - Creates strength descriptions from scores
- `generateDefaultChallenges()` - Creates challenge descriptions from scores
- `generateCompatibilitySummary()` - Creates overall summary text

### 3. API Endpoint

**Location:** `/src/main/java/com/nonononoki/alovoa/rest/MatchingController.java`

**Endpoint:** `GET /api/v1/matching/compatibility/{matchUuid}`

**Response Example:**
```json
{
  "overallScore": 78.5,
  "enemyScore": 12.3,
  "dimensionScores": {
    "values": 85.0,
    "lifestyle": 72.0,
    "personality": 80.0,
    "attraction": 75.0,
    "circumstantial": 65.0,
    "growth": 70.0
  },
  "topCompatibilities": [
    "Strong alignment in core values and life priorities",
    "Compatible personality traits and communication styles",
    "Similar lifestyle preferences and daily routines"
  ],
  "potentialChallenges": [
    "Communication and mutual respect will help navigate any differences"
  ],
  "summary": "You have strong compatibility with this person. Your core values and personalities align well, creating a solid foundation for connection."
}
```

## Frontend Implementation

### 1. Full Page View

**Location:** `/src/main/resources/templates/compatibility-explanation.html`

A standalone page that displays complete compatibility breakdown with:

- Large overall score display
- Enemy score (if significant > 10%)
- Six dimension progress bars with scores
- Expandable list of top compatibilities
- Expandable list of potential challenges
- Dynamic loading via JavaScript fetch

**Usage:**
Navigate to `/compatibility-explanation?matchUuid={uuid}`

**Features:**
- Animated progress bars
- Color-coded scores (green ≥70, blue ≥50, orange <50)
- Responsive design
- Auto-loads data on page load

### 2. Embeddable Widget

**Location:** `/src/main/resources/templates/compatibility-fragment.html`

A Thymeleaf fragment that can be embedded in other pages (e.g., user profile view):

**Usage in Thymeleaf:**
```html
<div th:replace="~{compatibility-fragment :: compatibility-explanation(matchUuid=${user.uuid})}"></div>
```

**Features:**
- Compact design with top 3 dimensions
- Mini progress bars
- Top 3 strengths and challenges
- Link to full breakdown page
- Self-contained styling and JavaScript

## Data Flow

1. **User views a match profile** → Frontend requests compatibility data
2. **API endpoint** `/api/v1/matching/compatibility/{matchUuid}` is called
3. **MatchingService** checks if compatibility score exists in database
4. If not found → Calculate and store via `calculateAndStoreCompatibility()`
5. **Parse stored data:**
   - Try JSON parsing for `topCompatibilities` and `potentialChallenges`
   - Fallback to newline-separated text parsing
   - Generate defaults if empty
6. **Return DTO** with all explanation data
7. **Frontend renders** scores, bars, lists, and summary

## Database Schema

Uses existing `CompatibilityScore` entity:

| Field | Type | Description |
|-------|------|-------------|
| `explanationJson` | MEDIUMTEXT | AI-generated detailed explanation (JSON) |
| `topCompatibilities` | MEDIUMTEXT | JSON array or newline-separated strengths |
| `potentialChallenges` | MEDIUMTEXT | JSON array or newline-separated challenges |
| `overallScore` | DOUBLE | Overall compatibility 0-100 |
| `enemyScore` | DOUBLE | Incompatibility percentage 0-100 |
| `valuesScore` | DOUBLE | Values dimension score |
| `lifestyleScore` | DOUBLE | Lifestyle dimension score |
| `personalityScore` | DOUBLE | Personality dimension score |
| `attractionScore` | DOUBLE | Attraction dimension score |
| `circumstantialScore` | DOUBLE | Circumstantial dimension score |
| `growthScore` | DOUBLE | Growth potential score |

## Integration Points

### For AI Service Integration

When the AI matching service returns compatibility data, it should populate:

```json
{
  "overall": 78.5,
  "values": 85.0,
  "lifestyle": 72.0,
  "personality": 80.0,
  "attraction": 75.0,
  "circumstantial": 65.0,
  "growth": 70.0,
  "explanation": {
    "topCompatibilities": [
      "Both value work-life balance and family time",
      "Similar communication styles - both prefer direct, honest conversations"
    ],
    "potentialChallenges": [
      "Different sleep schedules may require compromise",
      "Varying social battery levels - one is more extroverted"
    ],
    "summary": "Strong overall compatibility with excellent value alignment..."
  }
}
```

This data is automatically parsed and stored by `calculateAndStoreCompatibility()`.

### For Frontend Developers

To add compatibility widget to a user profile page:

1. **Include fragment in your template:**
   ```html
   <div th:replace="~{compatibility-fragment :: compatibility-explanation(matchUuid=${matchUser.uuid})}"></div>
   ```

2. **Or build custom view using API:**
   ```javascript
   fetch(`/api/v1/matching/compatibility/${matchUuid}`)
     .then(response => response.json())
     .then(data => {
       // Use data.overallScore, data.topCompatibilities, etc.
     });
   ```

## Fallback Behavior

When AI service is unavailable or hasn't generated explanations:

1. **Default Compatibilities Generated:**
   - If dimension score ≥ 70, add positive statement about that dimension
   - If no high scores, use generic "unique qualities that could complement each other"

2. **Default Challenges Generated:**
   - If dimension score < 50, add constructive note about that area
   - If enemy score > 30, add general note about differences
   - If no issues, use generic "communication and mutual respect" message

3. **Summary Generated:**
   - Score-based tiered messages (≥80, ≥70, ≥60, ≥50, <50)
   - Always encouraging but realistic

## Testing

### Manual Testing

1. **Test endpoint directly:**
   ```bash
   curl -X GET "http://localhost:8080/api/v1/matching/compatibility/{valid-uuid}" \
     -H "Cookie: your-session-cookie"
   ```

2. **Test full page:**
   Navigate to: `http://localhost:8080/compatibility-explanation?matchUuid={valid-uuid}`

3. **Test embedded widget:**
   Add fragment to any profile page and verify it loads

### Expected Behaviors

- ✓ Scores display correctly (0-100)
- ✓ Progress bars animate on load
- ✓ Colors change based on score thresholds
- ✓ Lists populate with meaningful text
- ✓ Fallbacks work when data is missing
- ✓ Enemy score only shows if > 10%
- ✓ All text is user-friendly and encouraging

## Future Enhancements

- [ ] Add i18n support for multi-language summaries
- [ ] Cache compatibility explanations in browser storage
- [ ] Add visual comparison charts (radar/spider charts)
- [ ] Include specific examples from user profiles in explanations
- [ ] Add "why this matters" tooltips for each dimension
- [ ] A/B test different summary tone/styles
- [ ] Add share functionality for compatibility results

## Security Considerations

- ✓ Endpoint requires authentication (via `authService.getCurrentUser()`)
- ✓ Users can only view compatibility with users they have access to
- ✓ No sensitive personal data exposed in explanations
- ✓ Generic descriptions protect privacy

## Performance Notes

- Compatibility scores are cached in database
- Only recalculated when profiles are updated
- Frontend makes single API call per page load
- JSON parsing has fallbacks to prevent errors
- Default generation is lightweight (no heavy computation)
