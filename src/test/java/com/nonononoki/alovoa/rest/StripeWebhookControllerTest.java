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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StripeWebhookControllerTest {

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
    private StripeService stripeService;

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
    @DisplayName("POST /api/v1/stripe/webhook - Valid signature processes successfully")
    void testWebhook_ValidSignature() throws Exception {
        String payload = createMockWebhookPayload();
        String signature = "valid_signature";

        // Mock successful webhook processing
        Mockito.doNothing().when(stripeService).processWebhook(payload, signature);

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", signature))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));
    }

    @Test
    @DisplayName("POST /api/v1/stripe/webhook - Invalid signature returns 401")
    void testWebhook_InvalidSignature() throws Exception {
        String payload = createMockWebhookPayload();
        String invalidSignature = "invalid_signature";

        // Mock signature verification failure
        Mockito.doThrow(new SecurityException("Invalid webhook signature"))
                .when(stripeService).processWebhook(payload, invalidSignature);

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", invalidSignature))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid signature"));
    }

    @Test
    @DisplayName("POST /api/v1/stripe/webhook - Processing error returns 500")
    void testWebhook_ProcessingError() throws Exception {
        String payload = createMockWebhookPayload();
        String signature = "valid_signature";

        // Mock processing error
        Mockito.doThrow(new RuntimeException("Database error"))
                .when(stripeService).processWebhook(payload, signature);

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", signature))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Processing error"));
    }

    @Test
    @DisplayName("POST /api/v1/stripe/webhook - Missing signature header")
    void testWebhook_MissingSignature() throws Exception {
        String payload = createMockWebhookPayload();

        // Mock will be called with null signature
        Mockito.doThrow(new SecurityException("Missing signature"))
                .when(stripeService).processWebhook(eq(payload), isNull());

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/stripe/checkout - Create checkout session successfully")
    void testCreateCheckout_Success() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(false)).thenReturn(user);
        Mockito.when(stripeService.isEnabled()).thenReturn(true);

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("sessionId", "cs_test_123");
        sessionData.put("url", "https://checkout.stripe.com/test");

        Mockito.when(stripeService.createDonationSession(eq(user.getId()), eq(2500L), isNull()))
                .thenReturn(sessionData);

        Map<String, Object> request = new HashMap<>();
        request.put("amountCents", 2500);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.sessionId").value("cs_test_123"))
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/test"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/stripe/checkout - Stripe disabled")
    void testCreateCheckout_StripeDisabled() throws Exception {
        Mockito.when(stripeService.isEnabled()).thenReturn(false);

        Map<String, Object> request = new HashMap<>();
        request.put("amountCents", 1000);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.message").value("Donations not yet configured"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/stripe/checkout - Amount too small")
    void testCreateCheckout_AmountTooSmall() throws Exception {
        Mockito.when(stripeService.isEnabled()).thenReturn(true);

        Map<String, Object> request = new HashMap<>();
        request.put("amountCents", 50); // Less than $1

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Minimum donation is $1"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/stripe/checkout - Amount too large")
    void testCreateCheckout_AmountTooLarge() throws Exception {
        Mockito.when(stripeService.isEnabled()).thenReturn(true);

        Map<String, Object> request = new HashMap<>();
        request.put("amountCents", 150000); // More than $1000

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Maximum donation is $1000"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/stripe/checkout - With prompt ID")
    void testCreateCheckout_WithPromptId() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(false)).thenReturn(user);
        Mockito.when(stripeService.isEnabled()).thenReturn(true);

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("sessionId", "cs_test_456");
        sessionData.put("url", "https://checkout.stripe.com/test");

        Mockito.when(stripeService.createDonationSession(eq(user.getId()), eq(5000L), eq(123L)))
                .thenReturn(sessionData);

        Map<String, Object> request = new HashMap<>();
        request.put("amountCents", 5000);
        request.put("promptId", 123);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.sessionId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/stripe/checkout - Anonymous donation (no auth)")
    void testCreateCheckout_AnonymousDonation() throws Exception {
        Mockito.when(stripeService.isEnabled()).thenReturn(true);

        // Mock getCurrentUser to return null (anonymous)
        Mockito.when(authService.getCurrentUser(false)).thenReturn(null);

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("sessionId", "cs_test_anon");
        sessionData.put("url", "https://checkout.stripe.com/test");

        Mockito.when(stripeService.createDonationSession(isNull(), eq(1000L), isNull()))
                .thenReturn(sessionData);

        Map<String, Object> request = new HashMap<>();
        request.put("amountCents", 1000);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("cs_test_anon"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/stripe/session/{sessionId} - Get session details")
    void testGetSession_Success() throws Exception {
        Map<String, Object> sessionDetails = new HashMap<>();
        sessionDetails.put("id", "cs_test_123");
        sessionDetails.put("amount_total", 2500);
        sessionDetails.put("payment_status", "paid");

        Mockito.when(stripeService.getSessionDetails("cs_test_123"))
                .thenReturn(sessionDetails);

        mockMvc.perform(get("/api/v1/stripe/session/cs_test_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cs_test_123"))
                .andExpect(jsonPath("$.amount_total").value(2500))
                .andExpect(jsonPath("$.payment_status").value("paid"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/stripe/session/{sessionId} - Session not found")
    void testGetSession_Error() throws Exception {
        Mockito.when(stripeService.getSessionDetails("invalid_session"))
                .thenThrow(new RuntimeException("Session not found"));

        mockMvc.perform(get("/api/v1/stripe/session/invalid_session"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to get session"));
    }

    @Test
    @DisplayName("GET /api/v1/stripe/config - Get Stripe configuration")
    void testGetConfig() throws Exception {
        Mockito.when(stripeService.isEnabled()).thenReturn(true);
        Mockito.when(stripeService.getPublicKey()).thenReturn("pk_test_123");

        mockMvc.perform(get("/api/v1/stripe/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value("pk_test_123"))
                .andExpect(jsonPath("$.suggestedAmounts").isArray())
                .andExpect(jsonPath("$.suggestedAmounts.length()").value(4));
    }

    @Test
    @DisplayName("GET /api/v1/stripe/config - Stripe disabled returns empty key")
    void testGetConfig_Disabled() throws Exception {
        Mockito.when(stripeService.isEnabled()).thenReturn(false);
        Mockito.when(stripeService.getPublicKey()).thenReturn(null);

        mockMvc.perform(get("/api/v1/stripe/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.publicKey").value(""));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/stripe/checkout - Service throws exception")
    void testCreateCheckout_ServiceException() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(false)).thenReturn(user);
        Mockito.when(stripeService.isEnabled()).thenReturn(true);

        Mockito.when(stripeService.createDonationSession(any(), anyLong(), any()))
                .thenThrow(new RuntimeException("Stripe API error"));

        Map<String, Object> request = new HashMap<>();
        request.put("amountCents", 1000);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to create checkout session"));
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Create a mock Stripe webhook payload for testing.
     */
    private String createMockWebhookPayload() {
        return """
                {
                    "id": "evt_test_webhook",
                    "type": "checkout.session.completed",
                    "data": {
                        "object": {
                            "id": "cs_test_session",
                            "object": "checkout.session",
                            "amount_total": 2500,
                            "currency": "usd",
                            "customer_email": "donor@example.com",
                            "payment_status": "paid",
                            "metadata": {
                                "type": "donation",
                                "user_id": "123"
                            }
                        }
                    }
                }
                """;
    }
}
