package com.nonononoki.alovoa.integration;

import com.nonononoki.alovoa.Tools;
import com.nonononoki.alovoa.entity.Captcha;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.RegisterDto;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.CaptchaService;
import com.nonononoki.alovoa.service.RegisterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for user registration using Testcontainers.
 * Tests the full registration flow with a real MariaDB database.
 */
@SpringBootTest
@Transactional
@EnabledIf("com.nonononoki.alovoa.integration.BaseIntegrationTest#isDockerAvailable")
public class UserRegistrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @Test
    void testDatabaseConnectionWorks() throws Exception {
        // Verify database is accessible and admin user exists
        long userCount = userRepository.count();
        assertTrue(userCount >= 1, "At least admin user should exist");
    }

    @Test
    void testUserRegistrationFlow() throws Exception {
        // Given
        long initialCount = userRepository.count();

        Captcha captcha = captchaService.generate();
        RegisterDto registerDto = createRegisterDto(
            "integration-test" + Tools.MAIL_TEST_DOMAIN,
            25,
            1,
            captcha
        );

        // When - Register user
        String tokenContent = registerService.register(registerDto);
        assertNotNull(tokenContent, "Token should be returned");

        // When - Confirm registration
        User confirmedUser = registerService.registerConfirm(tokenContent);

        // Then
        assertNotNull(confirmedUser, "User should be confirmed");
        assertNotNull(confirmedUser.getId(), "User should have an ID");
        assertEquals("integration-test" + Tools.MAIL_TEST_DOMAIN, confirmedUser.getEmail());
        assertEquals(initialCount + 1, userRepository.count(), "User count should increase");
    }

    @Test
    void testDuplicateEmailRejected() throws Exception {
        // Given - First registration
        Captcha captcha1 = captchaService.generate();
        RegisterDto registerDto1 = createRegisterDto(
            "duplicate-test" + Tools.MAIL_TEST_DOMAIN,
            25,
            1,
            captcha1
        );
        String token1 = registerService.register(registerDto1);
        registerService.registerConfirm(token1);

        // When - Try to register with same email
        Captcha captcha2 = captchaService.generate();
        RegisterDto registerDto2 = createRegisterDto(
            "duplicate-test" + Tools.MAIL_TEST_DOMAIN,
            26,
            2,
            captcha2
        );

        // Then - Should fail
        assertThrows(Exception.class, () -> registerService.register(registerDto2));
    }

    @Test
    void testMinAgeRequirement() throws Exception {
        // Given - User under minimum age (app.age.min=16)
        Captcha captcha = captchaService.generate();
        RegisterDto registerDto = createRegisterDto(
            "underage-test" + Tools.MAIL_TEST_DOMAIN,
            15, // Under 16 (the configured minimum)
            1,
            captcha
        );

        // Then - Should fail
        assertThrows(Exception.class, () -> registerService.register(registerDto));
    }

    @Test
    void testCaptchaValidation() throws Exception {
        // Given - Invalid captcha
        RegisterDto registerDto = new RegisterDto();
        registerDto.setEmail("captcha-test" + Tools.MAIL_TEST_DOMAIN);
        registerDto.setDateOfBirth(Tools.ageToDate(25));
        registerDto.setPassword("password123");
        registerDto.setFirstName("Test");
        registerDto.setGender(1L);
        registerDto.setTermsConditions(true);
        registerDto.setPrivacy(true);
        // Don't set captcha - should fail

        // Then - Should fail
        assertThrows(Exception.class, () -> registerService.register(registerDto));
    }

    private RegisterDto createRegisterDto(String email, int age, long gender, Captcha captcha) {
        RegisterDto dto = new RegisterDto();
        dto.setEmail(email);
        dto.setDateOfBirth(Tools.ageToDate(age));
        dto.setPassword("testPassword123");
        dto.setFirstName("TestUser");
        dto.setGender(gender);
        dto.setTermsConditions(true);
        dto.setPrivacy(true);
        dto.setCaptchaId(captcha.getId());
        dto.setCaptchaText(captcha.getText());
        return dto;
    }
}
