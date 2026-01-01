package com.nonononoki.alovoa.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Message;
import com.nonononoki.alovoa.entity.user.MessageReaction;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

	List<MessageReaction> findByMessage(Message message);

	Optional<MessageReaction> findByMessageAndUser(Message message, User user);

	void deleteByMessageAndUser(Message message, User user);

	long countByMessage(Message message);
}
