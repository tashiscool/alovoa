package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserProfileVisit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface UserProfileVisitRepository extends JpaRepository<UserProfileVisit, Long> {

    /**
     * Find an existing visit record between two users
     */
    Optional<UserProfileVisit> findByVisitorAndVisitedUser(User visitor, User visitedUser);

    /**
     * Get all visitors to a user's profile, ordered by most recent
     */
    Page<UserProfileVisit> findByVisitedUserOrderByLastVisitAtDesc(User visitedUser, Pageable pageable);

    /**
     * Get visitors from the last N days
     */
    @Query("SELECT v FROM UserProfileVisit v WHERE v.visitedUser = :user AND v.lastVisitAt > :since ORDER BY v.lastVisitAt DESC")
    List<UserProfileVisit> findRecentVisitors(@Param("user") User user, @Param("since") Date since);

    /**
     * Count unique visitors to a profile
     */
    long countByVisitedUser(User visitedUser);

    /**
     * Count visitors in a time period
     */
    @Query("SELECT COUNT(v) FROM UserProfileVisit v WHERE v.visitedUser = :user AND v.lastVisitAt > :since")
    long countRecentVisitors(@Param("user") User user, @Param("since") Date since);

    /**
     * Get profiles the user has visited
     */
    Page<UserProfileVisit> findByVisitorOrderByLastVisitAtDesc(User visitor, Pageable pageable);

    /**
     * Get profiles visited by user in a time period
     */
    @Query("SELECT v FROM UserProfileVisit v WHERE v.visitor = :user AND v.lastVisitAt > :since ORDER BY v.lastVisitAt DESC")
    List<UserProfileVisit> findRecentVisitsByVisitor(@Param("user") User user, @Param("since") Date since);

    /**
     * Check if user has visited another user's profile
     */
    boolean existsByVisitorAndVisitedUser(User visitor, User visitedUser);

    /**
     * Delete old visit records (for cleanup)
     */
    void deleteByLastVisitAtBefore(Date cutoff);
}
