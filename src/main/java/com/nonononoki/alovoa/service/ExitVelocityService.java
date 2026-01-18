package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ExitVelocityEvent;
import com.nonononoki.alovoa.entity.user.ExitVelocityMetrics;
import com.nonononoki.alovoa.repo.ExitVelocityEventRepository;
import com.nonononoki.alovoa.repo.ExitVelocityMetricsRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Exit Velocity Service - Tracks the primary success metric for AURA.
 *
 * Philosophy: The best dating platform is one that users leave because
 * they found meaningful relationships, not one that keeps them engaged forever.
 *
 * Exit Velocity measures:
 * - Time from registration to relationship formation
 * - Positive exit rate (leaving because found someone vs. frustration)
 * - User satisfaction and recommendation rates
 */
@Service
public class ExitVelocityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExitVelocityService.class);

    @Autowired
    private ExitVelocityEventRepository eventRepo;

    @Autowired
    private ExitVelocityMetricsRepository metricsRepo;

    @Autowired
    private UserRepository userRepo;

    /**
     * Record a relationship formation event - the ideal exit!
     */
    @Transactional
    public ExitVelocityEvent recordRelationshipFormed(User user, User partner) {
        ExitVelocityEvent event = new ExitVelocityEvent();
        event.setUser(user);
        event.setPartnerUser(partner);
        event.setEventType(ExitVelocityEvent.ExitEventType.RELATIONSHIP_FORMED);
        event.setRelationshipFormed(true);
        event.setExitDate(LocalDate.now());

        // Calculate days to relationship
        if (user.getDates() != null && user.getDates().getCreationDate() != null) {
            LocalDate creationDate = user.getDates().getCreationDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            event.setDaysToRelationship((int) ChronoUnit.DAYS.between(creationDate, LocalDate.now()));
        }

        if (user.getFirstActiveDate() != null) {
            event.setDaysActiveToRelationship((int) ChronoUnit.DAYS.between(user.getFirstActiveDate(), LocalDate.now()));
        }

        eventRepo.save(event);

        // Update user
        user.setRelationshipFormedDate(LocalDate.now());
        userRepo.save(user);

        LOGGER.info("Recorded relationship formation for user {} with user {} - Exit velocity: {} days",
                user.getId(), partner != null ? partner.getId() : "external", event.getDaysToRelationship());

        return event;
    }

    /**
     * Record exit survey response
     */
    @Transactional
    public void recordExitSurvey(User user, ExitSurveyResponse survey) {
        ExitVelocityEvent event = eventRepo.findByUser(user).orElseGet(() -> {
            ExitVelocityEvent newEvent = new ExitVelocityEvent();
            newEvent.setUser(user);
            newEvent.setExitDate(LocalDate.now());
            return newEvent;
        });

        event.setEventType(survey.exitType);
        event.setExitReason(survey.reason);
        event.setRelationshipFormed(survey.exitType == ExitVelocityEvent.ExitEventType.RELATIONSHIP_FORMED ||
                                    survey.exitType == ExitVelocityEvent.ExitEventType.RELATIONSHIP_EXTERNAL);
        event.setSatisfactionRating(survey.satisfactionRating);
        event.setFeedback(survey.feedback);
        event.setWouldRecommend(survey.wouldRecommend);

        // Calculate days if relationship formed
        if (event.getRelationshipFormed() && user.getDates() != null) {
            LocalDate creationDate = user.getDates().getCreationDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            event.setDaysToRelationship((int) ChronoUnit.DAYS.between(creationDate, LocalDate.now()));
        }

        eventRepo.save(event);

        user.setExitSurveyCompleted(true);
        userRepo.save(user);

        LOGGER.info("Recorded exit survey for user {}: type={}, satisfaction={}, wouldRecommend={}",
                user.getId(), survey.exitType, survey.satisfactionRating, survey.wouldRecommend);
    }

    /**
     * Detect inactive users and record as churned
     */
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    @Transactional
    public void detectInactiveUsers() {
        LOGGER.info("Running inactive user detection...");

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);

        // Find users inactive for 30 days (who haven't already been recorded)
        List<User> inactive30 = userRepo.findInactiveUsersWithoutExitEvent(thirtyDaysAgo);
        for (User user : inactive30) {
            ExitVelocityEvent event = new ExitVelocityEvent();
            event.setUser(user);
            event.setEventType(ExitVelocityEvent.ExitEventType.INACTIVE_30_DAYS);
            event.setRelationshipFormed(false);
            event.setExitDate(LocalDate.now());
            eventRepo.save(event);
        }

        LOGGER.info("Detected {} users inactive for 30+ days", inactive30.size());
    }

    /**
     * Calculate and store daily metrics
     */
    @Scheduled(cron = "0 30 2 * * *") // Run at 2:30 AM daily
    @Transactional
    public void calculateDailyMetrics() {
        LOGGER.info("Calculating daily exit velocity metrics...");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate monthAgo = yesterday.minusDays(30);

        // Check if already calculated
        if (metricsRepo.findByMetricDate(yesterday).isPresent()) {
            LOGGER.info("Metrics for {} already calculated", yesterday);
            return;
        }

        ExitVelocityMetrics metrics = new ExitVelocityMetrics();
        metrics.setMetricDate(yesterday);

        // Count exits
        List<ExitVelocityEvent> yesterdayEvents = eventRepo.findByExitDateBetween(yesterday, yesterday);
        metrics.setTotalExits(yesterdayEvents.size());

        // Count positive exits
        long positiveExits = yesterdayEvents.stream()
                .filter(e -> e.getRelationshipFormed() ||
                            e.getEventType() == ExitVelocityEvent.ExitEventType.RELATIONSHIP_EXTERNAL)
                .count();
        metrics.setPositiveExits((int) positiveExits);

        // Count relationships formed
        long relationshipsFormed = yesterdayEvents.stream()
                .filter(ExitVelocityEvent::getRelationshipFormed)
                .count();
        metrics.setRelationshipsFormed((int) relationshipsFormed);

        // Calculate average days to relationship (from last 30 days for statistical significance)
        metrics.setAvgDaysToRelationship(eventRepo.getAvgDaysToRelationship(monthAgo, yesterday));

        // Calculate median days to relationship
        List<Integer> daysValues = eventRepo.getDaysToRelationshipValues(monthAgo, yesterday);
        if (!daysValues.isEmpty()) {
            int middle = daysValues.size() / 2;
            if (daysValues.size() % 2 == 0) {
                metrics.setMedianDaysToRelationship((daysValues.get(middle - 1) + daysValues.get(middle)) / 2.0);
            } else {
                metrics.setMedianDaysToRelationship((double) daysValues.get(middle));
            }
        }

        // Calculate satisfaction
        metrics.setAvgSatisfaction(eventRepo.getAvgSatisfaction(monthAgo, yesterday));

        // Calculate recommendation rate
        long wouldRecommend = eventRepo.countWouldRecommend(monthAgo, yesterday);
        long withResponse = eventRepo.countWithRecommendationResponse(monthAgo, yesterday);
        if (withResponse > 0) {
            metrics.setRecommendationRate((double) wouldRecommend / withResponse);
        }

        // Count active users (active in last 7 days)
        metrics.setActiveUsers(userRepo.countActiveUsersInPeriod(yesterday.minusDays(7), yesterday));

        // Count new users (registered yesterday)
        metrics.setNewUsers(userRepo.countNewUsersOnDate(yesterday));

        // Count churned users (inactive for 30 days as of yesterday)
        metrics.setChurnedUsers(userRepo.countInactiveUsers(yesterday.minusDays(30)));

        metrics.setCalculatedAt(new Date());
        metricsRepo.save(metrics);

        LOGGER.info("Daily metrics calculated: {} exits, {} positive, {} relationships formed, avg {} days to relationship",
                metrics.getTotalExits(), metrics.getPositiveExits(), metrics.getRelationshipsFormed(),
                metrics.getAvgDaysToRelationship());
    }

    /**
     * Get current exit velocity summary
     */
    public ExitVelocitySummary getSummary() {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        LocalDate monthAgo = today.minusDays(30);

        ExitVelocitySummary summary = new ExitVelocitySummary();

        // Get most recent metrics
        metricsRepo.findTopByOrderByMetricDateDesc().ifPresent(latest -> {
            summary.avgDaysToRelationship = latest.getAvgDaysToRelationship();
            summary.medianDaysToRelationship = latest.getMedianDaysToRelationship();
            summary.positiveExitRate = latest.getPositiveExitRate();
            summary.avgSatisfaction = latest.getAvgSatisfaction();
            summary.recommendationRate = latest.getRecommendationRate();
        });

        // Get period summaries
        summary.relationshipsThisWeek = metricsRepo.getTotalRelationshipsInPeriod(weekAgo, today);
        summary.relationshipsThisMonth = metricsRepo.getTotalRelationshipsInPeriod(monthAgo, today);
        summary.avgPositiveExitRateMonth = metricsRepo.getAvgPositiveExitRateInPeriod(monthAgo, today);

        return summary;
    }

    /**
     * Get metrics history for charting
     */
    public List<ExitVelocityMetrics> getMetricsHistory(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        return metricsRepo.findByMetricDateBetweenOrderByMetricDateDesc(startDate, endDate);
    }

    // DTOs

    public static class ExitSurveyResponse {
        public ExitVelocityEvent.ExitEventType exitType;
        public String reason;
        public Integer satisfactionRating;
        public String feedback;
        public Boolean wouldRecommend;
    }

    public static class ExitVelocitySummary {
        public Double avgDaysToRelationship;
        public Double medianDaysToRelationship;
        public Double positiveExitRate;
        public Double avgSatisfaction;
        public Double recommendationRate;
        public long relationshipsThisWeek;
        public long relationshipsThisMonth;
        public Double avgPositiveExitRateMonth;
    }
}
