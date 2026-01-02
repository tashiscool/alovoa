package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.Tools;
import com.nonononoki.alovoa.entity.Captcha;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserDeleteToken;
import com.nonononoki.alovoa.model.RegisterDto;
import com.nonononoki.alovoa.model.UserDeleteAccountDto;
import com.nonononoki.alovoa.repo.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class RegisterServiceTest {

	@Autowired
	private CaptchaService captchaService;

	@Autowired
	private RegisterService registerService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepo;

	@Autowired
	private UserLikeRepository userLikeRepo;

	@Autowired
	private UserHideRepository userHideRepo;

	@Autowired
	private UserBlockRepository userBlockRepo;

	@Autowired
	private UserReportRepository userReportRepo;

	@Autowired
	private ConversationRepository conversationRepo;

	@Autowired
	private MessageRepository messageRepo;

	@Value("${app.age.min}")
	private int minAge;

	@Value("${app.age.max}")
	private int maxAge;

	@Value("${app.message.size}")
	private int maxMessageSize;

	@Value("${app.conversation.messages-max}")
	private int maxConvoMessages;

	@Value("${app.first-name.length-max}")
	private int firstNameLengthMax;

	@Value("${app.first-name.length-min}")
	private int firstNameLengthMin;

	@MockitoBean
	private AuthService authService;

	// Note: Do NOT use static caching for testUsers!
	// Tests use @Transactional which rolls back after each test.
	// A static cache would hold stale references to users that no longer exist.

	private static final int USER1_AGE = 18;
	private static final int USER2_AGE = 20;
	private static final int USER3_AGE = 30;

	@Test
	void test() throws Exception {

		// Get initial count (may vary based on test order)
		long initialCount = userRepo.count();

		List<User> testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax, firstNameLengthMin);

		// Verify 3 test users were created
		assertEquals(initialCount + 3, userRepo.count());
		assertEquals(3, testUsers.size());

		// Call the proper deleteAllUsers with the list of users to delete
		RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo, testUsers);

		assertEquals(0, conversationRepo.count());
		assertEquals(0, messageRepo.count());
		assertEquals(0, userHideRepo.count());
		assertEquals(0, userReportRepo.count());
		assertEquals(0, userLikeRepo.count());
		assertEquals(0, userBlockRepo.count());
		// After cleanup, we should be back to initial state
		assertEquals(initialCount, userRepo.count());

	}

	/**
	 * Creates fresh test users for each test invocation.
	 * IMPORTANT: Do NOT cache these users in a static variable!
	 * Tests use @Transactional which rolls back, making cached references stale.
	 */
	public static List<User> getTestUsers(CaptchaService captchaService, RegisterService registerService,
			int firstNameLengthMax, int firstNameLengthMin) throws Exception {
		// Always create fresh users - no static caching!
		// Each test runs in its own transaction that gets rolled back.

		Captcha c2 = captchaService.generate();
		RegisterDto user2Dto = createTestUserDto(2, c2, "test2", USER2_AGE);
		String tokenContent2 = registerService.register(user2Dto);
		User user2 = registerService.registerConfirm(tokenContent2);

		String user1Email = "non0" + "nonoki@gmail.com";
		// register and confirm test users
		Captcha c1 = captchaService.generate();
		RegisterDto user1Dto = createTestUserDto(1, c1, user1Email, USER1_AGE);
		String tokenContent1 = registerService.register(user1Dto);
		User user1 = registerService.registerConfirm(tokenContent1);

		Captcha c3 = captchaService.generate();
		RegisterDto user3Dto = createTestUserDto(2, c3, "test3", USER3_AGE);
		String tokenContent3 = registerService.register(user3Dto);
		User user3 = registerService.registerConfirm(tokenContent3);

		List<User> testUsers = new ArrayList<>();
		testUsers.add(user1);
		testUsers.add(user2);
		testUsers.add(user3);

		return testUsers;
	}

	public static List<User> createMockNewUsers(CaptchaService captchaService, RegisterService registerService,
											int numberOfUsersToCreate) throws Exception {
		List<User> newUsers = new ArrayList<>();

		for (int i = 0; i < numberOfUsersToCreate; ++i) {
			String userEmail = "non0" + i + "nonoki@gmail.com";
			// register and confirm test users
			Captcha c1 = captchaService.generate();
			RegisterDto user1Dto = createTestUserDto(1, c1, userEmail, USER1_AGE + i);
			String tokenContent1 = registerService.register(user1Dto);
			User user = registerService.registerConfirm(tokenContent1);
			newUsers.add(user);
		}

		return newUsers;
	}

	/**
	 * Deletes all users from the provided list.
	 * Note: With @Transactional tests, this is typically not needed as the
	 * transaction is rolled back automatically. But provided for completeness.
	 */
	public static void deleteAllUsers(UserService userService, AuthService authService, CaptchaService captchaService,
			ConversationRepository conversationRepo, UserRepository userRepo, List<User> usersToDelete) throws Exception {
		if (usersToDelete != null) {
			for (User user : usersToDelete) {
				if (userRepo.existsById(user.getId())) {
					deleteGivenUser(user, userService, userRepo, captchaService, authService);
				}
			}
		}
	}

	/**
	 * @deprecated Use deleteAllUsers(... , List<User> usersToDelete) instead.
	 * This version is kept for backward compatibility but does nothing since
	 * we no longer use static caching.
	 */
	@Deprecated
	public static void deleteAllUsers(UserService userService, AuthService authService, CaptchaService captchaService,
			ConversationRepository conversationRepo, UserRepository userRepo) throws Exception {
		// No-op: With @Transactional tests, cleanup happens automatically via rollback.
		// The static testUsers cache has been removed.
	}

	public static void deleteGivenUser(User user, final UserService userService, final UserRepository userRepo,
								   final CaptchaService captchaService, final AuthService authService) throws Exception {
		if (!user.isAdmin()) {
			user = userRepo.findById(user.getId()).get();
			Mockito.when(authService.getCurrentUser()).thenReturn(user);
			Mockito.doReturn(user).when(authService).getCurrentUser(true);
			UserDeleteToken token = userService.deleteAccountRequest();
			UserDeleteAccountDto dto = new UserDeleteAccountDto();
			Captcha captcha = captchaService.generate();
			dto.setCaptchaId(captcha.getId());
			dto.setCaptchaText(captcha.getText());
			dto.setConfirm(true);
			dto.setEmail(user.getEmail());
			dto.setTokenString(token.getContent());
			userService.deleteAccountConfirm(dto);
		}
	}

	private static RegisterDto createTestUserDto(long gender, Captcha c, String email, int age) throws IOException {
		RegisterDto dto = new RegisterDto();

		if (!email.contains("@")) {
			dto.setEmail(email + Tools.MAIL_TEST_DOMAIN);
		} else {
			dto.setEmail(email);
		}
		dto.setDateOfBirth(Tools.ageToDate(age));
		dto.setPassword("test123");
		dto.setFirstName("test");
		dto.setGender(gender);
		dto.setTermsConditions(true);
		dto.setPrivacy(true);
		return dto;
	}
}
