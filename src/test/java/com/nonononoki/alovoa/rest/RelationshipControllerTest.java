package com.nonononoki.alovoa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserRelationship.RelationshipType;
import com.nonononoki.alovoa.model.RelationshipDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRelationshipRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RelationshipControllerTest {

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
    private UserRelationshipRepository relationshipRepo;

    @Autowired
    private ConversationRepository conversationRepo;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private DonationService donationService;

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
        relationshipRepo.deleteAll();
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/relationship - No content when no relationship")
    void testGetMyRelationshipEmpty() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/api/v1/relationship"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/relationship/types - Returns all relationship types")
    void testGetRelationshipTypes() throws Exception {
        mockMvc.perform(get("/api/v1/relationship/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(RelationshipType.values().length));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/relationship/request - Send relationship request")
    void testSendRequest() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> request = new HashMap<>();
        request.put("partnerUuid", user2.getUuid().toString());
        request.put("type", "DATING");

        MvcResult result = mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.type").value("DATING"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        RelationshipDto dto = objectMapper.readValue(responseJson, RelationshipDto.class);
        assertEquals(user2.getUuid(), dto.getPartnerUuid());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/relationship/request - With anniversary date")
    void testSendRequestWithAnniversary() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> request = new HashMap<>();
        request.put("partnerUuid", user2.getUuid().toString());
        request.put("type", "IN_A_RELATIONSHIP");
        request.put("anniversaryDate", "2024-02-14");

        mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("IN_A_RELATIONSHIP"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/relationship/{uuid}/accept - Accept request")
    void testAcceptRequest() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User1 sends request
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put("partnerUuid", user2.getUuid().toString());
        sendRequest.put("type", "DATING");

        MvcResult sendResult = mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk())
                .andReturn();

        RelationshipDto sentDto = objectMapper.readValue(
                sendResult.getResponse().getContentAsString(), RelationshipDto.class);

        // User2 accepts
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/api/v1/relationship/" + sentDto.getUuid() + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/relationship/{uuid}/decline - Decline request")
    void testDeclineRequest() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put("partnerUuid", user2.getUuid().toString());
        sendRequest.put("type", "DATING");

        MvcResult sendResult = mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk())
                .andReturn();

        RelationshipDto sentDto = objectMapper.readValue(
                sendResult.getResponse().getContentAsString(), RelationshipDto.class);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);

        mockMvc.perform(post("/api/v1/relationship/" + sentDto.getUuid() + "/decline"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/v1/relationship/{uuid}/cancel - Cancel sent request")
    void testCancelRequest() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put("partnerUuid", user2.getUuid().toString());
        sendRequest.put("type", "DATING");

        MvcResult sendResult = mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk())
                .andReturn();

        RelationshipDto sentDto = objectMapper.readValue(
                sendResult.getResponse().getContentAsString(), RelationshipDto.class);

        mockMvc.perform(delete("/api/v1/relationship/" + sentDto.getUuid() + "/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/relationship/requests/pending - Get pending requests")
    void testGetPendingRequests() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User1 sends request to user2
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put("partnerUuid", user2.getUuid().toString());
        sendRequest.put("type", "DATING");

        mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk());

        // User2 checks pending requests
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);

        mockMvc.perform(get("/api/v1/relationship/requests/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/relationship/requests/sent - Get sent requests")
    void testGetSentRequests() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put("partnerUuid", user2.getUuid().toString());
        sendRequest.put("type", "DATING");

        mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/relationship/requests/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/v1/relationship/{uuid}/type - Update relationship type")
    void testUpdateType() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create and accept relationship
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put("partnerUuid", user2.getUuid().toString());
        sendRequest.put("type", "DATING");

        MvcResult sendResult = mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk())
                .andReturn();

        RelationshipDto sentDto = objectMapper.readValue(
                sendResult.getResponse().getContentAsString(), RelationshipDto.class);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        mockMvc.perform(post("/api/v1/relationship/" + sentDto.getUuid() + "/accept"))
                .andExpect(status().isOk());

        // Update type
        Map<String, String> updateRequest = new HashMap<>();
        updateRequest.put("type", "ENGAGED");

        mockMvc.perform(put("/api/v1/relationship/" + sentDto.getUuid() + "/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ENGAGED"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/relationship/{uuid}/toggle-visibility - Toggle visibility")
    void testToggleVisibility() throws Exception {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create and accept relationship
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put("partnerUuid", user2.getUuid().toString());
        sendRequest.put("type", "DATING");

        MvcResult sendResult = mockMvc.perform(post("/api/v1/relationship/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk())
                .andReturn();

        RelationshipDto sentDto = objectMapper.readValue(
                sendResult.getResponse().getContentAsString(), RelationshipDto.class);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        mockMvc.perform(post("/api/v1/relationship/" + sentDto.getUuid() + "/accept"))
                .andExpect(status().isOk());

        // Toggle visibility (should become false)
        mockMvc.perform(post("/api/v1/relationship/" + sentDto.getUuid() + "/toggle-visibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(false));

        // Toggle again (should become true)
        mockMvc.perform(post("/api/v1/relationship/" + sentDto.getUuid() + "/toggle-visibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(true));
    }
}
