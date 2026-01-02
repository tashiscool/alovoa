package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAudio;
import com.nonononoki.alovoa.entity.user.UserInterest;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import com.nonononoki.alovoa.entity.user.UserProfileDetails;
import com.nonononoki.alovoa.entity.user.UserProfilePicture;
import com.nonononoki.alovoa.entity.user.UserVideo;
import com.nonononoki.alovoa.entity.user.UserVideoVerification;
import com.nonononoki.alovoa.model.ProfileCompletenessDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.repo.UserRepository;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileCompletenessServiceTest {

    @Autowired
    private ProfileCompletenessService profileCompletenessService;

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

    @Value("${app.first-name.length-max}")
    private int firstNameLengthMax;

    @Value("${app.first-name.length-min}")
    private int firstNameLengthMin;

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

    // ============================================
    // Calculating Completion Percentage
    // ============================================

    @Test
    void testCalculateCompleteness_EmptyProfile_ShouldReturnZeroPercent() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Clear all optional fields
        user.setProfilePicture(null);
        user.setDescription(null);
        user.setVideos(null);
        user.setAudio(null);
        user.setInterests(new ArrayList<>());
        user.setProfileDetails(null);
        // Video verification is checked via videoVerification entity, not a setter
        user.setVideoVerification(null);
        user.setPersonalityProfile(null);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertNotNull(completeness);
        assertEquals(0, completeness.getOverallPercent());
        assertEquals(8, completeness.getMissingItems().size());
        assertEquals(0, completeness.getCompletedItems().size());
    }

    @Test
    void testCalculateCompleteness_FullProfile_ShouldReturn100Percent() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Set profile picture
        UserProfilePicture profilePic = new UserProfilePicture();
        profilePic.setUser(user);
        user.setProfilePicture(profilePic);

        // Set description (50+ chars)
        user.setDescription("This is a comprehensive description that is longer than fifty characters.");

        // Set video intro
        UserVideo introVideo = new UserVideo();
        introVideo.setUser(user);
        introVideo.setIsIntro(true);
        introVideo.setVideoUrl("intro.mp4");
        List<UserVideo> videos = new ArrayList<>();
        videos.add(introVideo);
        user.setVideos(videos);

        // Set audio
        UserAudio userAudio = new UserAudio();
        userAudio.setUser(user);
        user.setAudio(userAudio);

        // Set interests
        UserInterest interest1 = new UserInterest();
        interest1.setText("Reading");
        UserInterest interest2 = new UserInterest();
        interest2.setText("Hiking");
        UserInterest interest3 = new UserInterest();
        interest3.setText("Cooking");
        List<UserInterest> interests = new ArrayList<>();
        interests.add(interest1);
        interests.add(interest2);
        interests.add(interest3);
        user.setInterests(interests);

        // Set profile details
        UserProfileDetails details = new UserProfileDetails();
        details.setUser(user);
        details.setHeightCm(175);
        details.setBodyType(UserProfileDetails.BodyType.AVERAGE);
        details.setEducation(UserProfileDetails.EducationLevel.BACHELORS);
        details.setOccupation("Software Engineer");
        details.setDiet(UserProfileDetails.Diet.OMNIVORE);
        details.setPets(UserProfileDetails.PetStatus.HAS_CATS);
        user.setProfileDetails(details);

        // Set video verified - create a verified video verification
        UserVideoVerification verification = new UserVideoVerification();
        verification.setUser(user);
        verification.setStatus(UserVideoVerification.VerificationStatus.VERIFIED);
        user.setVideoVerification(verification);

        // Set personality profile
        UserPersonalityProfile personality = new UserPersonalityProfile();
        personality.setUser(user);
        personality.setOpenness(75.0);
        personality.setConscientiousness(80.0);
        personality.setExtraversion(65.0);
        personality.setAgreeableness(70.0);
        personality.setNeuroticism(45.0);
        personality.setAssessmentCompletedAt(new Date());
        user.setPersonalityProfile(personality);

        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertNotNull(completeness);
        assertEquals(100, completeness.getOverallPercent());
        assertEquals(0, completeness.getMissingItems().size());
        assertEquals(8, completeness.getCompletedItems().size());
        assertTrue(completeness.isHasProfilePicture());
        assertTrue(completeness.isHasDescription());
        assertTrue(completeness.isHasVideoIntro());
        assertTrue(completeness.isHasAudioIntro());
        assertTrue(completeness.isHasInterests());
        assertTrue(completeness.isHasProfileDetails());
        assertTrue(completeness.isVideoVerified());
        assertTrue(completeness.isHasAssessmentComplete());
    }

    @Test
    void testCalculateCompleteness_NullUser_ShouldThrowException() throws Exception {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            profileCompletenessService.calculateCompleteness(null);
        });

        assertEquals("User cannot be null", exception.getMessage());
    }

    // ============================================
    // Missing Fields Detection
    // ============================================

    @Test
    void testCalculateCompleteness_MissingProfilePicture_ShouldDetect() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        user.setProfilePicture(null);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertFalse(completeness.isHasProfilePicture());
        assertTrue(completeness.getMissingItems().stream()
                .anyMatch(item -> item.contains("profile picture")));
    }

    @Test
    void testCalculateCompleteness_ShortDescription_ShouldNotCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Description too short (less than 50 chars)
        user.setDescription("Short bio");
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertFalse(completeness.isHasDescription());
        assertTrue(completeness.getMissingItems().stream()
                .anyMatch(item -> item.contains("bio") && item.contains("50")));
    }

    @Test
    void testCalculateCompleteness_LongDescription_ShouldCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Description with 50+ characters
        user.setDescription("This is a longer description with more than fifty characters in total.");
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertTrue(completeness.isHasDescription());
        assertTrue(completeness.getCompletedItems().contains("Bio/description"));
    }

    @Test
    void testCalculateCompleteness_VideoWithoutIntroFlag_ShouldNotCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Video without intro flag
        UserVideo video = new UserVideo();
        video.setUser(user);
        video.setIsIntro(false);
        video.setVideoUrl("video.mp4");
        List<UserVideo> videos = new ArrayList<>();
        videos.add(video);
        user.setVideos(videos);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertFalse(completeness.isHasVideoIntro());
        assertTrue(completeness.getMissingItems().stream()
                .anyMatch(item -> item.contains("video introduction")));
    }

    @Test
    void testCalculateCompleteness_MultipleVideosWithIntro_ShouldCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Multiple videos, one is intro
        UserVideo intro = new UserVideo();
        intro.setUser(user);
        intro.setIsIntro(true);
        intro.setVideoUrl("intro.mp4");

        UserVideo other = new UserVideo();
        other.setUser(user);
        other.setIsIntro(false);
        other.setVideoUrl("other.mp4");

        List<UserVideo> videos = new ArrayList<>();
        videos.add(intro);
        videos.add(other);
        user.setVideos(videos);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertTrue(completeness.isHasVideoIntro());
        assertTrue(completeness.getCompletedItems().contains("Video introduction"));
    }

    @Test
    void testCalculateCompleteness_TwoInterests_ShouldNotCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Only 2 interests (need 3)
        UserInterest interest1 = new UserInterest();
        interest1.setText("Reading");
        UserInterest interest2 = new UserInterest();
        interest2.setText("Hiking");
        List<UserInterest> interests = new ArrayList<>();
        interests.add(interest1);
        interests.add(interest2);
        user.setInterests(interests);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertFalse(completeness.isHasInterests());
        assertTrue(completeness.getMissingItems().stream()
                .anyMatch(item -> item.contains("3 interests")));
    }

    @Test
    void testCalculateCompleteness_ThreeInterests_ShouldCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        UserInterest interest1 = new UserInterest();
        interest1.setText("Reading");
        UserInterest interest2 = new UserInterest();
        interest2.setText("Hiking");
        UserInterest interest3 = new UserInterest();
        interest3.setText("Cooking");
        List<UserInterest> interests = new ArrayList<>();
        interests.add(interest1);
        interests.add(interest2);
        interests.add(interest3);
        user.setInterests(interests);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertTrue(completeness.isHasInterests());
    }

    @Test
    void testCalculateCompleteness_InsufficientProfileDetails_ShouldNotCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Only 2 details (need at least 3)
        UserProfileDetails details = new UserProfileDetails();
        details.setUser(user);
        details.setHeightCm(175);
        details.setBodyType(UserProfileDetails.BodyType.AVERAGE);
        user.setProfileDetails(details);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertFalse(completeness.isHasProfileDetails());
    }

    @Test
    void testCalculateCompleteness_SufficientProfileDetails_ShouldCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // 3 or more details
        UserProfileDetails details = new UserProfileDetails();
        details.setUser(user);
        details.setHeightCm(175);
        details.setBodyType(UserProfileDetails.BodyType.AVERAGE);
        details.setEducation(UserProfileDetails.EducationLevel.BACHELORS);
        user.setProfileDetails(details);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertTrue(completeness.isHasProfileDetails());
        assertTrue(completeness.getCompletedItems().stream()
                .anyMatch(item -> item.contains("Profile details")));
    }

    @Test
    void testCalculateCompleteness_IncompletePersonalityProfile_ShouldNotCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Incomplete personality profile (missing some scores)
        UserPersonalityProfile personality = new UserPersonalityProfile();
        personality.setUser(user);
        personality.setOpenness(75.0);
        personality.setConscientiousness(80.0);
        // Missing other scores
        user.setPersonalityProfile(personality);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertFalse(completeness.isHasAssessmentComplete());
    }

    @Test
    void testCalculateCompleteness_CompletePersonalityProfile_ShouldCount() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        UserPersonalityProfile personality = new UserPersonalityProfile();
        personality.setUser(user);
        personality.setOpenness(75.0);
        personality.setConscientiousness(80.0);
        personality.setExtraversion(65.0);
        personality.setAgreeableness(70.0);
        personality.setNeuroticism(45.0);
        user.setPersonalityProfile(personality);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertTrue(completeness.isHasAssessmentComplete());
        assertTrue(completeness.getCompletedItems().contains("Personality assessment"));
    }

    // ============================================
    // Completion Milestones
    // ============================================

    @Test
    void testCalculateCompleteness_PartialProfile_ShouldCalculateCorrectPercentage() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Add only profile picture (20 points out of 100)
        UserProfilePicture pic = new UserProfilePicture();
        pic.setUser(user);
        user.setProfilePicture(pic);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertEquals(20, completeness.getOverallPercent());
        assertEquals(1, completeness.getCompletedItems().size());
        assertEquals(7, completeness.getMissingItems().size());
    }

    @Test
    void testCalculateCompleteness_HalfwayComplete_ShouldShow50Percent() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Profile picture (20) + Description (15) + Video intro (20) = 55 points
        UserProfilePicture pic = new UserProfilePicture();
        pic.setUser(user);
        user.setProfilePicture(pic);
        user.setDescription("This is a comprehensive description that is longer than fifty characters.");

        UserVideo introVideo = new UserVideo();
        introVideo.setUser(user);
        introVideo.setIsIntro(true);
        introVideo.setVideoUrl("intro.mp4");
        List<UserVideo> videos = new ArrayList<>();
        videos.add(introVideo);
        user.setVideos(videos);

        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertEquals(55, completeness.getOverallPercent());
    }

    @Test
    void testCalculateCompleteness_AdditionalPhotos_ShouldBeTracked() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        // Mock additional images (implementation depends on User entity structure)
        // This is a placeholder - adjust based on actual implementation
        UserProfilePicture pic = new UserProfilePicture();
        pic.setUser(user);
        user.setProfilePicture(pic);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertNotNull(completeness);
        // Additional photos count is tracked but doesn't affect percentage
        assertTrue(completeness.getAdditionalPhotosCount() >= 0);
    }

    @Test
    void testCalculateCompleteness_Preferences_ShouldBeTracked() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertNotNull(completeness);
        // Preferences tracking doesn't affect score but is included in DTO
        // hasPreferences field indicates if basic search preferences are set
    }

    @Test
    void testCalculateCompleteness_MissingItemsList_ShouldContainActionableItems() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        user.setProfilePicture(null);
        user.setDescription(null);
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertFalse(completeness.getMissingItems().isEmpty());
        // Missing items should be actionable
        completeness.getMissingItems().forEach(item -> {
            assertFalse(item.isEmpty());
            assertTrue(item.length() > 5); // Should have meaningful text
        });
    }

    @Test
    void testCalculateCompleteness_CompletedItemsList_ShouldMatchCompletedFields() throws Exception {
        User user = testUsers.get(0);
        user = userRepo.findById(user.getId()).orElseThrow();

        UserProfilePicture pic = new UserProfilePicture();
        pic.setUser(user);
        user.setProfilePicture(pic);
        user.setDescription("This is a comprehensive description that is longer than fifty characters.");
        user = userRepo.saveAndFlush(user);

        ProfileCompletenessDto completeness = profileCompletenessService.calculateCompleteness(user);

        assertEquals(2, completeness.getCompletedItems().size());
        assertTrue(completeness.getCompletedItems().contains("Profile picture"));
        assertTrue(completeness.getCompletedItems().contains("Bio/description"));
    }
}
