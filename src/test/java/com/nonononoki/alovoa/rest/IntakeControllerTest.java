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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
class IntakeControllerTest {

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
    @DisplayName("GET /intake/progress - Get intake progress")
    void testGetProgress() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(get("/intake/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").exists())
                .andExpect(jsonPath("$.encouragement").exists())
                .andExpect(jsonPath("$.platformStats").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /intake/questions - Get core questions")
    void testGetCoreQuestions() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(get("/intake/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").isArray())
                .andExpect(jsonPath("$.totalRequired").value(10))
                .andExpect(jsonPath("$.header").exists())
                .andExpect(jsonPath("$.funFact").exists())
                .andExpect(jsonPath("$.hobbyInsight").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/questions/submit - Submit answers successfully")
    void testSubmitAnswers() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        List<AssessmentResponseDto> responses = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            responses.add(new AssessmentResponseDto("Q" + i, 3));
        }

        mockMvc.perform(post("/intake/questions/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(responses)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/questions/submit - Empty responses returns error")
    void testSubmitEmptyAnswers() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        List<AssessmentResponseDto> responses = new ArrayList<>();

        mockMvc.perform(post("/intake/questions/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(responses)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No responses provided"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/questions/submit - Null responses returns error")
    void testSubmitNullAnswers() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(post("/intake/questions/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No responses provided"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/video - Upload video successfully")
    void testUploadVideo() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        MockMultipartFile video = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                "test video content".getBytes()
        );

        mockMvc.perform(multipart("/intake/video")
                        .file(video)
                        .param("skipAiAnalysis", "false"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/video - Upload video with skip AI")
    void testUploadVideoSkipAi() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        MockMultipartFile video = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                "test video content".getBytes()
        );

        mockMvc.perform(multipart("/intake/video")
                        .file(video)
                        .param("skipAiAnalysis", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/profile-info - Submit manual profile info")
    void testSubmitProfileInfo() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        Map<String, String> profileInfo = new HashMap<>();
        profileInfo.put("worldview", "I believe in kindness and compassion");
        profileInfo.put("background", "Grew up in a small town");
        profileInfo.put("lifeStory", "I've always been passionate about helping others");

        mockMvc.perform(post("/intake/profile-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileInfo)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/video/skip-analysis - Skip video analysis")
    void testSkipVideoAnalysis() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(post("/intake/video/skip-analysis"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /intake/video/status/{videoId} - Get video status")
    void testGetVideoStatus() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(get("/intake/video/status/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(1))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.providerAvailable").exists())
                .andExpect(jsonPath("$.provider").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /intake/video/status/{videoId} - Non-existent video returns 404")
    void testGetVideoStatusNotFound() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(get("/intake/video/status/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/video/retry/{videoId} - Retry video analysis")
    void testRetryVideoAnalysis() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(post("/intake/video/retry/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Analysis retry initiated"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /intake/audio - Upload audio successfully")
    void testUploadAudio() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        MockMultipartFile audio = new MockMultipartFile(
                "file",
                "test-audio.mp3",
                "audio/mp3",
                "test audio content".getBytes()
        );

        mockMvc.perform(multipart("/intake/audio")
                        .file(audio))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /intake/ai/status - Get AI provider status")
    void testGetAiStatus() throws Exception {
        mockMvc.perform(get("/intake/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").exists())
                .andExpect(jsonPath("$.provider").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /intake/video/tips - Get video recording tips")
    void testGetVideoTips() throws Exception {
        mockMvc.perform(get("/intake/video/tips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header").exists())
                .andExpect(jsonPath("$.tips").exists())
                .andExpect(jsonPath("$.funFact").exists())
                .andExpect(jsonPath("$.reminder").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /intake/encouragement/{step} - Get step encouragement")
    void testGetStepEncouragement() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(get("/intake/encouragement/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepEncouragement").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /intake/life-stats - Get personalized life stats")
    void testGetLifeStats() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        mockMvc.perform(get("/intake/life-stats"))
                .andExpect(status().isOk());
    }
}
