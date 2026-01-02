package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.WaitlistEntry;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.WaitlistRepository;
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
class WaitlistControllerTest {

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
    private WaitlistRepository waitlistRepo;

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
        waitlistRepo.deleteAll();
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - Successful signup")
    void testWaitlistSignup_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("email", "waitlist@example.com");
        request.put("gender", "woman");
        request.put("seeking", "men");
        request.put("location", "dc");
        request.put("source", "landing");

        mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("You're on the list!"))
                .andExpect(jsonPath("$.inviteCode").exists())
                .andExpect(jsonPath("$.position").exists())
                .andExpect(jsonPath("$.priorityNote").value("Women get priority access!"));
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - With referral code")
    void testWaitlistSignup_WithReferralCode() throws Exception {
        // First user signs up
        Map<String, String> request1 = new HashMap<>();
        request1.put("email", "user1@example.com");
        request1.put("gender", "man");
        request1.put("seeking", "women");
        request1.put("location", "arlington");

        String response1 = mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> result1 = objectMapper.readValue(response1, Map.class);
        String inviteCode = (String) result1.get("inviteCode");

        // Second user signs up with referral
        Map<String, String> request2 = new HashMap<>();
        request2.put("email", "user2@example.com");
        request2.put("gender", "woman");
        request2.put("seeking", "men");
        request2.put("location", "dc");
        request2.put("referralCode", inviteCode);

        mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - With UTM parameters")
    void testWaitlistSignup_WithUTMParameters() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("email", "utm-user@example.com");
        request.put("gender", "nonbinary");
        request.put("seeking", "everyone");
        request.put("location", "alexandria");
        request.put("utmSource", "google");
        request.put("utmMedium", "cpc");
        request.put("utmCampaign", "dating-app-launch");

        mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - Invalid gender returns error")
    void testWaitlistSignup_InvalidGender() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("email", "test@example.com");
        request.put("seeking", "men");
        request.put("location", "dc");
        // Missing gender

        mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - Duplicate email returns existing entry (idempotent)")
    void testWaitlistSignup_DuplicateEmail() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("email", "duplicate@example.com");
        request.put("gender", "woman");
        request.put("seeking", "men");
        request.put("location", "dc");

        // First signup
        String response1 = mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> result1 = objectMapper.readValue(response1, Map.class);
        String inviteCode = (String) result1.get("inviteCode");

        // Second signup with same email - idempotent, returns success with same invite code
        mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.inviteCode").value(inviteCode));
    }

    @Test
    @DisplayName("GET /api/v1/waitlist/status - Check status by email")
    void testCheckStatus_Found() throws Exception {
        // Sign up first
        Map<String, String> signupRequest = new HashMap<>();
        signupRequest.put("email", "status-check@example.com");
        signupRequest.put("gender", "man");
        signupRequest.put("seeking", "women");
        signupRequest.put("location", "nova");

        mockMvc.perform(post("/api/v1/waitlist/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isOk());

        // Check status
        mockMvc.perform(get("/api/v1/waitlist/status")
                        .param("email", "status-check@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.position").exists())
                .andExpect(jsonPath("$.inviteCode").exists())
                .andExpect(jsonPath("$.referrals").exists())
                .andExpect(jsonPath("$.inviteCodesRemaining").exists());
    }

    @Test
    @DisplayName("GET /api/v1/waitlist/status - Email not found")
    void testCheckStatus_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/waitlist/status")
                        .param("email", "nonexistent@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.message").value("Email not found on waitlist"));
    }

    @Test
    @DisplayName("GET /api/v1/waitlist/count - Get public count")
    void testGetCount() throws Exception {
        // Add a few entries
        for (int i = 1; i <= 5; i++) {
            Map<String, String> request = new HashMap<>();
            request.put("email", "user" + i + "@example.com");
            request.put("gender", i % 2 == 0 ? "woman" : "man");
            request.put("seeking", "everyone");
            request.put("location", "dc");

            mockMvc.perform(post("/api/v1/waitlist/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/waitlist/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.displayMessage").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/waitlist/stats - Admin access only")
    void testGetStats_AdminOnly() throws Exception {
        User adminUser = testUsers.get(0);
        adminUser.setAdmin(true);
        userRepo.save(adminUser);

        Mockito.doReturn(adminUser).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/api/v1/waitlist/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/waitlist/stats - Non-admin returns error")
    void testGetStats_NonAdminFails() throws Exception {
        User regularUser = testUsers.get(0);
        regularUser.setAdmin(false);
        userRepo.save(regularUser);

        Mockito.doReturn(regularUser).when(authService).getCurrentUser(true);

        // AlovoaException is caught by global ExceptionHandler which returns 409 Conflict
        mockMvc.perform(get("/api/v1/waitlist/stats"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - Different location values")
    void testWaitlistSignup_DifferentLocations() throws Exception {
        String[] locations = {"dc", "arlington", "alexandria", "nova-other", "maryland", "other"};

        for (int i = 0; i < locations.length; i++) {
            Map<String, String> request = new HashMap<>();
            request.put("email", "location" + i + "@example.com");
            request.put("gender", "woman");
            request.put("seeking", "men");
            request.put("location", locations[i]);

            mockMvc.perform(post("/api/v1/waitlist/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - Different gender values")
    void testWaitlistSignup_DifferentGenders() throws Exception {
        String[] genders = {"woman", "female", "man", "male", "nonbinary", "non-binary"};

        for (int i = 0; i < genders.length; i++) {
            Map<String, String> request = new HashMap<>();
            request.put("email", "gender" + i + "@example.com");
            request.put("gender", genders[i]);
            request.put("seeking", "everyone");
            request.put("location", "dc");

            mockMvc.perform(post("/api/v1/waitlist/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @DisplayName("POST /api/v1/waitlist/signup - Different seeking values")
    void testWaitlistSignup_DifferentSeeking() throws Exception {
        String[] seekingOptions = {"men", "male", "women", "female", "everyone", "all", "both"};

        for (int i = 0; i < seekingOptions.length; i++) {
            Map<String, String> request = new HashMap<>();
            request.put("email", "seeking" + i + "@example.com");
            request.put("gender", "woman");
            request.put("seeking", seekingOptions[i]);
            request.put("location", "dc");

            mockMvc.perform(post("/api/v1/waitlist/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
