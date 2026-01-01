package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ContentModerationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentModerationEventRepository extends JpaRepository<ContentModerationEvent, Long> {
    List<ContentModerationEvent> findByUser(User user);

    List<ContentModerationEvent> findByUserAndBlocked(User user, boolean blocked);

    List<ContentModerationEvent> findByContentType(String contentType);

    long countByUserAndBlocked(User user, boolean blocked);
}
