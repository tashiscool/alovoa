package com.nonononoki.alovoa.entity.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

/**
 * Daily aggregated exit velocity metrics for the platform.
 * Part of AURA's success tracking system.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_exit_metrics_date", columnList = "metric_date")
})
public class ExitVelocityMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_date", nullable = false, unique = true)
    private LocalDate metricDate;

    @Column(name = "total_exits", nullable = false)
    private Integer totalExits = 0;

    @Column(name = "positive_exits", nullable = false)
    private Integer positiveExits = 0;

    @Column(name = "relationships_formed", nullable = false)
    private Integer relationshipsFormed = 0;

    @Column(name = "avg_days_to_relationship")
    private Double avgDaysToRelationship;

    @Column(name = "median_days_to_relationship")
    private Double medianDaysToRelationship;

    @Column(name = "avg_satisfaction")
    private Double avgSatisfaction;

    @Column(name = "recommendation_rate")
    private Double recommendationRate;

    @Column(name = "active_users", nullable = false)
    private Integer activeUsers = 0;

    @Column(name = "new_users", nullable = false)
    private Integer newUsers = 0;

    @Column(name = "churned_users", nullable = false)
    private Integer churnedUsers = 0;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "calculated_at", nullable = false)
    private Date calculatedAt = new Date();

    /**
     * Calculate the positive exit rate
     */
    public Double getPositiveExitRate() {
        if (totalExits == 0) return 0.0;
        return (double) positiveExits / totalExits;
    }

    /**
     * Calculate the relationship formation rate
     */
    public Double getRelationshipFormationRate() {
        if (totalExits == 0) return 0.0;
        return (double) relationshipsFormed / totalExits;
    }

    /**
     * Calculate churn rate
     */
    public Double getChurnRate() {
        if (activeUsers == 0) return 0.0;
        return (double) churnedUsers / activeUsers;
    }

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = new Date();
        }
        if (metricDate == null) {
            metricDate = LocalDate.now();
        }
    }
}
