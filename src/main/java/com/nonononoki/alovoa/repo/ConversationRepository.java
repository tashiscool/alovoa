package com.nonononoki.alovoa.repo;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nonononoki.alovoa.entity.user.Conversation;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
	List<Conversation> findByUsers_Id(long userId);

	/**
	 * Find a conversation between two specific users.
	 */
	@Query("SELECT c FROM Conversation c JOIN c.users u1 JOIN c.users u2 " +
			"WHERE u1.id = :userId1 AND u2.id = :userId2")
	Optional<Conversation> findByUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

	/**
	 * Find conversations where the last message was sent before cutoff date.
	 * Used for ghosting detection.
	 */
	@Query("SELECT c FROM Conversation c WHERE c.lastUpdated < :cutoff AND c.lastUpdated IS NOT NULL")
	List<Conversation> findConversationsWithNoRecentActivity(@Param("cutoff") Date cutoff);

	/**
	 * Find active conversations (had messages in last N days)
	 */
	@Query("SELECT c FROM Conversation c WHERE c.lastUpdated > :since")
	List<Conversation> findActiveConversations(@Param("since") Date since);
}

