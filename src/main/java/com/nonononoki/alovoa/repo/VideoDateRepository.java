package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface VideoDateRepository extends JpaRepository<VideoDate, Long> {
    List<VideoDate> findByUserAOrUserB(User userA, User userB);
    List<VideoDate> findByConversation(Conversation conversation);

    @Query("SELECT v FROM VideoDate v WHERE (v.userA = :user OR v.userB = :user) AND v.status = :status")
    List<VideoDate> findByUserAndStatus(@Param("user") User user, @Param("status") VideoDate.DateStatus status);

    @Query("SELECT v FROM VideoDate v WHERE v.scheduledAt < :date AND v.status = 'SCHEDULED'")
    List<VideoDate> findExpiredScheduledDates(@Param("date") Date date);

    Optional<VideoDate> findByConversationAndStatus(Conversation conversation, VideoDate.DateStatus status);
}
