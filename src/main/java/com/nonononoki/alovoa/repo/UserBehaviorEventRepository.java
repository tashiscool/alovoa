package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserBehaviorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface UserBehaviorEventRepository extends JpaRepository<UserBehaviorEvent, Long> {
    List<UserBehaviorEvent> findByUser(User user);
    List<UserBehaviorEvent> findByUserAndCreatedAtAfter(User user, Date after);
    List<UserBehaviorEvent> findByUserAndBehaviorType(User user, UserBehaviorEvent.BehaviorType behaviorType);
    long countByUserAndBehaviorTypeAndCreatedAtAfter(User user, UserBehaviorEvent.BehaviorType behaviorType, Date after);

    /**
     * Count serious incidents by multiple behavior types within a date range.
     * Used for the "3+ incidents before RESTRICTED" pattern requirement.
     */
    long countByUserAndBehaviorTypeInAndCreatedAtAfter(User user, List<UserBehaviorEvent.BehaviorType> behaviorTypes, Date after);
}
