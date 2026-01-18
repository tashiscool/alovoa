package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ExitVelocityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExitVelocityEventRepository extends JpaRepository<ExitVelocityEvent, Long> {

    Optional<ExitVelocityEvent> findByUuid(UUID uuid);

    Optional<ExitVelocityEvent> findByUser(User user);

    List<ExitVelocityEvent> findByExitDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Count events by type within a date range
     */
    long countByEventTypeAndExitDateBetween(ExitVelocityEvent.ExitEventType type, LocalDate startDate, LocalDate endDate);

    /**
     * Count positive exits (relationship formed) within a date range
     */
    long countByRelationshipFormedTrueAndExitDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Get average days to relationship within a date range
     */
    @Query("SELECT AVG(e.daysToRelationship) FROM ExitVelocityEvent e " +
           "WHERE e.relationshipFormed = true AND e.exitDate BETWEEN :startDate AND :endDate")
    Double getAvgDaysToRelationship(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get average satisfaction within a date range
     */
    @Query("SELECT AVG(e.satisfactionRating) FROM ExitVelocityEvent e " +
           "WHERE e.satisfactionRating IS NOT NULL AND e.exitDate BETWEEN :startDate AND :endDate")
    Double getAvgSatisfaction(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get recommendation rate within a date range
     */
    @Query("SELECT COUNT(e) FROM ExitVelocityEvent e " +
           "WHERE e.wouldRecommend = true AND e.exitDate BETWEEN :startDate AND :endDate")
    long countWouldRecommend(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(e) FROM ExitVelocityEvent e " +
           "WHERE e.wouldRecommend IS NOT NULL AND e.exitDate BETWEEN :startDate AND :endDate")
    long countWithRecommendationResponse(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get all days to relationship values for median calculation
     */
    @Query("SELECT e.daysToRelationship FROM ExitVelocityEvent e " +
           "WHERE e.relationshipFormed = true AND e.daysToRelationship IS NOT NULL AND e.exitDate BETWEEN :startDate AND :endDate " +
           "ORDER BY e.daysToRelationship")
    List<Integer> getDaysToRelationshipValues(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
