package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VideoDateControllerTest {

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
    private VideoDateService videoDateService;

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
    @DisplayName("POST /api/v1/video-date/propose - Proposes video date successfully")
    void testProposeVideoDate() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Date proposedTime = new Date();
        Map<String, Object> mockResult = Map.of(
                "id", 1L,
                "status", "PROPOSED",
                "proposedTime", proposedTime
        );
        Mockito.when(videoDateService.proposeVideoDate(anyLong(), any(Date.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/video-date/propose")
                        .param("conversationId", "1")
                        .param("proposedTime", "2024-12-31T18:00:00.000+00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PROPOSED"));

        Mockito.verify(videoDateService).proposeVideoDate(eq(1L), any(Date.class));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/propose - Handles exception when proposing")
    void testProposeVideoDateException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(videoDateService.proposeVideoDate(anyLong(), any(Date.class)))
                .thenThrow(new RuntimeException("Conversation not found"));

        mockMvc.perform(post("/api/v1/video-date/propose")
                        .param("conversationId", "999")
                        .param("proposedTime", "2024-12-31T18:00:00.000+00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Conversation not found"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/respond - Accepts proposal")
    void testRespondToProposalAccept() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "id", 1L,
                "status", "CONFIRMED"
        );
        Mockito.when(videoDateService.respondToProposal(1L, true, null))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/video-date/1/respond")
                        .param("accept", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Mockito.verify(videoDateService).respondToProposal(1L, true, null);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/respond - Declines proposal")
    void testRespondToProposalDecline() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "id", 1L,
                "status", "DECLINED"
        );
        Mockito.when(videoDateService.respondToProposal(1L, false, null))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/video-date/1/respond")
                        .param("accept", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        Mockito.verify(videoDateService).respondToProposal(1L, false, null);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/respond - Responds with counter-proposal")
    void testRespondToProposalWithCounterTime() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "id", 1L,
                "status", "COUNTER_PROPOSED"
        );
        Mockito.when(videoDateService.respondToProposal(eq(1L), eq(false), any(Date.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/video-date/1/respond")
                        .param("accept", "false")
                        .param("counterTime", "2024-12-31T20:00:00.000+00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COUNTER_PROPOSED"));

        Mockito.verify(videoDateService).respondToProposal(eq(1L), eq(false), any(Date.class));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/start - Starts video date")
    void testStartVideoDate() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "id", 1L,
                "status", "IN_PROGRESS",
                "roomUrl", "https://video.example.com/room/123"
        );
        Mockito.when(videoDateService.startVideoDate(1L)).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/video-date/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.roomUrl").exists());

        Mockito.verify(videoDateService).startVideoDate(1L);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/start - Handles exception when starting")
    void testStartVideoDateException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(videoDateService.startVideoDate(1L))
                .thenThrow(new RuntimeException("Video date not confirmed"));

        mockMvc.perform(post("/api/v1/video-date/1/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Video date not confirmed"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/end - Ends video date")
    void testEndVideoDate() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "id", 1L,
                "status", "COMPLETED",
                "duration", 1800
        );
        Mockito.when(videoDateService.endVideoDate(1L)).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/video-date/1/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.duration").exists());

        Mockito.verify(videoDateService).endVideoDate(1L);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/feedback - Submits feedback")
    void testSubmitFeedback() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> feedback = new HashMap<>();
        feedback.put("rating", 5);
        feedback.put("comment", "Great conversation!");
        feedback.put("wouldMeetAgain", true);

        Map<String, Object> mockResult = Map.of(
                "id", 1L,
                "feedbackSubmitted", true
        );
        Mockito.when(videoDateService.submitFeedback(eq(1L), any(Map.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/video-date/1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackSubmitted").value(true));

        Mockito.verify(videoDateService).submitFeedback(eq(1L), any(Map.class));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/video-date/upcoming - Returns upcoming dates")
    void testGetUpcomingDates() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "dates", List.of(
                        Map.of("id", 1L, "scheduledTime", new Date()),
                        Map.of("id", 2L, "scheduledTime", new Date())
                ),
                "count", 2
        );
        Mockito.when(videoDateService.getUpcomingDates()).thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/video-date/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates").isArray())
                .andExpect(jsonPath("$.dates.length()").value(2))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/video-date/proposals - Returns pending proposals")
    void testGetPendingProposals() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "proposals", List.of(
                        Map.of("id", 1L, "status", "PROPOSED")
                ),
                "count", 1
        );
        Mockito.when(videoDateService.getPendingProposals()).thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/video-date/proposals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposals").isArray())
                .andExpect(jsonPath("$.proposals.length()").value(1))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/video-date/history - Returns date history")
    void testGetDateHistory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> mockResult = Map.of(
                "history", List.of(
                        Map.of("id", 1L, "status", "COMPLETED", "date", new Date()),
                        Map.of("id", 2L, "status", "COMPLETED", "date", new Date())
                ),
                "total", 2
        );
        Mockito.when(videoDateService.getDateHistory()).thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/video-date/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/video-date/upcoming - Handles service exception")
    void testGetUpcomingDatesException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(videoDateService.getUpcomingDates())
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/v1/video-date/upcoming"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Database error"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/respond - Handles exception when responding")
    void testRespondToProposalException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(videoDateService.respondToProposal(anyLong(), anyBoolean(), any()))
                .thenThrow(new RuntimeException("Proposal already responded"));

        mockMvc.perform(post("/api/v1/video-date/1/respond")
                        .param("accept", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Proposal already responded"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/video-date/{id}/feedback - Handles exception when submitting feedback")
    void testSubmitFeedbackException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, Object> feedback = new HashMap<>();
        feedback.put("rating", 5);

        Mockito.when(videoDateService.submitFeedback(eq(1L), any(Map.class)))
                .thenThrow(new RuntimeException("Video date not completed"));

        mockMvc.perform(post("/api/v1/video-date/1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedback)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Video date not completed"));
    }
}
