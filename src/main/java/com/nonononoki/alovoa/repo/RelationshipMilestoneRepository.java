package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.user.RelationshipMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelationshipMilestoneRepository extends JpaRepository<RelationshipMilestone, Long> {

    Optional<RelationshipMilestone> findByUuid(UUID uuid);

    List<RelationshipMilestone> findByConversationOrderByMilestoneDateDesc(Conversation conversation);

    @Query("SELECT m FROM RelationshipMilestone m WHERE (m.userA = :user OR m.userB = :user) ORDER BY m.milestoneDate DESC")
    List<RelationshipMilestone> findByUserOrderByMilestoneDateDesc(User user);

    Optional<RelationshipMilestone> findByConversationAndMilestoneType(
            Conversation conversation, RelationshipMilestone.MilestoneType type);

    @Query("SELECT m FROM RelationshipMilestone m WHERE m.checkInSent = false AND m.milestoneDate <= :today")
    List<RelationshipMilestone> findPendingCheckIns(LocalDate today);

    @Query("SELECT m FROM RelationshipMilestone m WHERE m.checkInSent = true " +
           "AND m.userAResponse IS NULL AND m.userBResponse IS NULL " +
           "AND m.checkInSentAt < :before")
    List<RelationshipMilestone> findUnansweredCheckIns(Date before);

    long countByMilestoneTypeAndCreatedAtBetween(
            RelationshipMilestone.MilestoneType type, Date start, Date end);

    @Query("SELECT COUNT(m) FROM RelationshipMilestone m WHERE m.leftPlatformTogether = true")
    long countSuccessfulExits();

    @Query("SELECT COUNT(m) FROM RelationshipMilestone m WHERE m.stillTogether = true AND m.milestoneType = :type")
    long countStillTogetherByMilestoneType(RelationshipMilestone.MilestoneType type);
}
