package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ProfileEditDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProfileEditDailySummaryRepository extends JpaRepository<ProfileEditDailySummary, Long> {

    /**
     * Get summary for a user on a specific date
     */
    Optional<ProfileEditDailySummary> findByUserAndEditDate(User user, LocalDate editDate);

    /**
     * Get summaries for a user within a date range
     */
    List<ProfileEditDailySummary> findByUserAndEditDateBetweenOrderByEditDateDesc(User user, LocalDate startDate, LocalDate endDate);

    /**
     * Get recent summaries for a user
     */
    List<ProfileEditDailySummary> findByUserAndEditDateAfterOrderByEditDateDesc(User user, LocalDate afterDate);

    /**
     * Find summaries that exceed threshold and haven't received coaching
     */
    @Query("SELECT s FROM ProfileEditDailySummary s " +
           "WHERE s.editDate = :date AND s.totalEdits >= :threshold AND s.coachingSent = false")
    List<ProfileEditDailySummary> findSummariesNeedingCoaching(@Param("date") LocalDate date, @Param("threshold") int threshold);

    /**
     * Calculate average daily edits for a user
     */
    @Query("SELECT AVG(s.totalEdits) FROM ProfileEditDailySummary s WHERE s.user = :user")
    Double getAverageDailyEdits(@Param("user") User user);

    /**
     * Get total edits in the last N days
     */
    @Query("SELECT COALESCE(SUM(s.totalEdits), 0) FROM ProfileEditDailySummary s " +
           "WHERE s.user = :user AND s.editDate >= :startDate")
    long getTotalEditsInPeriod(@Param("user") User user, @Param("startDate") LocalDate startDate);
}
