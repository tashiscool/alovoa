/**
 * Mock AURA Platform Data
 * Assessment questions, video dates, reputation, compatibility, etc.
 */

import {
  AssessmentQuestion,
  AssessmentCategory,
  AssessmentOption,
  QuestionImportance,
  UserAssessmentProfile,
  AttachmentStyle,
  UserQuestionAnswer,
  CompatibilityScore,
  CompatibilityBreakdown,
  CompatibilityDimension,
  VideoDate,
  VideoDateStatus,
  VideoDateFeedback,
  MatchWindow,
  MatchWindowStatus,
  UserCalendarAvailability,
  UserReputationScore,
  TrustLevel,
  ReputationBadge,
  BadgeType,
  ReputationHistoryItem,
  IntakeProgressDto,
  IntakeStep,
  UserDailyMatchLimit,
  ScaffoldedProfileDto,
  AttachmentInference,
  UserVideoIntroduction,
  VideoIntroStatus,
  ConversationDto,
  MessageDto,
} from '../myTypes';
import { MOCK_USERS, MOCK_CURRENT_USER } from './mockUsers';

// ============================================
// ASSESSMENT QUESTIONS
// ============================================

export const MOCK_ASSESSMENT_QUESTIONS: AssessmentQuestion[] = [
  // Big Five - Openness
  {
    id: 'bf-open-1',
    text: 'How do you feel about trying new and unusual experiences?',
    category: AssessmentCategory.BIG_FIVE,
    subcategory: 'Openness',
    options: [
      { id: 'bf-open-1-a', text: 'I actively seek out new experiences', value: 100, traits: { openness: 100 } },
      { id: 'bf-open-1-b', text: 'I enjoy them when they come my way', value: 75, traits: { openness: 75 } },
      { id: 'bf-open-1-c', text: 'I\'m comfortable with my routine', value: 50, traits: { openness: 50 } },
      { id: 'bf-open-1-d', text: 'I prefer familiar experiences', value: 25, traits: { openness: 25 } },
    ],
    weight: 1.0,
    importance: QuestionImportance.SOMEWHAT,
  },
  {
    id: 'bf-open-2',
    text: 'When it comes to art and creativity, you:',
    category: AssessmentCategory.BIG_FIVE,
    subcategory: 'Openness',
    options: [
      { id: 'bf-open-2-a', text: 'Are deeply moved by creative works', value: 100, traits: { openness: 100 } },
      { id: 'bf-open-2-b', text: 'Appreciate art but don\'t need it daily', value: 60, traits: { openness: 60 } },
      { id: 'bf-open-2-c', text: 'Don\'t think much about artistic things', value: 30, traits: { openness: 30 } },
    ],
    weight: 0.8,
  },

  // Big Five - Conscientiousness
  {
    id: 'bf-consc-1',
    text: 'How organized is your living space?',
    category: AssessmentCategory.BIG_FIVE,
    subcategory: 'Conscientiousness',
    options: [
      { id: 'bf-consc-1-a', text: 'Everything has its place', value: 100, traits: { conscientiousness: 100 } },
      { id: 'bf-consc-1-b', text: 'Mostly tidy with some clutter', value: 70, traits: { conscientiousness: 70 } },
      { id: 'bf-consc-1-c', text: 'Organized chaos - I know where things are', value: 45, traits: { conscientiousness: 45 } },
      { id: 'bf-consc-1-d', text: 'Pretty messy, but it works for me', value: 20, traits: { conscientiousness: 20 } },
    ],
    weight: 0.9,
  },

  // Big Five - Extraversion
  {
    id: 'bf-extra-1',
    text: 'After a long week, your ideal weekend involves:',
    category: AssessmentCategory.BIG_FIVE,
    subcategory: 'Extraversion',
    options: [
      { id: 'bf-extra-1-a', text: 'Going out with friends and meeting new people', value: 100, traits: { extraversion: 100 } },
      { id: 'bf-extra-1-b', text: 'Small gathering with close friends', value: 65, traits: { extraversion: 65 } },
      { id: 'bf-extra-1-c', text: 'Quiet time at home, maybe one outing', value: 40, traits: { extraversion: 40 } },
      { id: 'bf-extra-1-d', text: 'Recharging alone with minimal socializing', value: 15, traits: { extraversion: 15 } },
    ],
    weight: 1.0,
  },

  // Attachment Style
  {
    id: 'attach-1',
    text: 'When your partner hasn\'t texted back in a few hours, you typically:',
    category: AssessmentCategory.ATTACHMENT,
    options: [
      { id: 'attach-1-a', text: 'Assume they\'re busy and continue your day', value: 0, traits: { attachmentAnxiety: 10 } },
      { id: 'attach-1-b', text: 'Feel a bit curious but not worried', value: 25, traits: { attachmentAnxiety: 30 } },
      { id: 'attach-1-c', text: 'Start wondering if something\'s wrong', value: 60, traits: { attachmentAnxiety: 60 } },
      { id: 'attach-1-d', text: 'Feel anxious and need to reach out', value: 90, traits: { attachmentAnxiety: 90 } },
    ],
    weight: 1.2,
    importance: QuestionImportance.VERY,
  },
  {
    id: 'attach-2',
    text: 'How comfortable are you with emotional intimacy?',
    category: AssessmentCategory.ATTACHMENT,
    options: [
      { id: 'attach-2-a', text: 'I crave deep emotional connection', value: 0, traits: { attachmentAvoidance: 10 } },
      { id: 'attach-2-b', text: 'I enjoy it with the right person', value: 25, traits: { attachmentAvoidance: 25 } },
      { id: 'attach-2-c', text: 'It takes me a while to open up', value: 55, traits: { attachmentAvoidance: 55 } },
      { id: 'attach-2-d', text: 'I prefer to keep some emotional distance', value: 85, traits: { attachmentAvoidance: 85 } },
    ],
    weight: 1.2,
    importance: QuestionImportance.VERY,
  },

  // Values
  {
    id: 'val-1',
    text: 'Which statement best describes your view on wealth inequality?',
    category: AssessmentCategory.VALUES,
    options: [
      { id: 'val-1-a', text: 'It\'s a systemic problem that needs addressing', value: 100, traits: { progressive: 100 } },
      { id: 'val-1-b', text: 'Some inequality is natural but extremes are problematic', value: 65, traits: { progressive: 65 } },
      { id: 'val-1-c', text: 'People generally earn what they deserve', value: 30, traits: { progressive: 30 } },
    ],
    weight: 1.0,
    importance: QuestionImportance.SOMEWHAT,
  },
  {
    id: 'val-2',
    text: 'How important is it that household chores are split equally?',
    category: AssessmentCategory.VALUES,
    options: [
      { id: 'val-2-a', text: 'Essential - 50/50 or bust', value: 100, traits: { egalitarian: 100 } },
      { id: 'val-2-b', text: 'Important but can be flexible based on schedules', value: 75, traits: { egalitarian: 75 } },
      { id: 'val-2-c', text: 'Should be based on who\'s better at what', value: 50, traits: { egalitarian: 50 } },
      { id: 'val-2-d', text: 'Traditional roles work fine', value: 20, traits: { egalitarian: 20 } },
    ],
    weight: 1.0,
    importance: QuestionImportance.VERY,
    dealbreaker: true,
  },

  // Lifestyle
  {
    id: 'life-1',
    text: 'Your ideal Friday night looks like:',
    category: AssessmentCategory.LIFESTYLE,
    options: [
      { id: 'life-1-a', text: 'House party or going out', value: 100, traits: { socialOrientation: 100 } },
      { id: 'life-1-b', text: 'Dinner with friends', value: 75, traits: { socialOrientation: 75 } },
      { id: 'life-1-c', text: 'Date night or small hangout', value: 50, traits: { socialOrientation: 50 } },
      { id: 'life-1-d', text: 'Netflix and chill at home', value: 25, traits: { socialOrientation: 25 } },
    ],
    weight: 0.8,
  },
  {
    id: 'life-2',
    text: 'How often do you exercise?',
    category: AssessmentCategory.LIFESTYLE,
    options: [
      { id: 'life-2-a', text: 'Daily or almost daily', value: 100, traits: { healthFocus: 100 } },
      { id: 'life-2-b', text: '3-4 times per week', value: 75, traits: { healthFocus: 75 } },
      { id: 'life-2-c', text: '1-2 times per week', value: 50, traits: { healthFocus: 50 } },
      { id: 'life-2-d', text: 'Rarely or never', value: 15, traits: { healthFocus: 15 } },
    ],
    weight: 0.7,
  },

  // Dating
  {
    id: 'date-1',
    text: 'How soon do you like to meet someone in person after matching?',
    category: AssessmentCategory.DATING,
    options: [
      { id: 'date-1-a', text: 'ASAP - within a few days', value: 90 },
      { id: 'date-1-b', text: 'Within a week or two', value: 70 },
      { id: 'date-1-c', text: 'After getting to know them via chat first', value: 40 },
      { id: 'date-1-d', text: 'No rush - when it feels right', value: 20 },
    ],
    weight: 0.9,
  },

  // Communication
  {
    id: 'comm-1',
    text: 'When dealing with conflict in a relationship, you prefer to:',
    category: AssessmentCategory.COMMUNICATION,
    options: [
      { id: 'comm-1-a', text: 'Address it immediately and directly', value: 100, traits: { communicationDirectness: 100 } },
      { id: 'comm-1-b', text: 'Take some time to think, then discuss', value: 70, traits: { communicationDirectness: 70 } },
      { id: 'comm-1-c', text: 'Drop hints and hope they get it', value: 35, traits: { communicationDirectness: 35 } },
      { id: 'comm-1-d', text: 'Avoid conflict when possible', value: 10, traits: { communicationDirectness: 10 } },
    ],
    weight: 1.1,
    importance: QuestionImportance.VERY,
  },

  // Sex & Intimacy
  {
    id: 'sex-1',
    text: 'How important is physical intimacy in a relationship?',
    category: AssessmentCategory.SEX_INTIMACY,
    options: [
      { id: 'sex-1-a', text: 'Essential - very important to me', value: 100 },
      { id: 'sex-1-b', text: 'Important but not everything', value: 70 },
      { id: 'sex-1-c', text: 'Nice but not a priority', value: 40 },
      { id: 'sex-1-d', text: 'Not important at all', value: 10 },
    ],
    weight: 1.0,
    importance: QuestionImportance.VERY,
  },

  // Future Goals
  {
    id: 'future-1',
    text: 'Do you want children?',
    category: AssessmentCategory.FUTURE_GOALS,
    options: [
      { id: 'future-1-a', text: 'Definitely yes', value: 100 },
      { id: 'future-1-b', text: 'Leaning towards yes', value: 75 },
      { id: 'future-1-c', text: 'Not sure / open to it', value: 50 },
      { id: 'future-1-d', text: 'Leaning towards no', value: 25 },
      { id: 'future-1-e', text: 'Definitely no', value: 0 },
    ],
    weight: 1.5,
    importance: QuestionImportance.MANDATORY,
    dealbreaker: true,
  },
];

// ============================================
// USER ASSESSMENT PROFILE
// ============================================

export const MOCK_USER_ASSESSMENT_PROFILE: UserAssessmentProfile = {
  id: 1,
  userId: 1,
  openness: 85,
  conscientiousness: 70,
  extraversion: 65,
  agreeableness: 75,
  neuroticism: 35,
  attachmentAnxiety: 25,
  attachmentAvoidance: 20,
  attachmentStyle: AttachmentStyle.SECURE,
  progressive: 78,
  egalitarian: 82,
  socialOrientation: 65,
  healthFocus: 70,
  workLifeBalance: 60,
  financialAmbition: 55,
  questionsAnswered: 45,
  lastUpdated: new Date(),
  profileComplete: true,
};

// ============================================
// COMPATIBILITY SCORES
// ============================================

export const MOCK_COMPATIBILITY_SCORES: Map<string, CompatibilityScore> = new Map([
  ['match-emma-001', {
    id: 1,
    user1Id: 1,
    user2Id: 2,
    overallScore: 92,
    personalityScore: 88,
    valuesScore: 94,
    lifestyleScore: 85,
    attachmentScore: 95,
    interestsScore: 90,
    dealbreakersScore: 100,
    politicalScore: 92,
    questionsCompared: 35,
    matchPercentage: 89,
    conflictCount: 2,
    calculatedAt: new Date(),
    stale: false,
  }],
  ['match-sophia-002', {
    id: 2,
    user1Id: 1,
    user2Id: 3,
    overallScore: 78,
    personalityScore: 75,
    valuesScore: 82,
    lifestyleScore: 70,
    attachmentScore: 85,
    interestsScore: 75,
    dealbreakersScore: 100,
    politicalScore: 80,
    questionsCompared: 28,
    matchPercentage: 76,
    conflictCount: 4,
    calculatedAt: new Date(),
    stale: false,
  }],
  ['match-olivia-003', {
    id: 3,
    user1Id: 1,
    user2Id: 4,
    overallScore: 95,
    personalityScore: 92,
    valuesScore: 96,
    lifestyleScore: 90,
    attachmentScore: 98,
    interestsScore: 95,
    dealbreakersScore: 100,
    politicalScore: 94,
    questionsCompared: 42,
    matchPercentage: 93,
    conflictCount: 1,
    calculatedAt: new Date(),
    stale: false,
  }],
]);

export const MOCK_COMPATIBILITY_BREAKDOWN: CompatibilityBreakdown = {
  overallScore: 92,
  dimensions: [
    { dimension: 'Personality', score: 88, weight: 0.25, details: [{ label: 'Both introverted-leaning', compatible: true }] },
    { dimension: 'Values', score: 94, weight: 0.30, details: [{ label: 'Similar political views', compatible: true }] },
    { dimension: 'Lifestyle', score: 85, weight: 0.20, details: [{ label: 'Both enjoy hiking', compatible: true }] },
    { dimension: 'Attachment', score: 95, weight: 0.25, details: [{ label: 'Both secure attachment', compatible: true }] },
  ],
  dealbreakers: [],
  sharedQuestions: [
    { questionText: 'Do you want children?', yourAnswer: 'Leaning towards yes', theirAnswer: 'Leaning towards yes' },
    { questionText: 'How important is physical intimacy?', yourAnswer: 'Very important', theirAnswer: 'Important but not everything' },
  ],
};

// ============================================
// VIDEO DATES
// ============================================

export const MOCK_VIDEO_DATES: VideoDate[] = [
  {
    id: 1,
    date1UserId: 1,
    date2UserId: 7,
    partnerId: 7,
    partnerName: 'Ava',
    partnerProfilePicture: MOCK_USERS[6].profilePicture,
    isInitiator: true,
    scheduledAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000), // 2 days from now
    durationMinutes: 15,
    status: VideoDateStatus.SCHEDULED,
    createdAt: new Date(),
  },
  {
    id: 2,
    date1UserId: 3,
    date2UserId: 1,
    partnerId: 3,
    partnerName: 'Olivia',
    partnerProfilePicture: MOCK_USERS[2].profilePicture,
    isInitiator: false,
    scheduledAt: undefined,
    durationMinutes: 15,
    status: VideoDateStatus.PROPOSED,
    createdAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000),
  },
  {
    id: 3,
    date1UserId: 1,
    date2UserId: 6,
    partnerId: 6,
    partnerName: 'Isabella',
    partnerProfilePicture: MOCK_USERS[5].profilePicture,
    isInitiator: true,
    scheduledAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000),
    durationMinutes: 20,
    status: VideoDateStatus.COMPLETED,
    startedAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000),
    endedAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000 + 18 * 60 * 1000),
    actualDurationSeconds: 18 * 60,
    feedbackGiven: true,
    date1Feedback: {
      rating: 4,
      wouldMeetAgain: true,
      chemistry: 4,
      conversation: 5,
      connectionFelt: 'Good conversation, want to meet IRL',
      submittedAt: new Date(),
    },
    createdAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000),
  },
];

// ============================================
// MATCH WINDOWS
// ============================================

export const MOCK_MATCH_WINDOWS: MatchWindow[] = [
  {
    id: 1,
    userId: 1,
    windowStart: new Date(),
    windowEnd: new Date(Date.now() + 24 * 60 * 60 * 1000),
    matchedUserId: 2,
    matchedUser: MOCK_USERS[0],
    compatibilityScore: 92,
    matchReason: 'High compatibility in values and lifestyle',
    status: MatchWindowStatus.ACTIVE,
    userResponded: false,
    matchResponded: false,
    conversationStarted: false,
    dateScheduled: false,
    createdAt: new Date(),
    expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
  },
  {
    id: 2,
    userId: 1,
    windowStart: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
    windowEnd: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000),
    matchedUserId: 7,
    matchedUser: MOCK_USERS[6],
    compatibilityScore: 88,
    status: MatchWindowStatus.MATCHED,
    userResponded: true,
    matchResponded: true,
    conversationStarted: true,
    dateScheduled: true,
    createdAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
    expiresAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
  },
];

// ============================================
// CALENDAR AVAILABILITY
// ============================================

export const MOCK_CALENDAR_AVAILABILITY: UserCalendarAvailability = {
  userId: 1,
  timezone: 'America/Los_Angeles',
  videoDatesEnabled: true,
  minimumNoticeHours: 24,
  days: {
    monday: { enabled: true, slots: [{ start: '18:00', end: '21:00' }] },
    tuesday: { enabled: true, slots: [{ start: '18:00', end: '21:00' }] },
    wednesday: { enabled: true, slots: [{ start: '18:00', end: '21:00' }] },
    thursday: { enabled: true, slots: [{ start: '18:00', end: '21:00' }] },
    friday: { enabled: true, slots: [{ start: '17:00', end: '22:00' }] },
    saturday: { enabled: true, slots: [{ start: '10:00', end: '22:00' }] },
    sunday: { enabled: true, slots: [{ start: '10:00', end: '20:00' }] },
  },
};

// ============================================
// REPUTATION
// ============================================

export const MOCK_REPUTATION_SCORE: UserReputationScore = {
  id: 1,
  userId: 1,
  overallScore: 87,
  totalScore: 87,
  responseRate: 92,
  messageQuality: 85,
  dateHonor: 100,
  profileAccuracy: 90,
  communityStanding: 85,
  verificationPoints: 25,
  responsePoints: 20,
  reliabilityPoints: 22,
  feedbackPoints: 12,
  tenurePoints: 8,
  totalInteractions: 45,
  positiveInteractions: 42,
  negativeInteractions: 1,
  reportsReceived: 0,
  reportsUpheld: 0,
  badges: [
    { type: BadgeType.VERIFIED, name: 'Verified', description: 'Identity verified via video' },
    { type: BadgeType.RESPONSIVE, name: 'Quick Responder', description: 'Typically replies within hours' },
    { type: BadgeType.DATE_KEEPER, name: 'Date Keeper', description: 'Shows up to scheduled dates' },
  ],
  trustLevel: TrustLevel.TRUSTED,
  lastUpdated: new Date(),
};

export const MOCK_REPUTATION_HISTORY: ReputationHistoryItem[] = [
  { id: 1, type: 'VIDEO_VERIFIED', points: 25, description: 'Completed video verification', createdAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString() },
  { id: 2, type: 'DATE_COMPLETED', points: 10, description: 'Completed video date with Ava', createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString() },
  { id: 3, type: 'POSITIVE_FEEDBACK', points: 5, description: 'Received positive feedback', createdAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString() },
  { id: 4, type: 'QUICK_RESPONSE', points: 2, description: 'Quick message response', createdAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString() },
];

// ============================================
// INTAKE PROGRESS
// ============================================

export const MOCK_INTAKE_PROGRESS: IntakeProgressDto = {
  userId: 1,
  currentStep: IntakeStep.COMPLETE,
  stepsCompleted: [
    IntakeStep.WELCOME,
    IntakeStep.BASIC_PROFILE,
    IntakeStep.PHOTOS,
    IntakeStep.VIDEO_INTRO,
    IntakeStep.ASSESSMENT,
    IntakeStep.POLITICAL,
    IntakeStep.VERIFICATION,
    IntakeStep.COMPLETE,
  ],
  basicProfileComplete: true,
  photoUploaded: true,
  videoIntroComplete: true,
  assessmentStarted: true,
  assessmentComplete: true,
  politicalAssessmentComplete: true,
  verificationComplete: true,
  questionsAnswered: 45,
  totalQuestionsRequired: 40,
  estimatedTimeRemaining: 0,
  lastEncouragement: 'Congratulations! Your profile is complete!',
  nextMilestone: 'Start matching!',
};

// ============================================
// DAILY MATCH LIMITS
// ============================================

export const MOCK_DAILY_LIMITS: UserDailyMatchLimit = {
  userId: 1,
  matchesRemaining: 8,
  likesRemaining: 15,
  messagesRemaining: 50,
  resetAt: new Date(Date.now() + 12 * 60 * 60 * 1000),
  isPremium: false,
  remaining: 8,
  limit: 10,
};

// ============================================
// SCAFFOLDED PROFILE (Video Analysis Result)
// ============================================

export const MOCK_SCAFFOLDED_PROFILE: ScaffoldedProfileDto = {
  bigFive: {
    openness: { score: 85, confidence: 0.82 },
    conscientiousness: { score: 70, confidence: 0.78 },
    extraversion: { score: 65, confidence: 0.85 },
    agreeableness: { score: 75, confidence: 0.80 },
    neuroticism: { score: 35, confidence: 0.75 },
  },
  values: {
    progressive: { score: 78, confidence: 0.72 },
    egalitarian: { score: 82, confidence: 0.68 },
  },
  lifestyle: {
    social: { score: 65, confidence: 0.80 },
    health: { score: 70, confidence: 0.65 },
    workLife: { score: 60, confidence: 0.55 },
    finance: { score: 55, confidence: 0.50 },
  },
  attachment: {
    anxiety: { score: 25, confidence: 0.78 },
    avoidance: { score: 20, confidence: 0.75 },
    style: AttachmentStyle.SECURE,
    styleConfidence: 0.82,
  },
  suggestedDealbreakers: [
    { category: 'Children', item: 'Must want kids', confidence: 0.75, reason: 'Mentioned wanting a family' },
    { category: 'Politics', item: 'Progressive values', confidence: 0.68, reason: 'Expressed social views' },
  ],
  lowConfidenceAreas: ['finance', 'workLife'],
  reviewed: false,
  confirmed: false,
};

export const MOCK_VIDEO_INTRO: UserVideoIntroduction = {
  id: 1,
  userId: 1,
  s3Key: 'video-intro/test-user-001.mp4',
  durationSeconds: 90,
  status: VideoIntroStatus.COMPLETE,
  transcription: 'Hi! I\'m a software engineer who loves hiking, coffee, and meeting interesting people...',
  worldview: 'Optimistic about technology\'s potential to improve lives while maintaining healthy skepticism about its misuse.',
  background: 'Grew up in the Bay Area, studied computer science, now working at a startup.',
  lifeStory: 'My journey has been about finding balance between career ambitions and personal fulfillment.',
  analysisScore: 85,
  analysisComplete: true,
  inferredOpenness: 85,
  inferredConscientiousness: 70,
  inferredExtraversion: 65,
  inferredAgreeableness: 75,
  inferredNeuroticism: 35,
  inferredAttachmentAnxiety: 25,
  inferredAttachmentAvoidance: 20,
  inferredAttachmentStyle: AttachmentStyle.SECURE,
  overallInferenceConfidence: 0.78,
  inferenceReviewed: false,
  inferenceConfirmed: false,
  createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
  analyzedAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000 + 5 * 60 * 1000),
};

// ============================================
// CONVERSATIONS (for messaging)
// ============================================

export const MOCK_CONVERSATIONS: ConversationDto[] = [
  {
    id: 1,
    lastUpdated: new Date(Date.now() - 1 * 60 * 60 * 1000),
    userName: 'Ava',
    userProfilePicture: MOCK_USERS[6].profilePicture,
    lastMessage: {
      id: 101,
      content: 'Looking forward to our video date!',
      date: new Date(Date.now() - 1 * 60 * 60 * 1000),
      from: false,
      allowedFormatting: false,
    },
    uuid: 'match-ava-007',
    read: true,
  },
  {
    id: 2,
    lastUpdated: new Date(Date.now() - 5 * 60 * 60 * 1000),
    userName: 'Isabella',
    userProfilePicture: MOCK_USERS[5].profilePicture,
    lastMessage: {
      id: 102,
      content: 'That was such a great conversation yesterday!',
      date: new Date(Date.now() - 5 * 60 * 60 * 1000),
      from: false,
      allowedFormatting: false,
    },
    uuid: 'match-isabella-006',
    read: false,
  },
  {
    id: 3,
    lastUpdated: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
    userName: 'Emma',
    userProfilePicture: MOCK_USERS[0].profilePicture,
    lastMessage: {
      id: 103,
      content: 'Hey! I noticed we have a lot in common',
      date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
      from: true,
      allowedFormatting: false,
    },
    uuid: 'match-emma-001',
    read: true,
  },
];

export const MOCK_MESSAGES: Map<number, MessageDto[]> = new Map([
  [1, [
    { id: 1, content: 'Hey! We matched!', date: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000), from: true, allowedFormatting: false },
    { id: 2, content: 'Hi! Yes, I saw your profile - love that you\'re into hiking!', date: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000 + 30 * 60 * 1000), from: false, allowedFormatting: false },
    { id: 3, content: 'Yes! Any favorite trails?', date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000), from: true, allowedFormatting: false },
    { id: 4, content: 'Lands End is my go-to! Have you tried it?', date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000 + 15 * 60 * 1000), from: false, allowedFormatting: false },
    { id: 5, content: 'Love it! Want to schedule a video date?', date: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000), from: true, allowedFormatting: false },
    { id: 6, content: 'That sounds great! I\'m free this weekend.', date: new Date(Date.now() - 12 * 60 * 60 * 1000), from: false, allowedFormatting: false },
    { id: 7, content: 'Looking forward to our video date!', date: new Date(Date.now() - 1 * 60 * 60 * 1000), from: false, allowedFormatting: false },
  ]],
  [2, [
    { id: 10, content: 'Great chatting with you earlier!', date: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000), from: true, allowedFormatting: false },
    { id: 11, content: 'Same! Your book recommendations were perfect', date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000), from: false, allowedFormatting: false },
    { id: 12, content: 'That was such a great conversation yesterday!', date: new Date(Date.now() - 5 * 60 * 60 * 1000), from: false, allowedFormatting: false },
  ]],
]);

export default {
  MOCK_ASSESSMENT_QUESTIONS,
  MOCK_USER_ASSESSMENT_PROFILE,
  MOCK_COMPATIBILITY_SCORES,
  MOCK_COMPATIBILITY_BREAKDOWN,
  MOCK_VIDEO_DATES,
  MOCK_MATCH_WINDOWS,
  MOCK_CALENDAR_AVAILABILITY,
  MOCK_REPUTATION_SCORE,
  MOCK_REPUTATION_HISTORY,
  MOCK_INTAKE_PROGRESS,
  MOCK_DAILY_LIMITS,
  MOCK_SCAFFOLDED_PROFILE,
  MOCK_VIDEO_INTRO,
  MOCK_CONVERSATIONS,
  MOCK_MESSAGES,
};
