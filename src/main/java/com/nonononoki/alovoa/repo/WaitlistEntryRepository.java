package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.WaitlistEntry;
import com.nonononoki.alovoa.entity.WaitlistEntry.Location;
import com.nonononoki.alovoa.entity.WaitlistEntry.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByUuid(UUID uuid);

    Optional<WaitlistEntry> findByEmail(String email);

    Optional<WaitlistEntry> findByInviteCode(String inviteCode);

    boolean existsByEmail(String email);

    /**
     * Count total signups.
     */
    long count();

    /**
     * Count by status.
     */
    long countByStatus(Status status);

    /**
     * Count by location.
     */
    long countByLocation(Location location);

    /**
     * Get pending entries ordered by priority (for sending invites).
     */
    @Query("SELECT w FROM WaitlistEntry w WHERE w.status = 'PENDING' ORDER BY w.priorityScore DESC, w.signedUpAt ASC")
    Page<WaitlistEntry> findPendingByPriority(Pageable pageable);

    /**
     * Get entries that used a specific invite code.
     */
    List<WaitlistEntry> findByReferredBy(String inviteCode);

    /**
     * Count referrals for an invite code.
     */
    long countByReferredBy(String inviteCode);

    /**
     * Get waitlist stats by location.
     */
    @Query("SELECT w.location, COUNT(w) FROM WaitlistEntry w GROUP BY w.location")
    List<Object[]> countByLocationGrouped();

    /**
     * Get waitlist stats by gender.
     */
    @Query("SELECT w.gender, COUNT(w) FROM WaitlistEntry w GROUP BY w.gender")
    List<Object[]> countByGenderGrouped();

    /**
     * Get signups in last N days.
     */
    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.signedUpAt >= :since")
    long countSignupsSince(@Param("since") java.util.Date since);

    /**
     * Get position in line.
     */
    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.status = 'PENDING' AND (w.priorityScore > :score OR (w.priorityScore = :score AND w.signedUpAt < :signedUpAt))")
    long getPositionInLine(@Param("score") int score, @Param("signedUpAt") java.util.Date signedUpAt);

    // ============================================
    // Market Threshold Queries
    // ============================================

    /**
     * Count by location and gender (for market thresholds).
     */
    long countByLocationAndGender(Location location, WaitlistEntry.Gender gender);

    /**
     * Find pending entries by location and gender (for women-first invites).
     */
    @Query("SELECT w FROM WaitlistEntry w WHERE w.status = 'PENDING' AND w.location = :location AND w.gender = :gender ORDER BY w.priorityScore DESC, w.signedUpAt ASC")
    Page<WaitlistEntry> findPendingByLocationAndGender(@Param("location") Location location, @Param("gender") WaitlistEntry.Gender gender, Pageable pageable);

    /**
     * Find pending entries by location ordered by priority.
     */
    @Query("SELECT w FROM WaitlistEntry w WHERE w.status = 'PENDING' AND w.location = :location ORDER BY w.priorityScore DESC, w.signedUpAt ASC")
    Page<WaitlistEntry> findPendingByLocationOrderByPriority(@Param("location") Location location, Pageable pageable);
}
