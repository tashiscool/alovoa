package com.nonononoki.alovoa.repo;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nonononoki.alovoa.entity.user.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
	List<Conversation> findByUsers_Id(long userId);

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

