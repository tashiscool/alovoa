package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.User.DonationTier;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DonationControllerTest {

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
    private DonationService donationService;

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
    @DisplayName("GET /api/v1/donation/info - Get current user donation info")
    void testGetDonationInfo() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/api/v1/donation/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").exists())
                .andExpect(jsonPath("$.totalDonations").exists())
                .andExpect(jsonPath("$.streakMonths").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/donation/record - Record donation successfully")
    void testRecordDonation_Success() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "25.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.tier").exists());

        // Verify donation was recorded
        User updatedUser = userRepo.findById(user.getId()).orElseThrow();
        assertEquals(25.00, updatedUser.getTotalDonations(), 0.01);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/donation/record - Record donation with prompt ID")
    void testRecordDonation_WithPromptId() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "10.00")
                        .param("promptId", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.tier").value("SUPPORTER"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/donation/record - Upgrades donation tier")
    void testRecordDonation_UpgradesTier() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // First donation - should become SUPPORTER
        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "5.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("SUPPORTER"));

        // Second donation - should become BELIEVER ($25+ total)
        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "20.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("BELIEVER"));

        // Verify final state
        User updatedUser = userRepo.findById(user.getId()).orElseThrow();
        assertEquals(25.00, updatedUser.getTotalDonations(), 0.01);
        assertEquals(DonationTier.BELIEVER, updatedUser.getDonationTier());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/donation/dismiss/{promptId} - Dismiss donation prompt")
    void testDismissPrompt() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/api/v1/donation/dismiss/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("dismissed"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/donation/thank-you/{tier} - Get thank you message")
    void testGetThankYouMessage_ValidTier() throws Exception {
        mockMvc.perform(get("/api/v1/donation/thank-you/supporter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/donation/thank-you/{tier} - Invalid tier returns error")
    void testGetThankYouMessage_InvalidTier() throws Exception {
        mockMvc.perform(get("/api/v1/donation/thank-you/invalid_tier"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid tier"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/donation/thank-you/{tier} - All tiers return messages")
    void testGetThankYouMessage_AllTiers() throws Exception {
        String[] tiers = {"SUPPORTER", "BELIEVER", "BUILDER", "FOUNDING_MEMBER"};

        for (String tier : tiers) {
            mockMvc.perform(get("/api/v1/donation/thank-you/" + tier.toLowerCase()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/donation/stats - Admin can access stats")
    void testGetDonationStats_AdminAccess() throws Exception {
        User adminUser = testUsers.get(0);
        adminUser.setAdmin(true);
        userRepo.save(adminUser);

        Mockito.doReturn(adminUser).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/api/v1/donation/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/donation/stats - Non-admin cannot access stats")
    void testGetDonationStats_NonAdminDenied() throws Exception {
        User regularUser = testUsers.get(0);
        regularUser.setAdmin(false);
        userRepo.save(regularUser);

        Mockito.doReturn(regularUser).when(authService).getCurrentUser(true);

        // AlovoaException is caught by global ExceptionHandler which returns 409 Conflict
        mockMvc.perform(get("/api/v1/donation/stats"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/donation/relationship-exit - Get relationship exit prompt")
    void testGetRelationshipExitPrompt() throws Exception {
        mockMvc.perform(get("/api/v1/donation/relationship-exit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.amounts").isArray())
                .andExpect(jsonPath("$.amounts.length()").value(4))
                .andExpect(jsonPath("$.headline").value("Looks like AURA worked!"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/donation/record - Multiple donations accumulate")
    void testMultipleDonations_Accumulate() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Record three donations
        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "10.00"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "15.00"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "5.00"))
                .andExpect(status().isOk());

        // Verify total
        User updatedUser = userRepo.findById(user.getId()).orElseThrow();
        assertEquals(30.00, updatedUser.getTotalDonations(), 0.01);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/donation/info - Shows correct tier after donation")
    void testGetDonationInfo_AfterDonation() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Initial state - NONE
        mockMvc.perform(get("/api/v1/donation/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("NONE"));

        // Record a donation
        donationService.recordDonation(user, new BigDecimal("50.00"), null);

        // Refresh user
        user = userRepo.findById(user.getId()).orElseThrow();
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        // Check updated info - $50 is BELIEVER tier ($21-50), BUILDER is $51+
        mockMvc.perform(get("/api/v1/donation/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("BELIEVER"))
                .andExpect(jsonPath("$.totalDonations").value(50.00));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/donation/record - Large donation reaches FOUNDING_MEMBER tier")
    void testRecordDonation_FoundingMemberTier() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/api/v1/donation/record")
                        .param("amount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FOUNDING_MEMBER"));

        User updatedUser = userRepo.findById(user.getId()).orElseThrow();
        assertEquals(DonationTier.FOUNDING_MEMBER, updatedUser.getDonationTier());
    }
}
