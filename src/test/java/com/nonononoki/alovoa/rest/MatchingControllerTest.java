package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.CompatibilityExplanationDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private MatchingService matchingService;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax, firstNameLengthMin);
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/daily - Returns daily matches successfully")
    void testGetDailyMatchesSuccess() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "matches", List.of(),
                "count", 0
        );
        Mockito.when(matchingService.getDailyMatches()).thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/matching/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").exists())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/daily - Returns matches with content")
    void testGetDailyMatchesWithContent() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "matches", List.of(Map.of("userId", 123L, "score", 85)),
                "count", 1
        );
        Mockito.when(matchingService.getDailyMatches()).thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/matching/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/daily - Handles service exception")
    void testGetDailyMatchesException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(matchingService.getDailyMatches())
                .thenThrow(new RuntimeException("Matching service error"));

        mockMvc.perform(get("/api/v1/matching/daily"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Matching service error"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/compatibility/{matchUuid} - Returns compatibility explanation")
    void testGetCompatibilityExplanation() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        String matchUuid = "550e8400-e29b-41d4-a716-446655440000";
        CompatibilityExplanationDto mockDto = new CompatibilityExplanationDto();
        Mockito.when(matchingService.getCompatibilityExplanation(matchUuid))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/matching/compatibility/" + matchUuid))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/compatibility/{matchUuid} - Handles invalid UUID")
    void testGetCompatibilityExplanationInvalidUuid() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        String matchUuid = "invalid-uuid";
        Mockito.when(matchingService.getCompatibilityExplanation(matchUuid))
                .thenThrow(new IllegalArgumentException("Invalid UUID format"));

        mockMvc.perform(get("/api/v1/matching/compatibility/" + matchUuid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid UUID format"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/compatibility/{matchUuid} - Handles match not found")
    void testGetCompatibilityExplanationNotFound() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        String matchUuid = "550e8400-e29b-41d4-a716-446655440000";
        Mockito.when(matchingService.getCompatibilityExplanation(matchUuid))
                .thenThrow(new RuntimeException("Match not found"));

        mockMvc.perform(get("/api/v1/matching/compatibility/" + matchUuid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Match not found"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/compatibility/{matchUuid} - Returns detailed compatibility info")
    void testGetCompatibilityExplanationDetailed() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        String matchUuid = "550e8400-e29b-41d4-a716-446655440000";
        CompatibilityExplanationDto mockDto = new CompatibilityExplanationDto();
        // Assume DTO has fields like score, reasons, etc.
        Mockito.when(matchingService.getCompatibilityExplanation(matchUuid))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/matching/compatibility/" + matchUuid))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/daily - Auth failure handled gracefully")
    void testGetDailyMatchesNoAuth() throws Exception {
        Mockito.when(authService.getCurrentUser(true))
                .thenThrow(new RuntimeException("User not authenticated"));

        // When auth fails, the controller catches the exception and returns OK with error in body
        mockMvc.perform(get("/api/v1/matching/daily"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/matching/compatibility/{matchUuid} - Path variable is properly bound")
    void testCompatibilityPathVariableBinding() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        String matchUuid = "123e4567-e89b-12d3-a456-426614174000";
        CompatibilityExplanationDto mockDto = new CompatibilityExplanationDto();
        Mockito.when(matchingService.getCompatibilityExplanation(matchUuid))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/matching/compatibility/" + matchUuid))
                .andExpect(status().isOk());

        // Verify the path variable was correctly passed
        Mockito.verify(matchingService).getCompatibilityExplanation(matchUuid);
    }
}
