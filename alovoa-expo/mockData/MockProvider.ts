/**
 * Mock API Provider for AURA Platform Testing
 * Intercepts API calls and returns mock data
 */

import { AxiosResponse } from 'axios';
import * as URL from '../URL';
import {
  MOCK_CURRENT_USER,
  MOCK_USERS,
  MOCK_USERS_MAP,
  BYPASS_CREDENTIALS,
} from './mockUsers';
import {
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
} from './mockAura';
import {
  SearchStageEnum,
  VideoDateStatus,
  VerificationStatus,
  VideoIntroStatus,
} from '../myTypes';

// Mock mode flag - set via environment or storage
let mockModeEnabled = false;
let mockCurrentUser = { ...MOCK_CURRENT_USER };

export const isMockMode = () => mockModeEnabled;
export const enableMockMode = () => { mockModeEnabled = true; };
export const disableMockMode = () => { mockModeEnabled = false; };
export const getMockCurrentUser = () => mockCurrentUser;

// Create a mock axios response
const mockResponse = <T>(data: T, status: number = 200): AxiosResponse<T> => ({
  data,
  status,
  statusText: 'OK',
  headers: {},
  config: {} as any,
  request: { responseURL: '' },
});

// Extract ID from URL pattern like /api/v1/thing/%s
const extractIdFromUrl = (url: string, pattern: string): string | null => {
  const patternParts = pattern.replace(URL.DOMAIN, '').split('%s');
  if (patternParts.length < 2) return null;

  const urlPath = url.replace(URL.DOMAIN, '');
  const startIdx = urlPath.indexOf(patternParts[0]) + patternParts[0].length;
  const endIdx = patternParts[1] ? urlPath.indexOf(patternParts[1], startIdx) : urlPath.length;

  if (startIdx === -1 || endIdx === -1) return null;
  return urlPath.substring(startIdx, endIdx === -1 ? undefined : endIdx);
};

/**
 * Handle mock authentication
 */
export const handleMockAuth = (email: string, password: string): boolean => {
  if (email === BYPASS_CREDENTIALS.email && password === BYPASS_CREDENTIALS.password) {
    enableMockMode();
    return true;
  }
  return false;
};

/**
 * Main mock fetch handler
 * Returns mock data for known endpoints
 */
export const mockFetch = async (
  url: string,
  method: string = 'get',
  data?: any
): Promise<AxiosResponse<any> | null> => {
  if (!mockModeEnabled) return null;

  console.log('[MockProvider] Intercepting:', method.toUpperCase(), url);

  // ========== PROFILE ENDPOINTS ==========
  if (url === URL.API_RESOURCE_YOUR_PROFILE) {
    return mockResponse({
      user: mockCurrentUser,
      genders: [
        { id: 1, text: 'Male' },
        { id: 2, text: 'Female' },
        { id: 3, text: 'Non-binary' },
      ],
      intentions: [
        { id: 1, text: 'Meet Friends' },
        { id: 2, text: 'Dating' },
        { id: 3, text: 'Casual' },
      ],
      imageMax: 6,
      isLegal: true,
      mediaMaxSize: 10000000,
      interestMaxSize: 10,
      referralsLeft: 5,
      showIntention: true,
      'settings.ignoreIntention': false,
    });
  }

  if (url.startsWith(URL.API_RESOURCE_PROFILE.replace('%s', ''))) {
    const uuid = extractIdFromUrl(url, URL.API_RESOURCE_PROFILE);
    const user = MOCK_USERS_MAP.get(uuid || '');
    return mockResponse({
      compatible: true,
      user: user || MOCK_USERS[0],
      currUserDto: mockCurrentUser,
      isLegal: true,
    });
  }

  // ========== SEARCH ENDPOINTS ==========
  if (url === URL.API_RESOURCE_SEARCH || url === URL.API_SEARCH) {
    return mockResponse({
      dto: {
        users: MOCK_USERS.filter(u => !u.hiddenByCurrentUser && !u.blockedByCurrentUser),
        message: '',
        stage: SearchStageEnum.NORMAL,
        global: false,
        incompatible: false,
      },
      currUser: mockCurrentUser,
    });
  }

  // ========== ASSESSMENT ENDPOINTS ==========
  if (url === URL.API_ASSESSMENT_QUESTIONS) {
    return mockResponse({
      questions: MOCK_ASSESSMENT_QUESTIONS,
      categories: ['BIG_FIVE', 'ATTACHMENT', 'VALUES', 'LIFESTYLE', 'DATING', 'COMMUNICATION', 'SEX_INTIMACY', 'FUTURE_GOALS'],
    });
  }

  if (url.startsWith(URL.API_ASSESSMENT_QUESTIONS_CATEGORY.replace('%s', ''))) {
    const category = extractIdFromUrl(url, URL.API_ASSESSMENT_QUESTIONS_CATEGORY);
    return mockResponse({
      questions: MOCK_ASSESSMENT_QUESTIONS.filter(q => q.category === category),
    });
  }

  if (url === URL.API_ASSESSMENT_PROFILE) {
    return mockResponse(MOCK_USER_ASSESSMENT_PROFILE);
  }

  if (url === URL.API_ASSESSMENT_PROGRESS) {
    return mockResponse({
      answeredCount: 45,
      totalRequired: 40,
      categoryProgress: {
        BIG_FIVE: 100,
        ATTACHMENT: 100,
        VALUES: 90,
        LIFESTYLE: 85,
        DATING: 80,
      },
    });
  }

  if (url === URL.API_ASSESSMENT_ANSWER || url === URL.API_ASSESSMENT_ANSWER_BULK) {
    return mockResponse({ success: true });
  }

  // ========== MATCHING / COMPATIBILITY ==========
  if (url === URL.API_MATCHING_MATCHES) {
    const matches = MOCK_USERS.slice(0, 5).map(user => ({
      user,
      score: MOCK_COMPATIBILITY_SCORES.get(user.uuid) || {
        overallScore: Math.floor(Math.random() * 30) + 65,
        personalityScore: Math.floor(Math.random() * 30) + 60,
        valuesScore: Math.floor(Math.random() * 30) + 60,
      },
      commonAnswers: Math.floor(Math.random() * 20) + 10,
      sharedInterests: user.commonInterests?.map(i => i.text) || [],
    }));
    return mockResponse({
      matches,
      dailyLimit: MOCK_DAILY_LIMITS,
    });
  }

  if (url.startsWith(URL.API_MATCHING_SCORE.replace('%s', ''))) {
    const userId = extractIdFromUrl(url, URL.API_MATCHING_SCORE);
    const score = MOCK_COMPATIBILITY_SCORES.get(userId || '') || {
      overallScore: 75,
      personalityScore: 70,
      valuesScore: 78,
      lifestyleScore: 72,
    };
    return mockResponse(score);
  }

  if (url.startsWith(URL.API_MATCHING_BREAKDOWN.replace('%s', ''))) {
    return mockResponse(MOCK_COMPATIBILITY_BREAKDOWN);
  }

  if (url === URL.API_MATCHING_DAILY_LIMIT) {
    return mockResponse(MOCK_DAILY_LIMITS);
  }

  // ========== VIDEO VERIFICATION ==========
  if (url === URL.API_VIDEO_VERIFICATION_STATUS) {
    return mockResponse({
      status: mockCurrentUser.verified ? VerificationStatus.VERIFIED : VerificationStatus.PENDING,
      faceMatchScore: 0.98,
      livenessScore: 0.99,
      deepfakeScore: 0.02,
      verifiedAt: mockCurrentUser.verified ? new Date() : null,
    });
  }

  if (url === URL.API_VIDEO_VERIFICATION_START) {
    return mockResponse({ sessionId: 'mock-session-' + Date.now() });
  }

  if (url === URL.API_VIDEO_VERIFICATION_UPLOAD || url === URL.API_VIDEO_VERIFICATION_CONFIRM) {
    mockCurrentUser = { ...mockCurrentUser, verified: true };
    return mockResponse({ success: true, status: VerificationStatus.VERIFIED });
  }

  // ========== VIDEO INTRO ==========
  if (url === URL.API_VIDEO_INTRO_STATUS) {
    return mockResponse({
      status: MOCK_VIDEO_INTRO.status,
      hasIntro: true,
      durationSeconds: MOCK_VIDEO_INTRO.durationSeconds,
      analysisComplete: MOCK_VIDEO_INTRO.analysisComplete,
    });
  }

  if (url === URL.API_VIDEO_INTRO_ANALYSIS) {
    return mockResponse({
      transcription: MOCK_VIDEO_INTRO.transcription,
      worldview: MOCK_VIDEO_INTRO.worldview,
      background: MOCK_VIDEO_INTRO.background,
      lifeStory: MOCK_VIDEO_INTRO.lifeStory,
      analysisScore: MOCK_VIDEO_INTRO.analysisScore,
    });
  }

  if (url === URL.API_VIDEO_INTRO_START) {
    return mockResponse({ uploadUrl: 'mock://upload', sessionId: 'mock-intro-' + Date.now() });
  }

  if (url === URL.API_VIDEO_INTRO_UPLOAD || url === URL.API_VIDEO_INTRO_CONFIRM) {
    return mockResponse({ success: true, status: VideoIntroStatus.ANALYZING });
  }

  // ========== VIDEO DATING ==========
  if (url === URL.API_VIDEO_DATE_LIST) {
    return mockResponse({
      upcomingDates: MOCK_VIDEO_DATES.filter(d => d.status === VideoDateStatus.SCHEDULED),
      pastDates: MOCK_VIDEO_DATES.filter(d => d.status === VideoDateStatus.COMPLETED),
      pendingRequests: MOCK_VIDEO_DATES.filter(d => d.status === VideoDateStatus.PROPOSED),
    });
  }

  if (url.startsWith(URL.API_VIDEO_DATE_PROPOSE.replace('%s', ''))) {
    return mockResponse({ success: true, videoDate: { id: Date.now(), status: VideoDateStatus.PROPOSED } });
  }

  if (url.startsWith(URL.API_VIDEO_DATE_JOIN.replace('%s', ''))) {
    const dateId = extractIdFromUrl(url, URL.API_VIDEO_DATE_JOIN);
    const videoDate = MOCK_VIDEO_DATES.find(d => d.id === Number(dateId)) || MOCK_VIDEO_DATES[0];
    return mockResponse({
      ...videoDate,
      roomId: 'mock-room-' + Date.now(),
      roomToken: 'mock-token',
      status: VideoDateStatus.IN_PROGRESS,
    });
  }

  // ========== MATCH WINDOWS ==========
  if (url === URL.API_MATCH_WINDOW_LIST) {
    return mockResponse(MOCK_MATCH_WINDOWS);
  }

  if (url === URL.API_MATCH_WINDOW_CURRENT) {
    return mockResponse(MOCK_MATCH_WINDOWS.find(mw => mw.status === 'ACTIVE') || null);
  }

  // ========== CALENDAR ==========
  if (url === URL.API_CALENDAR_AVAILABILITY) {
    return mockResponse(MOCK_CALENDAR_AVAILABILITY);
  }

  if (url === URL.API_CALENDAR_UPDATE) {
    return mockResponse({ success: true });
  }

  // ========== SCAFFOLDING / INTAKE ==========
  if (url === URL.API_SCAFFOLDING_PROFILE) {
    return mockResponse({
      scaffolded: MOCK_SCAFFOLDED_PROFILE,
      videoIntro: MOCK_VIDEO_INTRO,
    });
  }

  if (url === URL.API_SCAFFOLDING_CONFIRM) {
    return mockResponse({ success: true, profileCreated: true });
  }

  if (url === URL.API_INTAKE_PROGRESS) {
    return mockResponse(MOCK_INTAKE_PROGRESS);
  }

  // ========== REPUTATION ==========
  if (url === URL.API_REPUTATION_SCORE) {
    return mockResponse(MOCK_REPUTATION_SCORE);
  }

  if (url === URL.API_REPUTATION_HISTORY) {
    return mockResponse(MOCK_REPUTATION_HISTORY);
  }

  if (url === URL.API_REPUTATION_BADGES) {
    return mockResponse(MOCK_REPUTATION_SCORE.badges);
  }

  // ========== CHAT ==========
  if (url === URL.API_RESOURCE_CHATS) {
    return mockResponse({
      user: mockCurrentUser,
      conversations: MOCK_CONVERSATIONS,
    });
  }

  if (url.startsWith(URL.API_RESOURCE_CHATS_DETAIL.replace('%s', ''))) {
    const convoId = extractIdFromUrl(url, URL.API_RESOURCE_CHATS_DETAIL);
    const convo = MOCK_CONVERSATIONS.find(c => c.id === Number(convoId));
    const partner = convo ? MOCK_USERS_MAP.get(convo.uuid) : MOCK_USERS[0];
    return mockResponse({
      user: mockCurrentUser,
      convoId: Number(convoId),
      partner,
    });
  }

  // ========== LIKES ==========
  if (url === URL.API_RESOURCE_USER_LIKED) {
    return mockResponse({
      users: MOCK_USERS.filter(u => u.likedByCurrentUser),
      user: mockCurrentUser,
    });
  }

  if (url === URL.API_RESOURCE_ALERTS) {
    const notifications = MOCK_USERS.filter(u => u.likesCurrentUser).map((u, i) => ({
      id: i + 1,
      date: new Date(Date.now() - i * 24 * 60 * 60 * 1000),
      message: `${u.firstName} liked you!`,
      userFromDto: u,
    }));
    return mockResponse({
      notifications,
      user: mockCurrentUser,
    });
  }

  // ========== USER ACTIONS ==========
  if (url.startsWith(URL.USER_LIKE.replace('%s', ''))) {
    const userId = extractIdFromUrl(url, URL.USER_LIKE);
    const user = MOCK_USERS_MAP.get(userId || '');
    if (user) {
      user.likedByCurrentUser = true;
      // Check for mutual match
      if (user.likesCurrentUser) {
        return mockResponse({ match: true, user });
      }
    }
    return mockResponse({ match: false });
  }

  if (url.startsWith(URL.USER_HIDE.replace('%s', ''))) {
    const userId = extractIdFromUrl(url, URL.USER_HIDE);
    const user = MOCK_USERS_MAP.get(userId || '');
    if (user) user.hiddenByCurrentUser = true;
    return mockResponse({ success: true });
  }

  if (url.startsWith(URL.USER_BLOCK.replace('%s', ''))) {
    const userId = extractIdFromUrl(url, URL.USER_BLOCK);
    const user = MOCK_USERS_MAP.get(userId || '');
    if (user) user.blockedByCurrentUser = true;
    return mockResponse({ success: true });
  }

  // ========== REPORTING ==========
  if (url === URL.API_REPORT_CREATE) {
    return mockResponse({ success: true, id: Date.now() });
  }

  // ========== POLITICAL ASSESSMENT ==========
  if (url === URL.API_POLITICAL_QUESTIONS) {
    return mockResponse({
      questions: [
        { id: 'pol-1', text: 'Where does wealth primarily come from?', options: ['Labor', 'Capital', 'Both equally'] },
        { id: 'pol-2', text: 'Should billionaires exist?', options: ['No', 'Maybe with limits', 'Yes'] },
        { id: 'pol-3', text: 'Healthcare should be...', options: ['Universal single-payer', 'Mixed public/private', 'Fully private'] },
      ],
    });
  }

  if (url === URL.API_POLITICAL_PROFILE || url === URL.API_POLITICAL_COMPASS) {
    return mockResponse(mockCurrentUser.politicalAssessment);
  }

  // ========== LOCATION ==========
  if (url === URL.API_LOCATION_CENTROID) {
    return mockResponse({
      centroidId: 'sf-downtown',
      displayName: 'San Francisco, CA',
      travelTimeMinutes: 0,
    });
  }

  // ========== DEFAULT / FALLBACK ==========
  console.log('[MockProvider] No handler for:', url);
  return null;
};

export default {
  isMockMode,
  enableMockMode,
  disableMockMode,
  getMockCurrentUser,
  handleMockAuth,
  mockFetch,
  BYPASS_CREDENTIALS,
};
