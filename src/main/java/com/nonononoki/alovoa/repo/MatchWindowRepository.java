package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.MatchWindow;
import com.nonononoki.alovoa.entity.MatchWindow.WindowStatus;
import com.nonononoki.alovoa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchWindowRepository extends JpaRepository<MatchWindow, Long> {

    Optional<MatchWindow> findByUuid(UUID uuid);

    /**
     * Find existing window between two users (regardless of order)
     */
    @Query("SELECT w FROM MatchWindow w WHERE " +
           "(w.userA = :userA AND w.userB = :userB) OR " +
           "(w.userA = :userB AND w.userB = :userA)")
    Optional<MatchWindow> findByUsers(@Param("userA") User userA, @Param("userB") User userB);

    /**
     * Find all active windows for a user (where they haven't confirmed yet)
     */
    @Query("SELECT w FROM MatchWindow w WHERE " +
           "((w.userA = :user AND w.userAConfirmed = false) OR " +
           " (w.userB = :user AND w.userBConfirmed = false)) AND " +
           "w.status IN ('PENDING_BOTH', 'PENDING_USER_A', 'PENDING_USER_B') AND " +
           "w.expiresAt > CURRENT_TIMESTAMP " +
           "ORDER BY w.expiresAt ASC")
    List<MatchWindow> findPendingWindowsForUser(@Param("user") User user);

    /**
     * Find all windows where user has confirmed, waiting on the other
     */
    @Query("SELECT w FROM MatchWindow w WHERE " +
           "((w.userA = :user AND w.userAConfirmed = true AND w.userBConfirmed = false) OR " +
           " (w.userB = :user AND w.userBConfirmed = true AND w.userAConfirmed = false)) AND " +
           "w.status IN ('PENDING_USER_A', 'PENDING_USER_B') AND " +
           "w.expiresAt > CURRENT_TIMESTAMP " +
           "ORDER BY w.expiresAt ASC")
    List<MatchWindow> findWaitingWindowsForUser(@Param("user") User user);

    /**
     * Find all confirmed matches for a user
     */
    @Query("SELECT w FROM MatchWindow w WHERE " +
           "(w.userA = :user OR w.userB = :user) AND " +
           "w.status = 'CONFIRMED' " +
           "ORDER BY w.updatedAt DESC")
    List<MatchWindow> findConfirmedWindowsForUser(@Param("user") User user);

    /**
     * Find expired windows that need status update
     */
    @Query("SELECT w FROM MatchWindow w WHERE " +
           "w.expiresAt < :now AND " +
           "w.status IN ('PENDING_BOTH', 'PENDING_USER_A', 'PENDING_USER_B', 'EXTENSION_PENDING')")
    List<MatchWindow> findExpiredWindows(@Param("now") Date now);

    /**
     * Count pending decisions for a user (for notification badge)
     */
    @Query("SELECT COUNT(w) FROM MatchWindow w WHERE " +
           "((w.userA = :user AND w.userAConfirmed = false) OR " +
           " (w.userB = :user AND w.userBConfirmed = false)) AND " +
           "w.status IN ('PENDING_BOTH', 'PENDING_USER_A', 'PENDING_USER_B') AND " +
           "w.expiresAt > CURRENT_TIMESTAMP")
    int countPendingDecisions(@Param("user") User user);

    /**
     * Find windows expiring soon (for reminder notifications)
     */
    @Query("SELECT w FROM MatchWindow w WHERE " +
           "w.expiresAt BETWEEN :now AND :soon AND " +
           "w.status IN ('PENDING_BOTH', 'PENDING_USER_A', 'PENDING_USER_B')")
    List<MatchWindow> findWindowsExpiringSoon(@Param("now") Date now, @Param("soon") Date soon);

    /**
     * Find all windows by status
     */
    List<MatchWindow> findByStatus(WindowStatus status);

    /**
     * Check if users have any active or confirmed window
     */
    @Query("SELECT COUNT(w) > 0 FROM MatchWindow w WHERE " +
           "((w.userA = :userA AND w.userB = :userB) OR " +
           " (w.userA = :userB AND w.userB = :userA)) AND " +
           "w.status NOT IN ('DECLINED_BY_A', 'DECLINED_BY_B', 'EXPIRED')")
    boolean existsActiveWindowBetween(@Param("userA") User userA, @Param("userB") User userB);
}
