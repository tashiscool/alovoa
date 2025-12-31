package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideoVerification;
import com.nonononoki.alovoa.entity.user.UserVideoVerification.VerificationStatus;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import com.nonononoki.alovoa.repo.UserVideoVerificationRepository;
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
class VideoVerificationResourceTest {

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
    private UserVideoVerificationRepository verificationRepo;

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

    @Autowired
    private VideoVerificationResource videoVerificationResource;

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
    void testVideoVerification_NotStarted() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        ModelAndView mav = videoVerificationResource.videoVerification();

        assertNotNull(mav);
        assertEquals("video-verification", mav.getViewName());
        assertFalse((Boolean) mav.getModel().get("verified"));
        assertEquals("NOT_STARTED", mav.getModel().get("status"));
    }

    @Test
    void testVideoVerification_Pending() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create pending verification
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.PENDING);
        verification.setCreatedAt(new Date());
        verificationRepo.save(verification);

        ModelAndView mav = videoVerificationResource.videoVerification();

        assertNotNull(mav);
        assertFalse((Boolean) mav.getModel().get("verified"));
        assertEquals(VerificationStatus.PENDING.name(), mav.getModel().get("status"));
    }

    @Test
    void testVideoVerification_Verified() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create completed verification
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.VERIFIED);
        verification.setCreatedAt(new Date());
        verification.setVerifiedAt(new Date());
        verification.setFaceMatchScore(0.92);
        verification.setLivenessScore(0.88);
        verificationRepo.save(verification);

        ModelAndView mav = videoVerificationResource.videoVerification();

        assertNotNull(mav);
        assertTrue((Boolean) mav.getModel().get("verified"));
        assertEquals(VerificationStatus.VERIFIED.name(), mav.getModel().get("status"));
        assertNotNull(mav.getModel().get("verifiedAt"));
    }

    @Test
    void testVideoVerification_Rejected() throws Exception {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create rejected verification
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setCreatedAt(new Date());
        verification.setFaceMatchScore(0.45);  // Below threshold
        verification.setRejectionReason("Face match score below threshold");
        verificationRepo.save(verification);

        ModelAndView mav = videoVerificationResource.videoVerification();

        assertNotNull(mav);
        assertFalse((Boolean) mav.getModel().get("verified"));
        assertEquals(VerificationStatus.REJECTED.name(), mav.getModel().get("status"));
        assertTrue(mav.getModel().containsKey("rejectionReason"));
    }
}
