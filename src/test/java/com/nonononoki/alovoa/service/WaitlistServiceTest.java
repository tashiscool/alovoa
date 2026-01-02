package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.WaitlistEntry;
import com.nonononoki.alovoa.entity.WaitlistEntry.*;
import com.nonononoki.alovoa.repo.WaitlistEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for WaitlistService.
 *
 * Coverage:
 * - Waitlist registration (signup)
 * - Position tracking in queue
 * - Invite processing and batch sending
 * - Referral system and priority scoring
 * - Market threshold tracking
 * - Statistics and analytics
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WaitlistServiceTest {

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private WaitlistEntryRepository waitlistRepo;

    @MockitoBean
    private MailService mailService;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        waitlistRepo.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        waitlistRepo.deleteAll();
    }

    // ============================================
    // Signup Tests
    // ============================================

    @Test
    void testSignup_BasicSignup_Success() {
        WaitlistEntry entry = waitlistService.signup(
                "test@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null,
                "landing-page",
                "google",
                "cpc",
                "spring-launch"
        );

        assertNotNull(entry);
        assertEquals("test@example.com", entry.getEmail());
        assertEquals(Gender.WOMAN, entry.getGender());
        assertEquals(Seeking.MEN, entry.getSeeking());
        assertEquals(Location.DC, entry.getLocation());
        assertEquals(Status.PENDING, entry.getStatus());
        assertNotNull(entry.getInviteCode());
        assertEquals(3, entry.getInviteCodesRemaining());
        assertEquals(100, entry.getPriorityScore()); // Women get +100 priority
        assertNotNull(entry.getSignedUpAt());
    }

    @Test
    void testSignup_DuplicateEmail_ReturnsExisting() {
        // First signup
        WaitlistEntry entry1 = waitlistService.signup(
                "duplicate@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.ARLINGTON,
                null, null, null, null, null
        );

        // Duplicate signup
        WaitlistEntry entry2 = waitlistService.signup(
                "duplicate@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                null, null, null, null, null
        );

        assertNotNull(entry1);
        assertNotNull(entry2);
        assertEquals(entry1.getId(), entry2.getId());
        assertEquals(entry1.getInviteCode(), entry2.getInviteCode());
        assertEquals(1, waitlistRepo.count());
    }

    @Test
    void testSignup_EmailNormalization_LowerCase() {
        WaitlistEntry entry = waitlistService.signup(
                "Test@EXAMPLE.COM",
                Gender.MAN,
                Seeking.EVERYONE,
                Location.DC,
                null, null, null, null, null
        );

        assertEquals("test@example.com", entry.getEmail());
    }

    @Test
    void testSignup_ManPriority_NoBonus() {
        WaitlistEntry entry = waitlistService.signup(
                "man@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                null, null, null, null, null
        );

        assertEquals(0, entry.getPriorityScore()); // Men get no initial bonus
    }

    @Test
    void testSignup_WomanPriority_GetsBonus() {
        WaitlistEntry entry = waitlistService.signup(
                "woman@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null, null, null, null, null
        );

        assertEquals(100, entry.getPriorityScore()); // Women get +100
    }

    @Test
    void testSignup_UtmTracking_SavesParams() {
        WaitlistEntry entry = waitlistService.signup(
                "utm@example.com",
                Gender.NONBINARY,
                Seeking.EVERYONE,
                Location.DC,
                null,
                "instagram",
                "instagram",
                "social",
                "valentines-2024"
        );

        assertEquals("instagram", entry.getSource());
        assertEquals("instagram", entry.getUtmSource());
        assertEquals("social", entry.getUtmMedium());
        assertEquals("valentines-2024", entry.getUtmCampaign());
    }

    // ============================================
    // Referral System Tests
    // ============================================

    @Test
    void testSignup_WithValidReferral_BoostsReferrer() {
        // Create referrer
        WaitlistEntry referrer = waitlistService.signup(
                "referrer@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null, null, null, null, null
        );

        int initialPriority = referrer.getPriorityScore();
        String referralCode = referrer.getInviteCode();

        // Sign up with referral
        WaitlistEntry referred = waitlistService.signup(
                "referred@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                referralCode,
                null, null, null, null
        );

        // Verify referral was recorded
        assertEquals(referralCode, referred.getReferredBy());

        // Verify referrer got priority boost
        WaitlistEntry updatedReferrer = waitlistRepo.findById(referrer.getId()).orElseThrow();
        assertTrue(updatedReferrer.getPriorityScore() > initialPriority);
        assertEquals(initialPriority + 10, updatedReferrer.getPriorityScore()); // Default referral bonus is 10
    }

    @Test
    void testSignup_WithInvalidReferral_NoBoost() {
        WaitlistEntry entry = waitlistService.signup(
                "test@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                "INVALID-CODE",
                null, null, null, null
        );

        assertEquals("INVALID-CODE", entry.getReferredBy());
        // No exception thrown, just no boost applied
    }

    @Test
    void testSignup_ReferralCodeCaseInsensitive() {
        // Create referrer
        WaitlistEntry referrer = waitlistService.signup(
                "referrer@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null, null, null, null, null
        );

        String referralCode = referrer.getInviteCode();
        int initialPriority = referrer.getPriorityScore();

        // Sign up with lowercase referral code
        WaitlistEntry referred = waitlistService.signup(
                "referred@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                referralCode.toLowerCase(),
                null, null, null, null
        );

        // Verify referral worked
        assertEquals(referralCode.toUpperCase(), referred.getReferredBy());

        WaitlistEntry updatedReferrer = waitlistRepo.findById(referrer.getId()).orElseThrow();
        assertEquals(initialPriority + 10, updatedReferrer.getPriorityScore());
    }

    @Test
    void testGetReferralCount() {
        // Create referrer
        WaitlistEntry referrer = waitlistService.signup(
                "referrer@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null, null, null, null, null
        );

        String referralCode = referrer.getInviteCode();

        // Initially no referrals
        assertEquals(0, waitlistService.getReferralCount(referrer));

        // Add 3 referrals
        for (int i = 0; i < 3; i++) {
            waitlistService.signup(
                    "referred" + i + "@example.com",
                    Gender.MAN,
                    Seeking.WOMEN,
                    Location.DC,
                    referralCode,
                    null, null, null, null
            );
        }

        assertEquals(3, waitlistService.getReferralCount(referrer));
    }

    // ============================================
    // Position Tracking Tests
    // ============================================

    @Test
    void testGetPositionInLine_SingleEntry() {
        WaitlistEntry entry = waitlistService.signup(
                "first@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                null, null, null, null, null
        );

        assertEquals(1, waitlistService.getPositionInLine(entry));
    }

    @Test
    void testGetPositionInLine_WomenGetPriority() throws InterruptedException {
        // Sign up man first
        WaitlistEntry man = waitlistService.signup(
                "man@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                null, null, null, null, null
        );

        Thread.sleep(10); // Ensure different timestamps

        // Sign up woman second
        WaitlistEntry woman = waitlistService.signup(
                "woman@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null, null, null, null, null
        );

        // Woman should be ahead despite signing up later
        assertEquals(1, waitlistService.getPositionInLine(woman));
        assertEquals(2, waitlistService.getPositionInLine(man));
    }

    @Test
    void testGetPositionInLine_ReferralsGetPriority() throws InterruptedException {
        // Create referrer with referrals
        WaitlistEntry referrer = waitlistService.signup(
                "referrer@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                null, null, null, null, null
        );

        // Add referral to boost priority
        waitlistService.signup(
                "friend@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                referrer.getInviteCode(),
                null, null, null, null
        );

        Thread.sleep(10);

        // Regular signup after
        WaitlistEntry regular = waitlistService.signup(
                "regular@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                null, null, null, null, null
        );

        // Referrer should be ahead
        WaitlistEntry updatedReferrer = waitlistRepo.findById(referrer.getId()).orElseThrow();
        assertTrue(waitlistService.getPositionInLine(updatedReferrer) <
                   waitlistService.getPositionInLine(regular));
    }

    @Test
    void testGetPositionInLine_InvitedStatus_ReturnsZero() {
        WaitlistEntry entry = waitlistService.signup(
                "invited@example.com",
                Gender.MAN,
                Seeking.WOMEN,
                Location.DC,
                null, null, null, null, null
        );

        // Mark as invited
        entry.setStatus(Status.INVITED);
        waitlistRepo.save(entry);

        assertEquals(0, waitlistService.getPositionInLine(entry));
    }

    // ============================================
    // Invite Processing Tests
    // ============================================

    @Test
    void testSendInviteBatch_SendsToTopPriority() {
        // Create 5 entries
        for (int i = 0; i < 5; i++) {
            waitlistService.signup(
                    "user" + i + "@example.com",
                    Gender.MAN,
                    Seeking.WOMEN,
                    Location.DC,
                    null, null, null, null, null
            );
        }

        // Send batch of 3
        List<WaitlistEntry> invited = waitlistService.sendInviteBatch(3);

        assertEquals(3, invited.size());

        // All invited should have INVITED status
        for (WaitlistEntry entry : invited) {
            assertEquals(Status.INVITED, entry.getStatus());
            assertNotNull(entry.getInvitedAt());
        }

        // Verify 2 still pending
        assertEquals(2, waitlistRepo.countByStatus(Status.PENDING));
    }

    @Test
    void testSendInviteBatch_EmptyWaitlist_ReturnsEmpty() {
        List<WaitlistEntry> invited = waitlistService.sendInviteBatch(10);
        assertTrue(invited.isEmpty());
    }

    @Test
    void testMarkRegistered() {
        WaitlistEntry entry = waitlistService.signup(
                "register@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null, null, null, null, null
        );

        // Mark as invited first
        entry.setStatus(Status.INVITED);
        waitlistRepo.save(entry);

        // Mark as registered
        waitlistService.markRegistered("register@example.com");

        WaitlistEntry updated = waitlistRepo.findByEmail("register@example.com").orElseThrow();
        assertEquals(Status.REGISTERED, updated.getStatus());
        assertNotNull(updated.getRegisteredAt());
    }

    @Test
    void testMarkRegistered_EmailNormalization() {
        waitlistService.signup(
                "register@example.com",
                Gender.WOMAN,
                Seeking.MEN,
                Location.DC,
                null, null, null, null, null
        );

        // Mark with different case
        waitlistService.markRegistered("REGISTER@EXAMPLE.COM");

        WaitlistEntry updated = waitlistRepo.findByEmail("register@example.com").orElseThrow();
        assertEquals(Status.REGISTERED, updated.getStatus());
    }

    // ============================================
    // Market Threshold Tests
    // ============================================

    @Test
    void testGetMarketStatus_NotReady() {
        // Add some users but not enough
        for (int i = 0; i < 50; i++) {
            waitlistService.signup(
                    "woman" + i + "@example.com",
                    Gender.WOMAN,
                    Seeking.MEN,
                    Location.DC,
                    null, null, null, null, null
            );
        }

        WaitlistService.MarketStatus status = waitlistService.getMarketStatus(Location.DC);

        assertEquals(Location.DC, status.location);
        assertEquals(50, status.womenCount);
        assertEquals(0, status.menCount);
        assertEquals(50, status.totalCount);
        assertFalse(status.readyToOpen);
        assertTrue(status.womenNeeded > 0);
        assertTrue(status.menNeeded > 0);
    }

    @Test
    void testGetMarketStatus_ReadyToOpen() {
        // Add 200 women
        for (int i = 0; i < 200; i++) {
            waitlistService.signup(
                    "woman" + i + "@example.com",
                    Gender.WOMAN,
                    Seeking.MEN,
                    Location.DC,
                    null, null, null, null, null
            );
        }

        // Add 300 men (total 500)
        for (int i = 0; i < 300; i++) {
            waitlistService.signup(
                    "man" + i + "@example.com",
                    Gender.MAN,
                    Seeking.WOMEN,
                    Location.DC,
                    null, null, null, null, null
            );
        }

        WaitlistService.MarketStatus status = waitlistService.getMarketStatus(Location.DC);

        assertTrue(status.readyToOpen);
        assertEquals(200, status.womenCount);
        assertEquals(300, status.menCount);
        assertEquals(500, status.totalCount);
        assertEquals(0, status.womenNeeded);
        assertEquals(0, status.menNeeded);
        assertEquals(0, status.totalNeeded);
        assertEquals(100.0, status.percentReady, 0.1);
    }

    @Test
    void testGetAllMarketStats() {
        // Add entries to multiple markets
        waitlistService.signup("dc@example.com", Gender.WOMAN, Seeking.MEN, Location.DC, null, null, null, null, null);
        waitlistService.signup("arlington@example.com", Gender.WOMAN, Seeking.MEN, Location.ARLINGTON, null, null, null, null, null);

        List<WaitlistService.MarketStatus> markets = waitlistService.getAllMarketStats();

        // Should return all locations
        assertEquals(Location.values().length, markets.size());

        // Should be sorted by percent ready
        for (int i = 0; i < markets.size() - 1; i++) {
            assertTrue(markets.get(i).percentReady >= markets.get(i + 1).percentReady);
        }
    }

    // ============================================
    // Statistics Tests
    // ============================================

    @Test
    void testGetStats_Empty() {
        Map<String, Object> stats = waitlistService.getStats();

        assertEquals(0L, stats.get("total"));
        assertEquals(0L, stats.get("pending"));
        assertEquals(0L, stats.get("invited"));
        assertEquals(0L, stats.get("registered"));
    }

    @Test
    void testGetStats_WithEntries() {
        // Create entries with different statuses
        WaitlistEntry entry1 = waitlistService.signup("pending@example.com", Gender.WOMAN, Seeking.MEN, Location.DC, null, null, null, null, null);

        WaitlistEntry entry2 = waitlistService.signup("invited@example.com", Gender.MAN, Seeking.WOMEN, Location.DC, null, null, null, null, null);
        entry2.setStatus(Status.INVITED);
        waitlistRepo.save(entry2);

        WaitlistEntry entry3 = waitlistService.signup("registered@example.com", Gender.WOMAN, Seeking.MEN, Location.ARLINGTON, null, null, null, null, null);
        entry3.setStatus(Status.REGISTERED);
        waitlistRepo.save(entry3);

        Map<String, Object> stats = waitlistService.getStats();

        assertEquals(3L, stats.get("total"));
        assertEquals(1L, stats.get("pending"));
        assertEquals(1L, stats.get("invited"));
        assertEquals(1L, stats.get("registered"));

        // Check location breakdown
        @SuppressWarnings("unchecked")
        Map<String, Long> byLocation = (Map<String, Long>) stats.get("byLocation");
        assertEquals(2L, byLocation.get("DC"));
        assertEquals(1L, byLocation.get("ARLINGTON"));

        // Check gender breakdown
        @SuppressWarnings("unchecked")
        Map<String, Long> byGender = (Map<String, Long>) stats.get("byGender");
        assertEquals(2L, byGender.get("WOMAN"));
        assertEquals(1L, byGender.get("MAN"));
    }

    // ============================================
    // Query Tests
    // ============================================

    @Test
    void testGetByEmail() {
        waitlistService.signup("test@example.com", Gender.WOMAN, Seeking.MEN, Location.DC, null, null, null, null, null);

        Optional<WaitlistEntry> entry = waitlistService.getByEmail("test@example.com");
        assertTrue(entry.isPresent());
        assertEquals("test@example.com", entry.get().getEmail());
    }

    @Test
    void testGetByEmail_NotFound() {
        Optional<WaitlistEntry> entry = waitlistService.getByEmail("notfound@example.com");
        assertFalse(entry.isPresent());
    }

    @Test
    void testGetByInviteCode() {
        WaitlistEntry created = waitlistService.signup("test@example.com", Gender.WOMAN, Seeking.MEN, Location.DC, null, null, null, null, null);
        String inviteCode = created.getInviteCode();

        Optional<WaitlistEntry> entry = waitlistService.getByInviteCode(inviteCode);
        assertTrue(entry.isPresent());
        assertEquals(inviteCode, entry.get().getInviteCode());
    }

    @Test
    void testGetByInviteCode_CaseInsensitive() {
        WaitlistEntry created = waitlistService.signup("test@example.com", Gender.WOMAN, Seeking.MEN, Location.DC, null, null, null, null, null);
        String inviteCode = created.getInviteCode();

        Optional<WaitlistEntry> entry = waitlistService.getByInviteCode(inviteCode.toLowerCase());
        assertTrue(entry.isPresent());
        assertEquals(inviteCode, entry.get().getInviteCode());
    }

    // ============================================
    // Market Opening Invite Tests
    // ============================================

    @Test
    void testSendMarketOpeningInvites_MarketNotReady() {
        // Add only a few users
        for (int i = 0; i < 10; i++) {
            waitlistService.signup("user" + i + "@example.com", Gender.WOMAN, Seeking.MEN, Location.DC, null, null, null, null, null);
        }

        List<WaitlistEntry> invited = waitlistService.sendMarketOpeningInvites(Location.DC);

        assertTrue(invited.isEmpty());
    }

    @Test
    void testSendMarketOpeningInvites_WomenGetPriorityInBatch() {
        // Create ready market
        for (int i = 0; i < 200; i++) {
            waitlistService.signup("woman" + i + "@example.com", Gender.WOMAN, Seeking.MEN, Location.DC, null, null, null, null, null);
        }
        for (int i = 0; i < 300; i++) {
            waitlistService.signup("man" + i + "@example.com", Gender.MAN, Seeking.WOMEN, Location.DC, null, null, null, null, null);
        }

        List<WaitlistEntry> invited = waitlistService.sendMarketOpeningInvites(Location.DC);

        assertTrue(invited.size() > 0);

        // First batch should be mostly women
        long womenInvited = invited.stream()
                .filter(e -> e.getGender() == Gender.WOMAN)
                .count();

        assertTrue(womenInvited > 0);
    }
}
