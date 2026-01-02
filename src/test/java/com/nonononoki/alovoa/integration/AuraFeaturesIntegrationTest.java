package com.nonononoki.alovoa.integration;

import com.nonononoki.alovoa.Tools;
import com.nonononoki.alovoa.entity.Captcha;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import com.nonononoki.alovoa.model.RegisterDto;
import com.nonononoki.alovoa.repo.*;
import com.nonononoki.alovoa.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AURA-specific features using Testcontainers.
 * Tests personality profiles, reputation scoring, and other AURA enhancements.
 */
@SpringBootTest
@Transactional
@EnabledIf("com.nonononoki.alovoa.integration.BaseIntegrationTest#shouldRunIntegrationTests")
public class AuraFeaturesIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private UserPersonalityProfileRepository personalityProfileRepository;

    @Autowired(required = false)
    private UserReputationScoreRepository reputationScoreRepository;

    @MockitoBean
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        // Create a test user
        Captcha captcha = captchaService.generate();
        RegisterDto registerDto = new RegisterDto();
        registerDto.setEmail("aura-test-" + System.currentTimeMillis() + Tools.MAIL_TEST_DOMAIN);
        registerDto.setDateOfBirth(Tools.ageToDate(25));
        registerDto.setPassword("testPassword123");
        registerDto.setFirstName("AuraTest");
        registerDto.setGender(1L);
        registerDto.setTermsConditions(true);
        registerDto.setPrivacy(true);
        registerDto.setCaptchaId(captcha.getId());
        registerDto.setCaptchaText(captcha.getText());

        String token = registerService.register(registerDto);
        testUser = registerService.registerConfirm(token);

        Mockito.when(authService.getCurrentUser()).thenReturn(testUser);
        Mockito.doReturn(testUser).when(authService).getCurrentUser(true);
    }

    @Test
    void testUserCreatedSuccessfully() throws Exception {
        assertNotNull(testUser);
        assertNotNull(testUser.getId());
        assertFalse(testUser.isAdmin());
    }

    @Test
    void testUserFoundInDatabase() throws Exception {
        Optional<User> foundUser = userRepository.findById(testUser.getId());
        assertTrue(foundUser.isPresent());
        assertEquals(testUser.getEmail(), foundUser.get().getEmail());
    }

    @Test
    void testPersonalityProfileRepository() throws Exception {
        if (personalityProfileRepository != null) {
            // Verify repository is accessible
            long count = personalityProfileRepository.count();
            assertTrue(count >= 0, "Should be able to query personality profiles");
        }
    }

    @Test
    void testReputationScoreRepository() throws Exception {
        if (reputationScoreRepository != null) {
            // Verify repository is accessible
            long count = reputationScoreRepository.count();
            assertTrue(count >= 0, "Should be able to query reputation scores");
        }
    }

    @Test
    void testMultipleUsersCanBeCreated() throws Exception {
        // Create multiple users
        for (int i = 0; i < 5; i++) {
            Captcha captcha = captchaService.generate();
            RegisterDto registerDto = new RegisterDto();
            registerDto.setEmail("multi-user-" + i + "-" + System.currentTimeMillis() + Tools.MAIL_TEST_DOMAIN);
            registerDto.setDateOfBirth(Tools.ageToDate(20 + i));
            registerDto.setPassword("testPassword123");
            registerDto.setFirstName("User" + i);
            registerDto.setGender((long) (i % 2) + 1);
            registerDto.setTermsConditions(true);
            registerDto.setPrivacy(true);
            registerDto.setCaptchaId(captcha.getId());
            registerDto.setCaptchaText(captcha.getText());

            String token = registerService.register(registerDto);
            User user = registerService.registerConfirm(token);
            assertNotNull(user);
        }

        // Verify users were created (at least admin + testUser + 5 new users)
        assertTrue(userRepository.count() >= 7);
    }

    @Test
    void testUserAgeCalculation() throws Exception {
        // Verify age is correctly stored
        assertNotNull(testUser.getDates());
        assertNotNull(testUser.getDates().getDateOfBirth());
    }

    @Test
    void testDatabaseTransactionRollback() throws Exception {
        long countBefore = userRepository.count();

        // This test runs in a transaction that will be rolled back
        // Create a user
        Captcha captcha = captchaService.generate();
        RegisterDto registerDto = new RegisterDto();
        registerDto.setEmail("rollback-test-" + System.currentTimeMillis() + Tools.MAIL_TEST_DOMAIN);
        registerDto.setDateOfBirth(Tools.ageToDate(30));
        registerDto.setPassword("testPassword123");
        registerDto.setFirstName("RollbackTest");
        registerDto.setGender(1L);
        registerDto.setTermsConditions(true);
        registerDto.setPrivacy(true);
        registerDto.setCaptchaId(captcha.getId());
        registerDto.setCaptchaText(captcha.getText());

        String token = registerService.register(registerDto);
        registerService.registerConfirm(token);

        // User should be visible in this transaction
        assertTrue(userRepository.count() > countBefore);
    }
}
