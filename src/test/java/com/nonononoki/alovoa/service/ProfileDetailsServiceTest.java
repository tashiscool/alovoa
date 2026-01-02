package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserProfileDetails;
import com.nonononoki.alovoa.entity.user.UserProfileDetails.*;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserProfileDetailsRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileDetailsServiceTest {

    @Autowired
    private ProfileDetailsService profileDetailsService;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserProfileDetailsRepository profileDetailsRepo;

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
    @DisplayName("Get or create profile details")
    void testGetOrCreateProfileDetails() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        UserProfileDetails details = profileDetailsService.getOrCreateProfileDetails();

        assertNotNull(details);
        assertEquals(user.getId(), details.getUser().getId());
    }

    @Test
    @DisplayName("Update height in centimeters")
    void testUpdateHeight() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        UserProfileDetails details = profileDetailsService.updateHeight(175);

        assertEquals(175, details.getHeightCm());
    }

    @Test
    @DisplayName("Height - low value")
    void testHeightMinimum() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        // Service accepts any value - no validation
        UserProfileDetails details = profileDetailsService.updateHeight(50);
        assertEquals(50, details.getHeightCm());
    }

    @Test
    @DisplayName("Height - high value")
    void testHeightMaximum() throws Exception {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        // Service accepts any value - no validation
        UserProfileDetails details = profileDetailsService.updateHeight(300);
        assertEquals(300, details.getHeightCm());
    }

    @Test
    @DisplayName("Update body type")
    void testUpdateBodyType() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        for (BodyType bodyType : BodyType.values()) {
            UserProfileDetails details = profileDetailsService.updateBodyType(bodyType);
            assertEquals(bodyType, details.getBodyType());
        }
    }

    @Test
    @DisplayName("Update ethnicity")
    void testUpdateEthnicity() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        for (Ethnicity ethnicity : Ethnicity.values()) {
            UserProfileDetails details = profileDetailsService.updateEthnicity(ethnicity);
            assertEquals(ethnicity, details.getEthnicity());
        }
    }

    @Test
    @DisplayName("Update diet")
    void testUpdateDiet() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        for (Diet diet : Diet.values()) {
            UserProfileDetails details = profileDetailsService.updateDiet(diet);
            assertEquals(diet, details.getDiet());
        }
    }

    @Test
    @DisplayName("Update education level")
    void testUpdateEducation() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        for (EducationLevel education : EducationLevel.values()) {
            UserProfileDetails details = profileDetailsService.updateEducation(education);
            assertEquals(education, details.getEducation());
        }
    }

    @Test
    @DisplayName("Update income level")
    void testUpdateIncome() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        for (IncomeLevel income : IncomeLevel.values()) {
            UserProfileDetails details = profileDetailsService.updateIncome(income);
            assertEquals(income, details.getIncome());
        }
    }

    @Test
    @DisplayName("All income levels exist including high brackets")
    void testIncomeLevelsExist() throws Exception {
        // Verify all expected income levels exist
        assertNotNull(IncomeLevel.LESS_THAN_20K);
        assertNotNull(IncomeLevel.INCOME_20K_40K);
        assertNotNull(IncomeLevel.INCOME_40K_60K);
        assertNotNull(IncomeLevel.INCOME_60K_80K);
        assertNotNull(IncomeLevel.INCOME_80K_100K);
        assertNotNull(IncomeLevel.INCOME_100K_150K);
        assertNotNull(IncomeLevel.INCOME_150K_250K);
        assertNotNull(IncomeLevel.INCOME_250K_500K);
        assertNotNull(IncomeLevel.INCOME_500K_PLUS);
        assertNotNull(IncomeLevel.RATHER_NOT_SAY);

        assertEquals(10, IncomeLevel.values().length);
    }

    @Test
    @DisplayName("Update zodiac sign")
    void testUpdateZodiac() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        for (ZodiacSign zodiac : ZodiacSign.values()) {
            UserProfileDetails details = profileDetailsService.updateZodiacSign(zodiac);
            assertEquals(zodiac, details.getZodiacSign());
        }
    }

    @Test
    @DisplayName("All zodiac signs exist")
    void testAllZodiacSigns() throws Exception {
        assertEquals(12, ZodiacSign.values().length);

        assertNotNull(ZodiacSign.ARIES);
        assertNotNull(ZodiacSign.TAURUS);
        assertNotNull(ZodiacSign.GEMINI);
        assertNotNull(ZodiacSign.CANCER);
        assertNotNull(ZodiacSign.LEO);
        assertNotNull(ZodiacSign.VIRGO);
        assertNotNull(ZodiacSign.LIBRA);
        assertNotNull(ZodiacSign.SCORPIO);
        assertNotNull(ZodiacSign.SAGITTARIUS);
        assertNotNull(ZodiacSign.CAPRICORN);
        assertNotNull(ZodiacSign.AQUARIUS);
        assertNotNull(ZodiacSign.PISCES);
    }

    @Test
    @DisplayName("Update occupation")
    void testUpdateOccupation() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        UserProfileDetails details = profileDetailsService.updateOccupation("Software Engineer", null);
        assertEquals("Software Engineer", details.getOccupation());
    }

    @Test
    @DisplayName("Update employer")
    void testUpdateEmployer() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        UserProfileDetails details = profileDetailsService.updateOccupation(null, "Anthropic");
        assertEquals("Anthropic", details.getEmployer());
    }

    @Test
    @DisplayName("Update languages")
    void testUpdateLanguages() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        UserProfileDetails details = profileDetailsService.updateLanguages("English, Spanish, French");
        assertEquals("English, Spanish, French", details.getLanguages());
    }

    @Test
    @DisplayName("Update has pets")
    void testUpdateHasPets() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        UserProfileDetails details = profileDetailsService.updateHasPets(true);
        assertEquals(UserProfileDetails.PetStatus.HAS_PETS, details.getPets());

        details = profileDetailsService.updateHasPets(false);
        assertEquals(UserProfileDetails.PetStatus.NO_PETS_LIKES_THEM, details.getPets());
    }

    @Test
    @DisplayName("Update pet details")
    void testUpdatePetDetails() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        profileDetailsService.updateHasPets(true);
        UserProfileDetails details = profileDetailsService.updatePetDetails("Two dogs and a cat");

        assertEquals("Two dogs and a cat", details.getPetDetails());
    }

    @Test
    @DisplayName("Clear profile detail field")
    void testClearField() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        // Set height then clear it
        profileDetailsService.updateHeight(180);
        UserProfileDetails details = profileDetailsService.updateHeight(null);

        assertNull(details.getHeightCm());
    }

    @Test
    @DisplayName("Profile details persist across calls")
    void testDetailsPersistence() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.doReturn(user).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user);

        profileDetailsService.updateHeight(175);
        profileDetailsService.updateBodyType(BodyType.FIT);
        profileDetailsService.updateDiet(Diet.VEGETARIAN);

        UserProfileDetails details = profileDetailsService.getOrCreateProfileDetails();

        assertEquals(175, details.getHeightCm());
        assertEquals(BodyType.FIT, details.getBodyType());
        assertEquals(Diet.VEGETARIAN, details.getDiet());
    }

    @Test
    @DisplayName("Multiple users have separate profile details")
    void testSeparateDetails() throws AlovoaException {
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);

        // Update user1's details
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user1);
        profileDetailsService.updateHeight(170);
        profileDetailsService.updateBodyType(BodyType.THIN);

        // Update user2's details
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user2);
        profileDetailsService.updateHeight(185);
        profileDetailsService.updateBodyType(BodyType.CURVY);

        // Verify user1's details
        Mockito.doReturn(user1).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user1);
        UserProfileDetails details1 = profileDetailsService.getOrCreateProfileDetails();
        assertEquals(170, details1.getHeightCm());
        assertEquals(BodyType.THIN, details1.getBodyType());

        // Verify user2's details
        Mockito.doReturn(user2).when(authService).getCurrentUser(true);
        Mockito.when(authService.getCurrentUser()).thenReturn(user2);
        UserProfileDetails details2 = profileDetailsService.getOrCreateProfileDetails();
        assertEquals(185, details2.getHeightCm());
        assertEquals(BodyType.CURVY, details2.getBodyType());
    }

    @Test
    @DisplayName("Body type enum values")
    void testBodyTypeValues() throws Exception {
        assertNotNull(BodyType.THIN);
        assertNotNull(BodyType.FIT);
        assertNotNull(BodyType.AVERAGE);
        assertNotNull(BodyType.CURVY);
        assertNotNull(BodyType.OVERWEIGHT);
        assertNotNull(BodyType.RATHER_NOT_SAY);

        assertTrue(BodyType.values().length >= 6);
    }

    @Test
    @DisplayName("Diet enum values")
    void testDietValues() throws Exception {
        assertNotNull(Diet.OMNIVORE);
        assertNotNull(Diet.VEGETARIAN);
        assertNotNull(Diet.VEGAN);
        assertNotNull(Diet.PESCATARIAN);
        assertNotNull(Diet.KOSHER);
        assertNotNull(Diet.HALAL);
        assertNotNull(Diet.OTHER);

        assertTrue(Diet.values().length >= 7);
    }

    @Test
    @DisplayName("Ethnicity enum values")
    void testEthnicityValues() throws Exception {
        assertNotNull(Ethnicity.ASIAN);
        assertNotNull(Ethnicity.BLACK);
        assertNotNull(Ethnicity.HISPANIC_LATINO);
        assertNotNull(Ethnicity.WHITE);
        assertNotNull(Ethnicity.MIDDLE_EASTERN);
        assertNotNull(Ethnicity.NATIVE_AMERICAN);
        assertNotNull(Ethnicity.PACIFIC_ISLANDER);
        assertNotNull(Ethnicity.MIXED);
        assertNotNull(Ethnicity.OTHER);
        assertNotNull(Ethnicity.RATHER_NOT_SAY);

        assertTrue(Ethnicity.values().length >= 10);
    }

    @Test
    @DisplayName("Education level enum values")
    void testEducationLevelValues() throws Exception {
        assertNotNull(EducationLevel.HIGH_SCHOOL);
        assertNotNull(EducationLevel.SOME_COLLEGE);
        assertNotNull(EducationLevel.ASSOCIATES);
        assertNotNull(EducationLevel.BACHELORS);
        assertNotNull(EducationLevel.MASTERS);
        assertNotNull(EducationLevel.DOCTORATE);
        // PROFESSIONAL removed - use OTHER or RATHER_NOT_SAY instead
        assertNotNull(EducationLevel.TRADE_SCHOOL);
        assertNotNull(EducationLevel.OTHER);

        assertTrue(EducationLevel.values().length >= 9);
    }
}
