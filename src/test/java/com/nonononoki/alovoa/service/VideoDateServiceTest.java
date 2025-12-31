package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoDate;
import com.nonononoki.alovoa.entity.VideoDate.VideoDateStatus;
import com.nonononoki.alovoa.repo.*;
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
import org.springframework.web.client.RestTemplate;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VideoDateServiceTest {

    @Autowired
    private VideoDateService videoDateService;

    @Autowired
    private VideoDateRepository videoDateRepo;

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

    @MockitoBean
    private RestTemplate restTemplate;

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
    void testScheduleDate() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = getFutureDate(2); // 2 days from now

        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        assertNotNull(date);
        assertNotNull(date.getUuid());
        assertEquals(initiator, date.getInitiator());
        assertEquals(participant, date.getParticipant());
        assertEquals(VideoDateStatus.SCHEDULED, date.getStatus());
        assertEquals(30, date.getDurationMinutes());
        assertNotNull(date.getCreatedAt());
    }

    @Test
    void testScheduleDate_PastTime() {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date pastTime = new Date(System.currentTimeMillis() - 86400000); // Yesterday

        assertThrows(Exception.class, () ->
                videoDateService.scheduleDate(participant.getUuid().toString(), pastTime, 30));
    }

    @Test
    void testScheduleDate_SameUser() {
        User user = testUsers.get(0);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        Date futureTime = getFutureDate(1);

        assertThrows(Exception.class, () ->
                videoDateService.scheduleDate(user.getUuid().toString(), futureTime, 30));
    }

    @Test
    void testAcceptDate() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = getFutureDate(2);
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        // Switch to participant
        Mockito.when(authService.getCurrentUser(true)).thenReturn(participant);

        VideoDate acceptedDate = videoDateService.acceptDate(date.getUuid().toString());

        assertEquals(VideoDateStatus.CONFIRMED, acceptedDate.getStatus());
        assertNotNull(acceptedDate.getRoomId());
    }

    @Test
    void testAcceptDate_WrongUser() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        User other = testUsers.get(2);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = getFutureDate(2);
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        // Other user tries to accept
        Mockito.when(authService.getCurrentUser(true)).thenReturn(other);

        assertThrows(Exception.class, () ->
                videoDateService.acceptDate(date.getUuid().toString()));
    }

    @Test
    void testDeclineDate() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = getFutureDate(2);
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        // Participant declines
        Mockito.when(authService.getCurrentUser(true)).thenReturn(participant);

        VideoDate declinedDate = videoDateService.declineDate(date.getUuid().toString(), "Schedule conflict");

        assertEquals(VideoDateStatus.DECLINED, declinedDate.getStatus());
        assertEquals("Schedule conflict", declinedDate.getDeclineReason());
    }

    @Test
    void testCancelDate() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = getFutureDate(2);
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        // Initiator cancels
        VideoDate cancelledDate = videoDateService.cancelDate(date.getUuid().toString(), "Emergency came up");

        assertEquals(VideoDateStatus.CANCELLED, cancelledDate.getStatus());
        assertEquals("Emergency came up", cancelledDate.getCancelReason());
    }

    @Test
    void testRescheduleDate() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date originalTime = getFutureDate(2);
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), originalTime, 30);

        Date newTime = getFutureDate(5);
        VideoDate rescheduledDate = videoDateService.rescheduleDate(date.getUuid().toString(), newTime);

        assertEquals(VideoDateStatus.RESCHEDULED, rescheduledDate.getStatus());
        assertEquals(newTime, rescheduledDate.getScheduledAt());
    }

    @Test
    void testCompleteDate() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = getFutureDate(0); // Today
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        // Accept
        Mockito.when(authService.getCurrentUser(true)).thenReturn(participant);
        date = videoDateService.acceptDate(date.getUuid().toString());

        // Complete
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);
        VideoDate completedDate = videoDateService.completeDate(date.getUuid().toString());

        assertEquals(VideoDateStatus.COMPLETED, completedDate.getStatus());
        assertNotNull(completedDate.getCompletedAt());
    }

    @Test
    void testCompleteDate_NotConfirmed() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = getFutureDate(2);
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        // Try to complete without acceptance
        assertThrows(Exception.class, () ->
                videoDateService.completeDate(date.getUuid().toString()));
    }

    @Test
    void testReportNoShow() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        Date scheduledTime = new Date(); // Now
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), scheduledTime, 30);

        // Accept
        Mockito.when(authService.getCurrentUser(true)).thenReturn(participant);
        date = videoDateService.acceptDate(date.getUuid().toString());

        // Initiator reports participant as no-show
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);
        VideoDate noShowDate = videoDateService.reportNoShow(date.getUuid().toString());

        assertEquals(VideoDateStatus.NO_SHOW, noShowDate.getStatus());
        assertNotNull(noShowDate.getNoShowReportedBy());
        assertEquals(initiator, noShowDate.getNoShowReportedBy());
    }

    @Test
    void testGetUpcomingDates() throws Exception {
        User user = testUsers.get(0);
        User other = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create some dates
        videoDateService.scheduleDate(other.getUuid().toString(), getFutureDate(1), 30);
        videoDateService.scheduleDate(other.getUuid().toString(), getFutureDate(3), 30);

        List<VideoDate> upcoming = videoDateService.getUpcomingDates();

        assertEquals(2, upcoming.size());
    }

    @Test
    void testGetDateHistory() throws Exception {
        User user = testUsers.get(0);
        User other = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);

        // Create and complete a date
        VideoDate date = videoDateService.scheduleDate(other.getUuid().toString(), new Date(), 30);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(other);
        date = videoDateService.acceptDate(date.getUuid().toString());

        Mockito.when(authService.getCurrentUser(true)).thenReturn(user);
        videoDateService.completeDate(date.getUuid().toString());

        List<VideoDate> history = videoDateService.getDateHistory();

        assertFalse(history.isEmpty());
        assertEquals(VideoDateStatus.COMPLETED, history.get(0).getStatus());
    }

    @Test
    void testSubmitFeedback() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        // Create and complete date
        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), new Date(), 30);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(participant);
        date = videoDateService.acceptDate(date.getUuid().toString());

        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);
        date = videoDateService.completeDate(date.getUuid().toString());

        // Submit feedback
        VideoDate withFeedback = videoDateService.submitFeedback(
                date.getUuid().toString(),
                5,  // rating
                "Great conversation!",
                true  // wouldMeetAgain
        );

        assertNotNull(withFeedback.getInitiatorRating());
        assertEquals(5, withFeedback.getInitiatorRating());
    }

    @Test
    void testGetRoomInfo() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), new Date(), 30);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(participant);
        date = videoDateService.acceptDate(date.getUuid().toString());

        Map<String, Object> roomInfo = videoDateService.getRoomInfo(date.getUuid().toString());

        assertNotNull(roomInfo);
        assertTrue(roomInfo.containsKey("roomId"));
        assertTrue(roomInfo.containsKey("token"));
    }

    @Test
    void testGetRoomInfo_Unauthorized() throws Exception {
        User initiator = testUsers.get(0);
        User participant = testUsers.get(1);
        User other = testUsers.get(2);
        Mockito.when(authService.getCurrentUser(true)).thenReturn(initiator);

        VideoDate date = videoDateService.scheduleDate(participant.getUuid().toString(), new Date(), 30);

        Mockito.when(authService.getCurrentUser(true)).thenReturn(participant);
        date = videoDateService.acceptDate(date.getUuid().toString());

        // Other user tries to get room info
        Mockito.when(authService.getCurrentUser(true)).thenReturn(other);

        assertThrows(Exception.class, () ->
                videoDateService.getRoomInfo(date.getUuid().toString()));
    }

    private Date getFutureDate(int daysFromNow) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, daysFromNow);
        cal.add(Calendar.HOUR, 1); // Add an hour to make sure it's in the future
        return cal.getTime();
    }
}
