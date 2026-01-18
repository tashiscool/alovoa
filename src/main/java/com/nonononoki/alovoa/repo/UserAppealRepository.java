package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import com.nonononoki.alovoa.entity.user.UserAppeal;
import com.nonononoki.alovoa.entity.user.UserAppeal.AppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAppealRepository extends JpaRepository<UserAppeal, Long> {

    Optional<UserAppeal> findByUuid(UUID uuid);

    List<UserAppeal> findByUser(User user);

    List<UserAppeal> findByUserOrderByCreatedAtDesc(User user);

    Page<UserAppeal> findByStatus(AppealStatus status, Pageable pageable);

    List<UserAppeal> findByStatusIn(List<AppealStatus> statuses);

    /**
     * Count appeals by user within a date range (for rate limiting)
     */
    @Query("SELECT COUNT(a) FROM UserAppeal a WHERE a.user = :user AND a.createdAt > :after")
    long countByUserAndCreatedAtAfter(@Param("user") User user, @Param("after") Date after);

    /**
     * Find pending appeals older than a cutoff (for expiration)
     */
    @Query("SELECT a FROM UserAppeal a WHERE a.status = 'PENDING' AND a.createdAt < :cutoff")
    List<UserAppeal> findExpiredPendingAppeals(@Param("cutoff") Date cutoff);

    /**
     * Find active appeal for a user (PENDING or UNDER_REVIEW)
     */
    @Query("SELECT a FROM UserAppeal a WHERE a.user = :user AND a.status IN ('PENDING', 'UNDER_REVIEW')")
    Optional<UserAppeal> findActiveAppealByUser(@Param("user") User user);

    /**
     * Find appeal linked to a specific report
     */
    Optional<UserAppeal> findByLinkedReport(UserAccountabilityReport report);

    /**
     * Find users currently on probation (for graduation check)
     */
    @Query("SELECT a FROM UserAppeal a WHERE a.outcome = 'PROBATION_GRANTED' AND a.probationEndDate <= :now")
    List<UserAppeal> findUsersReadyForProbationGraduation(@Param("now") Date now);

    /**
     * Find most recent appeal for a user
     */
    Optional<UserAppeal> findFirstByUserOrderByCreatedAtDesc(User user);
}
