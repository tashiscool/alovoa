package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.user.RealWorldDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RealWorldDateRepository extends JpaRepository<RealWorldDate, Long> {

    Optional<RealWorldDate> findByUuid(UUID uuid);

    List<RealWorldDate> findByUserAOrUserBOrderByCreatedAtDesc(User userA, User userB);

    List<RealWorldDate> findByConversationOrderByCreatedAtDesc(Conversation conversation);

    @Query("SELECT r FROM RealWorldDate r WHERE (r.userA = :user OR r.userB = :user) AND r.status = :status")
    List<RealWorldDate> findByUserAndStatus(User user, RealWorldDate.DateStatus status);

    @Query("SELECT r FROM RealWorldDate r WHERE r.conversation = :conversation AND r.status = 'COMPLETED'")
    List<RealWorldDate> findCompletedByConversation(Conversation conversation);

    long countByUserAOrUserB(User userA, User userB);

    @Query("SELECT COUNT(r) FROM RealWorldDate r WHERE (r.userA = :user OR r.userB = :user) AND r.status = 'COMPLETED'")
    long countCompletedByUser(User user);
}
