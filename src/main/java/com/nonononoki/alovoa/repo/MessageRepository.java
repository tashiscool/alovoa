package com.nonononoki.alovoa.repo;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.user.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {
	List<Message> findByUserFrom(User u);
	List<Message> findByUserTo(User u);

	/**
	 * Find the most recent message in a conversation
	 */
	@Query("SELECT m FROM Message m WHERE m.conversation = :conversation ORDER BY m.date DESC LIMIT 1")
	Optional<Message> findLastMessageInConversation(@Param("conversation") Conversation conversation);

	/**
	 * Find conversations where a specific user received the last message (potential ghosting)
	 */
	@Query("SELECT m FROM Message m WHERE m.userTo = :user AND m.date < :cutoff " +
			"AND NOT EXISTS (SELECT m2 FROM Message m2 WHERE m2.conversation = m.conversation " +
			"AND m2.userFrom = :user AND m2.date > m.date)")
	List<Message> findUnansweredMessagesBefore(@Param("user") User user, @Param("cutoff") Date cutoff);

	/**
	 * Count messages sent by a user in a conversation
	 */
	long countByConversationAndUserFrom(Conversation conversation, User userFrom);

	/**
	 * Find all unread messages in a conversation that were sent to a specific user
	 */
	@Query("SELECT m FROM Message m WHERE m.conversation = :conversation " +
			"AND m.userTo = :userTo AND m.readAt IS NULL")
	List<Message> findUnreadMessagesInConversation(@Param("conversation") Conversation conversation,
	                                                @Param("userTo") User userTo);

	/**
	 * Find all undelivered messages in a conversation that were sent to a specific user
	 */
	@Query("SELECT m FROM Message m WHERE m.conversation = :conversation " +
			"AND m.userTo = :userTo AND m.deliveredAt IS NULL")
	List<Message> findUndeliveredMessagesInConversation(@Param("conversation") Conversation conversation,
	                                                     @Param("userTo") User userTo);
}

