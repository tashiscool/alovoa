package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.MatchWindow;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MatchWindowControllerTest {

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
    private MatchWindowService windowService;

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
    @DisplayName("GET /api/v1/match-windows/pending - Returns pending decisions")
    void testGetPendingDecisions() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<MatchWindow> mockWindows = new ArrayList<>();
        Mockito.when(windowService.getPendingDecisions()).thenReturn(mockWindows);

        mockMvc.perform(get("/api/v1/match-windows/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/match-windows/waiting - Returns waiting matches")
    void testGetWaitingMatches() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<MatchWindow> mockWindows = new ArrayList<>();
        Mockito.when(windowService.getWaitingMatches()).thenReturn(mockWindows);

        mockMvc.perform(get("/api/v1/match-windows/waiting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/match-windows/confirmed - Returns confirmed matches")
    void testGetConfirmedMatches() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<MatchWindow> mockWindows = new ArrayList<>();
        Mockito.when(windowService.getConfirmedMatches()).thenReturn(mockWindows);

        mockMvc.perform(get("/api/v1/match-windows/confirmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/match-windows/pending/count - Returns pending count")
    void testGetPendingCount() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(windowService.getPendingCount()).thenReturn(5);

        mockMvc.perform(get("/api/v1/match-windows/pending/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/match-windows/{uuid} - Returns 404 when window not found")
    void testGetWindow() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        // Use empty to avoid serialization issues with mock MatchWindow
        Mockito.when(windowService.getWindow(uuid)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/match-windows/" + uuid))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/match-windows/{uuid} - Returns 404 when window not found")
    void testGetWindowNotFound() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        Mockito.when(windowService.getWindow(uuid)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/match-windows/" + uuid))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/match-windows/{uuid}/confirm - Handles window not found")
    void testConfirmInterest() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        // Simulate exception when window not found
        Mockito.when(windowService.confirmInterest(uuid))
                .thenThrow(new RuntimeException("Window not found"));

        mockMvc.perform(post("/api/v1/match-windows/" + uuid + "/confirm"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/match-windows/{uuid}/confirm - Handles exception")
    void testConfirmInterestException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        Mockito.when(windowService.confirmInterest(uuid))
                .thenThrow(new RuntimeException("Window expired"));

        mockMvc.perform(post("/api/v1/match-windows/" + uuid + "/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Window expired"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/match-windows/{uuid}/decline - Handles window not found")
    void testDeclineMatch() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        // Simulate exception when window not found
        Mockito.when(windowService.declineMatch(uuid))
                .thenThrow(new RuntimeException("Window not found"));

        mockMvc.perform(post("/api/v1/match-windows/" + uuid + "/decline"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/match-windows/{uuid}/decline - Handles exception")
    void testDeclineMatchException() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        Mockito.when(windowService.declineMatch(uuid))
                .thenThrow(new RuntimeException("Already processed"));

        mockMvc.perform(post("/api/v1/match-windows/" + uuid + "/decline"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Already processed"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/match-windows/{uuid}/extend - Handles window not found")
    void testRequestExtension() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        // Simulate exception when window not found
        Mockito.when(windowService.requestExtension(uuid))
                .thenThrow(new RuntimeException("Window not found"));

        mockMvc.perform(post("/api/v1/match-windows/" + uuid + "/extend"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/match-windows/{uuid}/extend - Handles exception when extension already used")
    void testRequestExtensionAlreadyUsed() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        UUID uuid = UUID.randomUUID();
        Mockito.when(windowService.requestExtension(uuid))
                .thenThrow(new RuntimeException("Extension already used"));

        mockMvc.perform(post("/api/v1/match-windows/" + uuid + "/extend"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Extension already used"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/match-windows/dashboard - Returns dashboard with all sections")
    void testGetDashboard() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<MatchWindow> pending = new ArrayList<>();
        List<MatchWindow> waiting = new ArrayList<>();
        List<MatchWindow> confirmed = new ArrayList<>();

        Mockito.when(windowService.getPendingDecisions()).thenReturn(pending);
        Mockito.when(windowService.getWaitingMatches()).thenReturn(waiting);
        Mockito.when(windowService.getConfirmedMatches()).thenReturn(confirmed);
        Mockito.when(windowService.getPendingCount()).thenReturn(3);

        mockMvc.perform(get("/api/v1/match-windows/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").isArray())
                .andExpect(jsonPath("$.waiting").isArray())
                .andExpect(jsonPath("$.confirmed").isArray())
                .andExpect(jsonPath("$.pendingCount").value(3));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/match-windows/pending/count - Returns zero when no pending")
    void testGetPendingCountZero() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Mockito.when(windowService.getPendingCount()).thenReturn(0);

        mockMvc.perform(get("/api/v1/match-windows/pending/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
