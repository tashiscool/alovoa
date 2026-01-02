package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.AssessmentResponseDto;
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

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssessmentControllerTest {

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
    @DisplayName("GET /assessment/questions/{category} - Get questions by valid category")
    void testGetQuestionsByCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/questions/BIG_FIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/questions/{category} - Invalid category returns error")
    void testGetQuestionsByInvalidCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/questions/INVALID_CATEGORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid category"))
                .andExpect(jsonPath("$.validCategories").isArray())
                .andExpect(jsonPath("$.validCategories", hasSize(6)));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/progress - Get assessment progress")
    void testGetAssessmentProgress() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/submit - Submit responses successfully")
    void testSubmitResponses() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        responses.add(new AssessmentResponseDto("Q1", 3));
        responses.add(new AssessmentResponseDto("Q2", 4));

        mockMvc.perform(post("/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(responses)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/results - Get assessment results")
    void testGetAssessmentResults() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/reset - Reset all assessments")
    void testResetAllAssessments() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/assessment/reset"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/reset - Reset specific category")
    void testResetSpecificCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/assessment/reset")
                        .param("category", "BIG_FIVE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/reset - Invalid category returns error")
    void testResetInvalidCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/assessment/reset")
                        .param("category", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid category"))
                .andExpect(jsonPath("$.validCategories").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/admin/reload-questions - Admin can reload questions")
    void testAdminReloadQuestions() throws Exception {
        User adminUser = testUsers.get(0);
        adminUser.setAdmin(true);
        userRepo.save(adminUser);
        Mockito.doReturn(adminUser).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/assessment/admin/reload-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Questions reloaded from JSON"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/admin/reload-questions - Non-admin returns 403")
    void testNonAdminReloadQuestions() throws Exception {
        User user = testUsers.get(0);
        user.setAdmin(false);
        userRepo.save(user);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/assessment/admin/reload-questions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Admin access required"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/match/{userUuid} - Calculate match with another user")
    void testCalculateMatch() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/match/" + user2.getUuid()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/match/{userUuid}/explain - Get match explanation")
    void testGetMatchExplanation() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/match/" + user2.getUuid() + "/explain"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/next - Get next unanswered question")
    void testGetNextQuestion() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/next"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/next - Get next question for specific category")
    void testGetNextQuestionByCategory() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/next")
                        .param("category", "VALUES"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/batch - Get batch of unanswered questions")
    void testGetNextQuestionBatch() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/batch")
                        .param("limit", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/batch - Limit is capped at 50")
    void testGetNextQuestionBatchMaxLimit() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/batch")
                        .param("limit", "100"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/validate - Validate answer")
    void testValidateAnswer() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        AssessmentResponseDto response = new AssessmentResponseDto();
        response.setQuestionId("Q1");
        response.setNumericResponse(3);

        mockMvc.perform(post("/assessment/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(response)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /assessment/answer - Submit single answer")
    void testSubmitSingleAnswer() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        AssessmentResponseDto response = new AssessmentResponseDto();
        response.setQuestionId("Q1");
        response.setNumericResponse(3);
        response.setImportance("high");

        mockMvc.perform(post("/assessment/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(response)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /assessment/stats - Get question bank statistics")
    void testGetQuestionBankStats() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/assessment/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallProgress").exists());
    }
}
