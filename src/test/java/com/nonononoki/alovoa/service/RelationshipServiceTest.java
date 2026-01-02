package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserRelationship;
import com.nonononoki.alovoa.entity.UserRelationship.RelationshipStatus;
import com.nonononoki.alovoa.entity.UserRelationship.RelationshipType;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.RelationshipDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRelationshipRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelationshipServiceTest {

    @Autowired
    private RelationshipService relationshipService;

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
    @DisplayName("Send relationship request successfully")
    void testSendRequest() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        RelationshipDto dto = relationshipService.sendRequest(
                user2.getUuid(),
                RelationshipType.DATING,
                null
        );

        assertNotNull(dto);
        assertNotNull(dto.getUuid());
        assertEquals(RelationshipType.DATING, dto.getType());
        assertEquals(RelationshipStatus.PENDING, dto.getStatus());
        assertEquals(user2.getUuid(), dto.getPartnerUuid());
        assertTrue(dto.isInitiator());
    }

    @Test
    @DisplayName("Send relationship request with anniversary date")
    void testSendRequestWithAnniversary() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        Date anniversary = new Date();
        RelationshipDto dto = relationshipService.sendRequest(
                user2.getUuid(),
                RelationshipType.IN_A_RELATIONSHIP,
                anniversary
        );

        assertNotNull(dto.getAnniversaryDate());
    }

    @Test
    @DisplayName("Cannot send request to self")
    void testCannotRequestSelf() throws Exception {
        User user1 = testUsers.get(0);
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);

        assertThrows(AlovoaException.class, () -> {
            relationshipService.sendRequest(user1.getUuid(), RelationshipType.DATING, null);
        });
    }

    @Test
    @DisplayName("Cannot send duplicate request")
    void testCannotDuplicateRequest() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        // Try to send another request
        assertThrows(AlovoaException.class, () -> {
            relationshipService.sendRequest(user2.getUuid(), RelationshipType.ENGAGED, null);
        });
    }

    @Test
    @DisplayName("Accept relationship request successfully")
    void testAcceptRequest() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // User1 sends request
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto sentDto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        // User2 accepts
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        RelationshipDto acceptedDto = relationshipService.acceptRequest(sentDto.getUuid());

        assertEquals(RelationshipStatus.CONFIRMED, acceptedDto.getStatus());
        assertNotNull(acceptedDto.getConfirmedAt());
        assertFalse(acceptedDto.isInitiator());
    }

    @Test
    @DisplayName("Only recipient can accept request")
    void testOnlyRecipientCanAccept() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        User user3 = testUsers.get(2);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        // User3 (not involved) tries to accept
        Mockito.doReturn(user3).when(authService).getCurrentUser(true);
        assertThrows(AlovoaException.class, () -> {
            relationshipService.acceptRequest(dto.getUuid());
        });

        // User1 (sender) tries to accept their own request
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        assertThrows(AlovoaException.class, () -> {
            relationshipService.acceptRequest(dto.getUuid());
        });
    }

    @Test
    @DisplayName("Decline relationship request successfully")
    void testDeclineRequest() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        relationshipService.declineRequest(dto.getUuid());

        // Verify it's declined
        Optional<UserRelationship> rel = relationshipRepo.findByUuid(dto.getUuid());
        assertTrue(rel.isPresent());
        assertEquals(RelationshipStatus.DECLINED, rel.get().getStatus());
    }

    @Test
    @DisplayName("Cancel sent request successfully")
    void testCancelRequest() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        relationshipService.cancelRequest(dto.getUuid());

        // Verify it's deleted
        Optional<UserRelationship> rel = relationshipRepo.findByUuid(dto.getUuid());
        assertFalse(rel.isPresent());
    }

    @Test
    @DisplayName("Only sender can cancel request")
    void testOnlySenderCanCancel() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        // User2 (recipient) tries to cancel
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        assertThrows(AlovoaException.class, () -> {
            relationshipService.cancelRequest(dto.getUuid());
        });
    }

    @Test
    @DisplayName("End active relationship successfully")
    void testEndRelationship() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Create and accept relationship
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        relationshipService.acceptRequest(dto.getUuid());

        // User1 ends the relationship
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        relationshipService.endRelationship(dto.getUuid());

        Optional<UserRelationship> rel = relationshipRepo.findByUuid(dto.getUuid());
        assertTrue(rel.isPresent());
        assertEquals(RelationshipStatus.ENDED, rel.get().getStatus());
    }

    @Test
    @DisplayName("Either partner can end relationship")
    void testEitherPartnerCanEnd() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        relationshipService.acceptRequest(dto.getUuid());

        // User2 (recipient) ends the relationship - should work
        relationshipService.endRelationship(dto.getUuid());

        Optional<UserRelationship> rel = relationshipRepo.findByUuid(dto.getUuid());
        assertEquals(RelationshipStatus.ENDED, rel.get().getStatus());
    }

    @Test
    @DisplayName("Update relationship type")
    void testUpdateRelationshipType() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        relationshipService.acceptRequest(dto.getUuid());

        // Update from DATING to ENGAGED
        RelationshipDto updated = relationshipService.updateRelationshipType(dto.getUuid(), RelationshipType.ENGAGED);
        assertEquals(RelationshipType.ENGAGED, updated.getType());
    }

    @Test
    @DisplayName("Toggle relationship visibility")
    void testToggleVisibility() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        relationshipService.acceptRequest(dto.getUuid());

        // Default should be public
        assertTrue(dto.getIsPublic());

        // Toggle to private
        RelationshipDto toggled = relationshipService.toggleVisibility(dto.getUuid());
        assertFalse(toggled.getIsPublic());

        // Toggle back to public
        toggled = relationshipService.toggleVisibility(dto.getUuid());
        assertTrue(toggled.getIsPublic());
    }

    @Test
    @DisplayName("Get pending requests for user")
    void testGetPendingRequests() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        // Check from user2's perspective
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        List<RelationshipDto> pending = relationshipService.getPendingRequests();

        assertEquals(1, pending.size());
        assertEquals(user1.getUuid(), pending.get(0).getPartnerUuid());
    }

    @Test
    @DisplayName("Get sent requests for user")
    void testGetSentRequests() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        List<RelationshipDto> sent = relationshipService.getSentRequests();
        assertEquals(1, sent.size());
        assertEquals(user2.getUuid(), sent.get(0).getPartnerUuid());
    }

    @Test
    @DisplayName("Get my relationship returns active relationship")
    void testGetMyRelationship() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.MARRIED, null);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        relationshipService.acceptRequest(dto.getUuid());

        // Check from user1's perspective
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        Optional<RelationshipDto> myRel = relationshipService.getMyRelationship();

        assertTrue(myRel.isPresent());
        assertEquals(RelationshipType.MARRIED, myRel.get().getType());
        assertEquals(user2.getUuid(), myRel.get().getPartnerUuid());
    }

    @Test
    @DisplayName("Cannot have multiple active relationships")
    void testCannotHaveMultipleRelationships() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        User user3 = testUsers.get(2);

        // User1 and User2 get into a relationship
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), RelationshipType.DATING, null);

        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        relationshipService.acceptRequest(dto.getUuid());

        // User1 tries to start another relationship with User3
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        assertThrows(AlovoaException.class, () -> {
            relationshipService.sendRequest(user3.getUuid(), RelationshipType.DATING, null);
        });
    }

    @Test
    @DisplayName("All relationship types are valid")
    void testAllRelationshipTypes() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        for (RelationshipType type : RelationshipType.values()) {
            // Clean up any existing relationships
            relationshipRepo.deleteAll();

            Mockito.doReturn(user1).when(authService).getCurrentUser(true);
            RelationshipDto dto = relationshipService.sendRequest(user2.getUuid(), type, null);

            assertNotNull(dto);
            assertEquals(type, dto.getType());
            assertNotNull(dto.getTypeDisplayName());
        }
    }
}
