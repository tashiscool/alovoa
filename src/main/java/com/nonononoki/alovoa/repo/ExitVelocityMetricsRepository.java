package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.user.ExitVelocityMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExitVelocityMetricsRepository extends JpaRepository<ExitVelocityMetrics, Long> {

    Optional<ExitVelocityMetrics> findByMetricDate(LocalDate metricDate);

    List<ExitVelocityMetrics> findByMetricDateBetweenOrderByMetricDateDesc(LocalDate startDate, LocalDate endDate);

    /**
     * Get the most recent metrics
     */
    Optional<ExitVelocityMetrics> findTopByOrderByMetricDateDesc();

    /**
     * Get average exit velocity over a period
     */
    @Query("SELECT AVG(m.avgDaysToRelationship) FROM ExitVelocityMetrics m " +
           "WHERE m.metricDate BETWEEN :startDate AND :endDate")
    Double getAvgExitVelocityInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get total relationships formed over a period
     */
    @Query("SELECT COALESCE(SUM(m.relationshipsFormed), 0) FROM ExitVelocityMetrics m " +
           "WHERE m.metricDate BETWEEN :startDate AND :endDate")
    long getTotalRelationshipsInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get average positive exit rate over a period
     */
    @Query("SELECT CASE WHEN SUM(m.totalExits) > 0 THEN CAST(SUM(m.positiveExits) AS double) / SUM(m.totalExits) ELSE 0.0 END " +
           "FROM ExitVelocityMetrics m WHERE m.metricDate BETWEEN :startDate AND :endDate")
    Double getAvgPositiveExitRateInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
