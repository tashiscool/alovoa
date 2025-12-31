package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserPersonalityProfileRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonalityResourceTest {

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
    private UserPersonalityProfileRepository personalityRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @Autowired
    private PersonalityResource personalityResource;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MailService mailService;

    private List<User> testUsers;

    @BeforeEach
    void before() throws Exception {
        Mockito.when(mailService.sendMail(Mockito.any(String.class), any(String.class), any(String.class),
                any(String.class))).thenReturn(true);
        testUsers = RegisterServiceTest.getTestUsers(captchaService, registerService, firstNameLengthMax,
                firstNameLengthMin);
    }

    @AfterEach
    void after() throws Exception {
        RegisterServiceTest.deleteAllUsers(userService, authService, captchaService, conversationRepo, userRepo);
    }

    @Test
    void testPersonalityAssessment_NotCompleted() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        ModelAndView mav = personalityResource.personalityAssessment();

        assertNotNull(mav);
        assertEquals("personality-assessment", mav.getViewName());
        assertFalse((Boolean) mav.getModel().get("alreadyCompleted"));
        assertTrue(mav.getModel().containsKey("questions"));
    }

    @Test
    void testPersonalityAssessment_AlreadyCompleted() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create completed profile
        UserPersonalityProfile profile = new UserPersonalityProfile();
        profile.setUser(user);
        profile.setOpenness(75.0);
        profile.setConscientiousness(65.0);
        profile.setExtraversion(50.0);
        profile.setAgreeableness(70.0);
        profile.setNeuroticism(35.0);
        profile.setAssessmentCompletedAt(new Date());
        personalityRepo.save(profile);
        user.setPersonalityProfile(profile);
        userRepo.save(user);

        ModelAndView mav = personalityResource.personalityAssessment();

        assertNotNull(mav);
        assertTrue((Boolean) mav.getModel().get("alreadyCompleted"));
    }

    @Test
    void testPersonalityResults_NotCompleted() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        ModelAndView mav = personalityResource.personalityResults();

        assertNotNull(mav);
        // Should redirect to assessment
        assertEquals("redirect:/personality-assessment", mav.getViewName());
    }

    @Test
    void testPersonalityResults_Completed() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create completed profile
        UserPersonalityProfile profile = new UserPersonalityProfile();
        profile.setUser(user);
        profile.setOpenness(75.0);
        profile.setConscientiousness(65.0);
        profile.setExtraversion(50.0);
        profile.setAgreeableness(70.0);
        profile.setNeuroticism(35.0);
        profile.setAttachmentStyle(UserPersonalityProfile.AttachmentStyle.SECURE);
        profile.setCommunicationDirectness(70);
        profile.setCommunicationEmotional(60);
        profile.setAssessmentCompletedAt(new Date());
        personalityRepo.save(profile);
        user.setPersonalityProfile(profile);
        userRepo.save(user);

        ModelAndView mav = personalityResource.personalityResults();

        assertNotNull(mav);
        assertEquals("personality-results", mav.getViewName());
        assertEquals(75.0, mav.getModel().get("openness"));
        assertEquals(65.0, mav.getModel().get("conscientiousness"));
        assertEquals(50.0, mav.getModel().get("extraversion"));
        assertEquals(70.0, mav.getModel().get("agreeableness"));
        assertEquals(35.0, mav.getModel().get("neuroticism"));
        assertEquals(UserPersonalityProfile.AttachmentStyle.SECURE, mav.getModel().get("attachmentStyle"));
    }
}
