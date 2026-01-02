package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.EssayPromptTemplate;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.EssayPromptTemplateRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EssayControllerTest {

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

    @Autowired
    private EssayPromptTemplateRepository essayTemplateRepo;

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

        // Ensure templates exist
        if (essayTemplateRepo.count() == 0) {
            essayTemplateRepo.saveAll(List.of(EssayPromptTemplate.getDefaultTemplates()));
        }
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/essays/templates - Get all templates")
    void testGetTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/essays/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/essays - Get user essays")
    void testGetUserEssays() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/api/v1/essays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/essays/{promptId} - Save single essay")
    void testSaveEssay() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, String> request = new HashMap<>();
        request.put("text", "This is my self summary essay.");

        mockMvc.perform(post("/api/v1/essays/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify it was saved
        mockMvc.perform(get("/api/v1/essays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answer").value("This is my self summary essay."));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/essays - Save multiple essays")
    void testSaveMultipleEssays() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, String> essays = new HashMap<>();
        essays.put("1", "Self summary content");
        essays.put("2", "What I'm doing content");
        essays.put("3", "Really good at content");

        mockMvc.perform(post("/api/v1/essays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(essays)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/essays/{promptId} - Invalid prompt ID returns error")
    void testInvalidPromptId() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, String> request = new HashMap<>();
        request.put("text", "Some text");

        mockMvc.perform(post("/api/v1/essays/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/essays/{promptId} - Essay too long returns error")
    void testEssayTooLong() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        Map<String, String> request = new HashMap<>();
        request.put("text", "x".repeat(2001));

        mockMvc.perform(post("/api/v1/essays/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/v1/essays/{promptId} - Delete essay")
    void testDeleteEssay() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // First save an essay
        Map<String, String> request = new HashMap<>();
        request.put("text", "Essay to delete");

        mockMvc.perform(post("/api/v1/essays/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then delete it
        mockMvc.perform(delete("/api/v1/essays/1"))
                .andExpect(status().isOk());

        // Verify it's gone
        mockMvc.perform(get("/api/v1/essays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answer").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/essays/count - Get filled essay count")
    void testGetFilledCount() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Initially 0
        mockMvc.perform(get("/api/v1/essays/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // Add some essays
        Map<String, String> essays = new HashMap<>();
        essays.put("1", "Essay 1");
        essays.put("2", "Essay 2");
        essays.put("3", "Essay 3");

        mockMvc.perform(post("/api/v1/essays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(essays)))
                .andExpect(status().isOk());

        // Now should be 3
        mockMvc.perform(get("/api/v1/essays/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }
}
