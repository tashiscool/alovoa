package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserRelationship;
import com.nonononoki.alovoa.entity.UserRelationship.RelationshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRelationshipRepository extends JpaRepository<UserRelationship, Long> {

    Optional<UserRelationship> findByUuid(UUID uuid);

    /**
     * Find active (confirmed) relationship for a user.
     */
    @Query("SELECT r FROM UserRelationship r WHERE (r.user1 = :user OR r.user2 = :user) AND r.status = 'CONFIRMED'")
    Optional<UserRelationship> findActiveRelationshipByUser(@Param("user") User user);

    /**
     * Find all relationships involving a user (any status).
     */
    @Query("SELECT r FROM UserRelationship r WHERE r.user1 = :user OR r.user2 = :user ORDER BY r.createdAt DESC")
    List<UserRelationship> findAllByUser(@Param("user") User user);

    /**
     * Find pending requests where user is the recipient (user2).
     */
    @Query("SELECT r FROM UserRelationship r WHERE r.user2 = :user AND r.status = 'PENDING'")
    List<UserRelationship> findPendingRequestsForUser(@Param("user") User user);

    /**
     * Find pending requests sent by user (user1).
     */
    @Query("SELECT r FROM UserRelationship r WHERE r.user1 = :user AND r.status = 'PENDING'")
    List<UserRelationship> findPendingRequestsByUser(@Param("user") User user);

    /**
     * Check if a relationship exists between two users (any status except ENDED).
     */
    @Query("SELECT r FROM UserRelationship r WHERE " +
           "((r.user1 = :user1 AND r.user2 = :user2) OR (r.user1 = :user2 AND r.user2 = :user1)) " +
           "AND r.status != 'ENDED'")
    Optional<UserRelationship> findExistingRelationship(@Param("user1") User user1, @Param("user2") User user2);

    /**
     * Count pending requests for a user.
     */
    @Query("SELECT COUNT(r) FROM UserRelationship r WHERE r.user2 = :user AND r.status = 'PENDING'")
    long countPendingRequests(@Param("user") User user);
}
