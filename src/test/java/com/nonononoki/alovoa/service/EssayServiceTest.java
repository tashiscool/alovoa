package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.EssayPromptTemplate;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.EssayDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.EssayPromptTemplateRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EssayServiceTest {

    @Autowired
    private EssayService essayService;

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
    private EssayPromptTemplateRepository essayTemplateRepo;

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

        // Ensure essay templates exist
        if (essayTemplateRepo.count() == 0) {
            List<EssayPromptTemplate> templates = EssayPromptTemplate.getDefaultTemplates();
            essayTemplateRepo.saveAll(templates);
        }
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    @DisplayName("Get all essay templates returns 10 prompts")
    void testGetAllTemplates() {
        List<EssayPromptTemplate> templates = essayService.getAllTemplates();

        assertEquals(10, templates.size());

        // Verify they are ordered correctly
        for (int i = 0; i < templates.size(); i++) {
            assertEquals(i + 1, templates.get(i).getDisplayOrder());
        }
    }

    @Test
    @DisplayName("Templates have correct content")
    void testTemplateContent() {
        List<EssayPromptTemplate> templates = essayService.getAllTemplates();

        // Check first template (Self Summary)
        EssayPromptTemplate selfSummary = templates.stream()
                .filter(t -> t.getPromptId().equals(EssayPromptTemplate.SELF_SUMMARY))
                .findFirst()
                .orElseThrow();

        assertEquals("My self-summary", selfSummary.getTitle());
        assertNotNull(selfSummary.getPlaceholder());
        assertNotNull(selfSummary.getHelpText());
        assertEquals(0, selfSummary.getMinLength());
        assertEquals(2000, selfSummary.getMaxLength());
    }

    @Test
    @DisplayName("Get user essays returns empty answers initially")
    void testGetUserEssaysEmpty() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        List<EssayDto> essays = essayService.getCurrentUserEssays();

        assertEquals(10, essays.size());
        // All answers should be null initially
        for (EssayDto essay : essays) {
            assertNull(essay.getAnswer());
        }
    }

    @Test
    @DisplayName("Save essay successfully")
    void testSaveEssay() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        String essayText = "This is my self summary. I'm a test user.";
        essayService.saveEssay(EssayPromptTemplate.SELF_SUMMARY, essayText);

        // Retrieve and verify
        List<EssayDto> essays = essayService.getCurrentUserEssays();
        EssayDto selfSummary = essays.stream()
                .filter(e -> e.getPromptId().equals(EssayPromptTemplate.SELF_SUMMARY))
                .findFirst()
                .orElseThrow();

        assertEquals(essayText, selfSummary.getAnswer());
    }

    @Test
    @DisplayName("Update existing essay")
    void testUpdateEssay() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Save initial essay
        essayService.saveEssay(EssayPromptTemplate.DOING_WITH_LIFE, "Original text");

        // Update essay
        String updatedText = "Updated text with more content";
        essayService.saveEssay(EssayPromptTemplate.DOING_WITH_LIFE, updatedText);

        List<EssayDto> essays = essayService.getCurrentUserEssays();
        EssayDto doingWithLife = essays.stream()
                .filter(e -> e.getPromptId().equals(EssayPromptTemplate.DOING_WITH_LIFE))
                .findFirst()
                .orElseThrow();

        assertEquals(updatedText, doingWithLife.getAnswer());
    }

    @Test
    @DisplayName("Delete essay by setting empty text")
    void testDeleteEssay() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Save essay
        essayService.saveEssay(EssayPromptTemplate.REALLY_GOOD_AT, "I'm really good at testing!");

        // Delete by setting empty
        essayService.saveEssay(EssayPromptTemplate.REALLY_GOOD_AT, "");

        List<EssayDto> essays = essayService.getCurrentUserEssays();
        EssayDto reallyGoodAt = essays.stream()
                .filter(e -> e.getPromptId().equals(EssayPromptTemplate.REALLY_GOOD_AT))
                .findFirst()
                .orElseThrow();

        assertNull(reallyGoodAt.getAnswer());
    }

    @Test
    @DisplayName("Delete essay by setting null text")
    void testDeleteEssayNull() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        essayService.saveEssay(EssayPromptTemplate.FAVORITES, "My favorite things");
        essayService.saveEssay(EssayPromptTemplate.FAVORITES, null);

        List<EssayDto> essays = essayService.getCurrentUserEssays();
        EssayDto favorites = essays.stream()
                .filter(e -> e.getPromptId().equals(EssayPromptTemplate.FAVORITES))
                .findFirst()
                .orElseThrow();

        assertNull(favorites.getAnswer());
    }

    @Test
    @DisplayName("Save multiple essays at once")
    void testSaveMultipleEssays() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        Map<Long, String> essays = new HashMap<>();
        essays.put(EssayPromptTemplate.SELF_SUMMARY, "My self summary");
        essays.put(EssayPromptTemplate.DOING_WITH_LIFE, "What I'm doing");
        essays.put(EssayPromptTemplate.FRIDAY_NIGHT, "Friday plans");

        essayService.saveEssays(essays);

        List<EssayDto> savedEssays = essayService.getCurrentUserEssays();

        // Count filled essays
        long filledCount = savedEssays.stream()
                .filter(e -> e.getAnswer() != null)
                .count();

        assertEquals(3, filledCount);
    }

    @Test
    @DisplayName("Invalid prompt ID throws exception")
    void testInvalidPromptId() {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Prompt ID 0 is invalid
        assertThrows(AlovoaException.class, () -> {
            essayService.saveEssay(0L, "Test");
        });

        // Prompt ID 11 is invalid
        assertThrows(AlovoaException.class, () -> {
            essayService.saveEssay(11L, "Test");
        });

        // Negative prompt ID is invalid
        assertThrows(AlovoaException.class, () -> {
            essayService.saveEssay(-1L, "Test");
        });
    }

    @Test
    @DisplayName("Essay too long throws exception")
    void testEssayTooLong() {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create a string longer than 2000 characters
        String longEssay = "x".repeat(2001);

        assertThrows(AlovoaException.class, () -> {
            essayService.saveEssay(EssayPromptTemplate.SELF_SUMMARY, longEssay);
        });
    }

    @Test
    @DisplayName("Essay at max length is accepted")
    void testEssayMaxLength() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create a string exactly 2000 characters
        String maxEssay = "x".repeat(2000);
        essayService.saveEssay(EssayPromptTemplate.SELF_SUMMARY, maxEssay);

        List<EssayDto> essays = essayService.getCurrentUserEssays();
        EssayDto selfSummary = essays.stream()
                .filter(e -> e.getPromptId().equals(EssayPromptTemplate.SELF_SUMMARY))
                .findFirst()
                .orElseThrow();

        assertEquals(2000, selfSummary.getAnswer().length());
    }

    @Test
    @DisplayName("Get filled essay count")
    void testGetFilledEssayCount() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Initially no essays
        assertEquals(0, essayService.getFilledEssayCount(user));

        // Add some essays
        essayService.saveEssay(EssayPromptTemplate.SELF_SUMMARY, "Summary");
        essayService.saveEssay(EssayPromptTemplate.DOING_WITH_LIFE, "Doing stuff");
        essayService.saveEssay(EssayPromptTemplate.REALLY_GOOD_AT, "Testing");

        // Refresh user
        user = userRepo.findById(user.getId()).orElseThrow();
        assertEquals(3, essayService.getFilledEssayCount(user));
    }

    @Test
    @DisplayName("Essay text is trimmed")
    void testEssayTextTrimmed() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        essayService.saveEssay(EssayPromptTemplate.SELF_SUMMARY, "  Text with spaces  ");

        List<EssayDto> essays = essayService.getCurrentUserEssays();
        EssayDto selfSummary = essays.stream()
                .filter(e -> e.getPromptId().equals(EssayPromptTemplate.SELF_SUMMARY))
                .findFirst()
                .orElseThrow();

        assertEquals("Text with spaces", selfSummary.getAnswer());
    }

    @Test
    @DisplayName("All 10 essay prompts can be filled")
    void testAllPromptsCanBeFilled() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Fill all 10 prompts
        for (long i = 1; i <= 10; i++) {
            essayService.saveEssay(i, "Essay " + i + " content");
        }

        user = userRepo.findById(user.getId()).orElseThrow();
        assertEquals(10, essayService.getFilledEssayCount(user));

        List<EssayDto> essays = essayService.getCurrentUserEssays();
        for (EssayDto essay : essays) {
            assertNotNull(essay.getAnswer());
            assertTrue(essay.getAnswer().startsWith("Essay "));
        }
    }

    @Test
    @DisplayName("Essay DTO contains all template fields")
    void testEssayDtoFields() throws AlovoaException {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        List<EssayDto> essays = essayService.getCurrentUserEssays();

        for (EssayDto essay : essays) {
            assertNotNull(essay.getPromptId());
            assertNotNull(essay.getTitle());
            assertNotNull(essay.getPlaceholder());
            assertNotNull(essay.getHelpText());
            assertNotNull(essay.getDisplayOrder());
            assertNotNull(essay.getMinLength());
            assertNotNull(essay.getMaxLength());
            assertNotNull(essay.getRequired());
        }
    }
}
