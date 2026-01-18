package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ProfileCoachingMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileCoachingMessageRepository extends JpaRepository<ProfileCoachingMessage, Long> {

    Optional<ProfileCoachingMessage> findByUuid(UUID uuid);

    /**
     * Get active (not dismissed) messages for a user
     */
    List<ProfileCoachingMessage> findByUserAndDismissedFalseOrderBySentAtDesc(User user);

    /**
     * Get all messages for a user
     */
    List<ProfileCoachingMessage> findByUserOrderBySentAtDesc(User user);

    /**
     * Check if user has received a specific message type recently
     */
    boolean existsByUserAndMessageTypeAndSentAtAfter(User user, ProfileCoachingMessage.MessageType type, Date after);

    /**
     * Count messages by type for a user
     */
    long countByUserAndMessageType(User user, ProfileCoachingMessage.MessageType type);

    /**
     * Get feedback statistics
     */
    @Query("SELECT COUNT(m) FROM ProfileCoachingMessage m WHERE m.helpfulFeedback = true")
    long countHelpfulMessages();

    @Query("SELECT COUNT(m) FROM ProfileCoachingMessage m WHERE m.helpfulFeedback = false")
    long countUnhelpfulMessages();
}
